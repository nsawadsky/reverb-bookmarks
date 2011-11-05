package ca.ubc.cs.reverb.indexer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.document.Document;
import org.apache.lucene.queryParser.MultiFieldQueryParser;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.Version;

import ca.ubc.cs.reverb.indexer.messages.BatchQueryResult;
import ca.ubc.cs.reverb.indexer.messages.Location;
import ca.ubc.cs.reverb.indexer.messages.QueryResult;

/**
 * WebPageSearcher instances are not shared across threads, since my understanding from the docs is that
 * MultiFieldQueryParser is not thread-safe.
 */
public class WebPageSearcher {
    private final static int MAX_RESULTS = 30;
    
    private IndexerConfig config;
    private SharedIndexReader reader;
    private QueryParser parser;
    private LocationsDatabase locationsDatabase;
    
    public WebPageSearcher(IndexerConfig config, SharedIndexReader reader, LocationsDatabase locationsDatabase) {
        this.config = config;
        this.locationsDatabase = locationsDatabase;
        
        parser = new MultiFieldQueryParser(Version.LUCENE_33, 
                new String[] {WebPageIndexer.TITLE_FIELD_NAME, WebPageIndexer.CONTENT_FIELD_NAME}, 
                new WebPageAnalyzer());

        this.reader = reader;
    }
    
    public BatchQueryResult performSearch(List<String> inputQueries) throws IndexerException {
        IndexSearcher indexSearcher = null;
        
        try {
            // Must create new IndexSearcher if you are creating a new IndexReader 
            // (through reopen).
            indexSearcher = new IndexSearcher(reader.reopen());
        } catch (IOException e) {
            throw new IndexerException("Error reopening IndexReader: " + e, e);
        }

        List<String> queryStrings = new ArrayList<String>();
        for (String input: inputQueries) {
            if (! queryStrings.contains(input)) {
                queryStrings.add(input);
            }
        }
        
        Map<String, HitInfo> infosByUrl = new HashMap<String, HitInfo>();
        
        for (String queryString: queryStrings) {
            List<Hit> hits = performSearch(indexSearcher, queryString);
            for (Hit hit: hits) {
                HitInfo info = infosByUrl.get(hit.url);
                if (info == null) {
                    infosByUrl.put(hit.url, new HitInfo(hit, queryString, hit.luceneScore));
                } else {
                    if (hit.luceneScore > info.bestIndividualScore) {
                        info.bestIndividualScore = hit.luceneScore;
                        info.bestQuery = queryString;
                    } 
                    info.combinedScore += hit.luceneScore;
                    info.queries.add(queryString);
                }
            }
        }
        
        List<HitInfo> hitInfos = new ArrayList<HitInfo>(infosByUrl.values());
        Collections.sort(hitInfos, new Comparator<HitInfo>() {

            @Override
            public int compare(HitInfo o1, HitInfo o2) {
                if (o1.getOverallScore() > o2.getOverallScore()) {
                    return -1;
                }
                if (o1.getOverallScore() < o2.getOverallScore()) {
                    return 1;
                }
                return 0;
            }
            
        });
        if (hitInfos.size() > MAX_RESULTS) {
            hitInfos = hitInfos.subList(0, MAX_RESULTS);
        }

        Map<String, MergedQueryResult> hitInfosByBestQuery = new HashMap<String, MergedQueryResult>();
        for (HitInfo info: hitInfos) {
            MergedQueryResult merged = hitInfosByBestQuery.get(info.bestQuery);
            if (merged == null) {
                merged = new MergedQueryResult(info.bestQuery, info);
                hitInfosByBestQuery.put(info.bestQuery, merged);
            } else {
                merged.hits.add(info);
            }
        }
        
        List<MergedQueryResult> mergedResults = new ArrayList<MergedQueryResult>();
        for (String queryString: queryStrings) {
            MergedQueryResult mergedResult = hitInfosByBestQuery.get(queryString);
            if (mergedResult != null) {
                mergedResults.add(mergedResult);
            }
        }
        
        for (int i = 0; i < mergedResults.size()-1; i++) {
            for (int j = i+1; j < mergedResults.size(); j++) {
                MergedQueryResult result1 = mergedResults.get(i);
                if (!result1.hits.isEmpty()) {
                    MergedQueryResult result2 = mergedResults.get(j);
                    if (! result2.hits.isEmpty()) {
                        if (result2.allHitsMatch(result1.queries) && result1.allHitsMatch(result2.queries)) {
                            result1.queries.addAll(result2.queries);
                            result1.hits.addAll(result2.hits);
                            result2.hits.clear();
                        }
                    }
                }
            }
        }
        
        BatchQueryResult result = new BatchQueryResult();
        for (MergedQueryResult mergedResult: mergedResults) {
            if (! mergedResult.hits.isEmpty()) {
                result.queryResults.add(new QueryResult(
                        mergedResult.queries, getLocationList(mergedResult.hits)));
            }
        }
        return result;
    }
    
