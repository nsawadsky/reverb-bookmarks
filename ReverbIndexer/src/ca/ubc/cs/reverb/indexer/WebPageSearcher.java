package ca.ubc.cs.reverb.indexer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.queryParser.MultiFieldQueryParser;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.Version;

import ca.ubc.cs.reverb.indexer.messages.BatchQueryResult;
import ca.ubc.cs.reverb.indexer.messages.IndexerQuery;
import ca.ubc.cs.reverb.indexer.messages.Location;
import ca.ubc.cs.reverb.indexer.messages.QueryResult;

/**
 * WebPageSearcher instances are not shared across threads, since my understanding from the docs is that
 * MultiFieldQueryParser is not thread-safe.
 */
public class WebPageSearcher {
    private final static int MAX_RESULTS = 10;
    private final static int MAX_RESULTS_PER_QUERY = 20;
    
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
    
    public BatchQueryResult performSearch(List<IndexerQuery> inputQueries) throws IndexerException {
        IndexSearcher indexSearcher = null;
        
        try {
            // Must create new IndexSearcher if you are creating a new IndexReader 
            // (through reopen).
            indexSearcher = new IndexSearcher(reader.reopen());
        } catch (IOException e) {
            throw new IndexerException("Error reopening IndexReader: " + e, e);
        }
        
        // First, ensure query strings are unique.
        List<IndexerQuery> queries = new ArrayList<IndexerQuery>();
        Set<String> queryStrings = new HashSet<String>();
        for (IndexerQuery query: inputQueries) {
            if (! queryStrings.contains(query.queryString)) {
                queryStrings.add(query.queryString);
                queries.add(query);
            }
        }
        
        // Gather hits, store by URL.
        Map<String, HitInfo> infosByUrl = new HashMap<String, HitInfo>();
        for (IndexerQuery query: queries) {
            List<Hit> hits = performSearch(indexSearcher, query.queryString, MAX_RESULTS_PER_QUERY);
            for (Hit hit: hits) {
                HitInfo info = infosByUrl.get(hit.url);
                if (info == null) {
                    infosByUrl.put(hit.url, new HitInfo(hit, query, hit.luceneScore));
                } else {
                    if (hit.luceneScore > info.bestIndividualScore) {
                        // Keep track of which query gave the highest score for a given URL.
                        info.bestIndividualScore = hit.luceneScore;
                        info.bestQuery = query;
                    } 
                    // If two different queries return the same URL, add the scores together.
                    info.combinedScore += hit.luceneScore;
                    
                    // Keep track of all queries which matched a given URL.
                    info.queries.add(query);
                }
            }
        }
        
        // Sort results by overall score.
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
        
        // Truncate the list based on the max results to be returned.
        if (hitInfos.size() > MAX_RESULTS) {
            hitInfos = hitInfos.subList(0, MAX_RESULTS);
        }

        // Group results with the query that gave them the highest score.  Ensure that the resulting
        // list of MergedQueryResults is sorted according to the highest-scoring contained result.
        List<MergedQueryResult> mergedResults = new ArrayList<MergedQueryResult>();
        for (HitInfo info: hitInfos) {
            MergedQueryResult mergedResult = null;
            for (MergedQueryResult merged: mergedResults) {
                if (merged.queries.get(0).queryString.equals(info.bestQuery.queryString)) {
                    mergedResult = merged;
                    break;
                }
            }
            if (mergedResult == null) {
                mergedResult = new MergedQueryResult(info.bestQuery, info);
                mergedResults.add(mergedResult);
            } else {
                mergedResult.hits.add(info);
            }
        }
        
        // Merge later groups of results in to earlier groups if all results in the later group match  
        // the first query for the earlier group.
        for (int i = 0; i < mergedResults.size()-1; i++) {
            for (int j = i+1; j < mergedResults.size(); j++) {
                MergedQueryResult result1 = mergedResults.get(i);
                if (!result1.hits.isEmpty()) {
                    MergedQueryResult result2 = mergedResults.get(j);
                    if (! result2.hits.isEmpty()) {
                        if (result2.allHitsMatch(result1.queries.get(0))) {
                            result1.queries.addAll(result2.queries);
                            result1.hits.addAll(result2.hits);
                            result2.hits.clear();
                        }
                    }
                }
            }
        }
        
        // Create the result structure to be sent to the client.
        BatchQueryResult result = new BatchQueryResult();
        for (MergedQueryResult mergedResult: mergedResults) {
            // Prune entries which were merged with earlier entries.
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
    
    private List<Hit> performSearch(IndexSearcher searcher, String queryString, int maxResults) throws IndexerException {
        try {
            Query query = parser.parse(queryString);
            
            try {
                List<Hit> resultList = new ArrayList<Hit>();
                TopDocs topDocs = searcher.search(query, maxResults);
                
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
        public MergedQueryResult(IndexerQuery query, HitInfo info) {
            queries.add(query);
            hits.add(info);
        }
        
        public boolean allHitsMatch(IndexerQuery query) {
            for (HitInfo info: hits) {
                boolean matched = false;
                for (IndexerQuery hitQuery: info.queries) {
                    if (hitQuery.queryString.equals(query.queryString)) {
                        matched = true; 
                        break;
                    }
                }
                if (!matched) {
                    return false;
                }
            }
            return true;
        }
        
        public List<IndexerQuery> queries = new ArrayList<IndexerQuery>();
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
        public HitInfo(Hit hit, IndexerQuery query, float score) {
            this.hit = hit;
            this.frecencyBoost = hit.frecencyBoost;
            this.bestQuery = query;
            this.bestIndividualScore = score;
            this.combinedScore = score;
            queries.add(query);
        }
        
        public Hit hit;
        public List<IndexerQuery> queries = new ArrayList<IndexerQuery>();
        public float bestIndividualScore;
        public IndexerQuery bestQuery;
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