    private List<Location> getLocationList(List<HitInfo> hits) {
        List<Location> result = new ArrayList<Location>();
        for (HitInfo hitInfo: hits) {
            result.add(new Location(hitInfo.hit.url, hitInfo.hit.title, hitInfo.combinedScore, 
                    hitInfo.frecencyBoost, hitInfo.getOverallScore()));
        }
        return result;
    }
    
    private List<Hit> performSearch(IndexSearcher searcher, String queryString) throws IndexerException {
        try {
            Query query = parser.parse(queryString);
            
            try {
                List<Hit> resultList = new ArrayList<Hit>();
                TopDocs topDocs = searcher.search(query, 20);
                
                List<String> urls = new ArrayList<String>();
                for (ScoreDoc scoreDoc: topDocs.scoreDocs) {
                    Document doc = searcher.doc(scoreDoc.doc);
                    String url = doc.get(WebPageIndexer.URL_FIELD_NAME);
                    String title = doc.get(WebPageIndexer.TITLE_FIELD_NAME);
                    resultList.add(new Hit(url, title, scoreDoc.score));
                    urls.add(url);
                }
                Map<String, Float> frecencyBoosts = locationsDatabase.getFrecencyBoosts(urls);
                for (Hit location: resultList) {
                    Float frecencyBoost = frecencyBoosts.get(location.url);
                    if (frecencyBoost != null) {
                        location.frecencyBoost = frecencyBoost;
                    }
                }
                return resultList;
            } finally {
                searcher.close();
            }
        } catch (Exception e) {
            throw new IndexerException("Error running query '" + queryString + "': " + e, e);
        }
    }
    
    private class MergedQueryResult {
        public MergedQueryResult(String query, HitInfo info) {
            queries.add(query);
            hits.add(info);
        }
        
        public boolean allHitsMatch(List<String> testQueries) {
            for (HitInfo info: hits) {
                if (!info.queries.containsAll(testQueries)) {
                    return false;
                }
            }
            return true;
        }
        
        public List<String> queries = new ArrayList<String>();
        public List<HitInfo> hits = new ArrayList<HitInfo>();
    }
    
    private class Hit {
        public Hit(String url, String title, float luceneScore) {
            this.url = url;
            this.title = title;
            this.luceneScore = luceneScore;
        }
        
        public String url;
        public String title;
        public float luceneScore;
        public float frecencyBoost;
    }
        
    private class HitInfo {
        public HitInfo(Hit hit, String queryString, float score) {
            this.hit = hit;
            this.frecencyBoost = hit.frecencyBoost;
            this.bestQuery = queryString;
            this.bestIndividualScore = score;
            this.combinedScore = score;
            queries.add(queryString);
        }
        
        public Hit hit;
        public List<String> queries = new ArrayList<String>();
        public float bestIndividualScore;
        public String bestQuery;
        public float frecencyBoost;
        public float combinedScore;
        
        public float getOverallScore() {
            if (frecencyBoost > 0.0) {
                return frecencyBoost * combinedScore;
            }
            return combinedScore;
        }
    }
}
