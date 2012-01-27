package ca.ubc.cs.reverb.indexer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.document.Document;
import org.apache.lucene.queryParser.MultiFieldQueryParser;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.Version;

import ca.ubc.cs.reverb.indexer.messages.BatchQueryReply;
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
    private StudyDataCollector collector;

    private final static String VERSION_FIELD_SEP = "[\\_\\-\\.]";
    private final static String VERSION_NUMBER = "[0-9]+(?:" + VERSION_FIELD_SEP + "[0-9]+)*";
    private final static Pattern VERSION_NUMBER_PATTERN = Pattern.compile(VERSION_NUMBER); 
    private final static Pattern TEXT_PLUS_VERSION_NUMBER_PATTERN = Pattern.compile("(.*?)(" + VERSION_NUMBER + ")");
    
    // For testing
    WebPageSearcher() { }
    
    public WebPageSearcher(IndexerConfig config, SharedIndexReader reader, 
            LocationsDatabase locationsDatabase, StudyDataCollector collector) {
        this.config = config;
        this.locationsDatabase = locationsDatabase;
        this.collector = collector;
        
        parser = new MultiFieldQueryParser(Version.LUCENE_33, 
                new String[] {WebPageIndexer.TITLE_FIELD_NAME, WebPageIndexer.CONTENT_FIELD_NAME}, 
                new WebPageAnalyzer());

        this.reader = reader;
    }
    
    public BatchQueryReply performSearch(List<IndexerQuery> inputQueries) throws IndexerException {
        long now = new Date().getTime();
        
        IndexSearcher indexSearcher = getNewIndexSearcher();
        
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
            List<Hit> hits = performSearch(indexSearcher, query.queryString, MAX_RESULTS_PER_QUERY, now);
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

        // Compact hit infos whose URL's are identical except for a version number.
        List<HitInfo> hitInfos = compactHitInfos(new ArrayList<HitInfo>(infosByUrl.values()));
        
        // Sort results by overall score.
        sortHitInfoList(hitInfos);
        
        // Truncate the list based on the max results to be returned.
        if (hitInfos.size() > MAX_RESULTS) {
            hitInfos = hitInfos.subList(0, MAX_RESULTS);
        }

        if (collector != null) {
            // Log the recommendations about to be sent.
            for (HitInfo info: hitInfos) {
                collector.logEvent(new RecommendationEvent(
                        now, info.hit.locationInfo, info.frecencyBoost, info.combinedScore,
                        info.getOverallScore()));
            }
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
        List<MergedQueryResult> nextMergedResults = new ArrayList<MergedQueryResult>();
        for (MergedQueryResult currResult: mergedResults) {
            boolean foundMatch = false;
            for (MergedQueryResult nextResult: nextMergedResults) {
                if (currResult.allHitsMatch(nextResult.queries.get(0))) {
                    nextResult.queries.addAll(currResult.queries);
                    nextResult.hits.addAll(currResult.hits);
                    foundMatch = true;
                    break;
                }
            }
            if (!foundMatch) {
                nextMergedResults.add(currResult);
            }
        }
        
        // Sort hit info lists for merged results.
        for (MergedQueryResult mergedResult: nextMergedResults) {
            sortHitInfoList(mergedResult.hits);
        }
        
        // Create the result structure to be sent to the client.
        BatchQueryReply result = new BatchQueryReply();
        for (MergedQueryResult mergedResult: nextMergedResults) {
            result.queryResults.add(new QueryResult(
                    mergedResult.queries, getLocationList(mergedResult.hits)));
        }
        return result;
    }
    
    /**
     * Combines hit infos whose URL's differ only in a version number.
     * 
     * @param hitInfos The list of hit infos to be compacted.
     * @return The input list, with entries whose URL's differ only in a (single) version number replaced by 
     *         a single entry.
     */
    protected List<HitInfo> compactHitInfos(List<HitInfo> hitInfos) {
        // Collapse hits whose URL's are identical except for a version number. 
        List<LatestVersionHitInfo> latestVersionHitInfos = new ArrayList<LatestVersionHitInfo>();
        
        for (HitInfo hitInfo: hitInfos) {
            boolean found = false;
            for (LatestVersionHitInfo topInfo: latestVersionHitInfos) {
                if (topInfo.tryCombine(hitInfo)) {
                    found = true; 
                    break;
                }
            }
            if (!found) {
                latestVersionHitInfos.add(new LatestVersionHitInfo(hitInfo));
            }
        }
        
        List<HitInfo> result = new ArrayList<HitInfo>();
        for (LatestVersionHitInfo topInfo: latestVersionHitInfos) {
            result.add(topInfo.hitInfo);
        }
        return result;
    }

    protected IndexSearcher getNewIndexSearcher() throws IndexerException {
        try {
            // Must create new IndexSearcher if you are creating a new IndexReader 
            // (through reopen).
            return new IndexSearcher(reader.reopen());
        } catch (IOException e) {
            throw new IndexerException("Error reopening IndexReader: " + e, e);
        }
    }
    
    protected List<Hit> performSearch(IndexSearcher searcher, String queryString, int maxResults, long now) throws IndexerException {
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
                Map<String, LocationInfo> locationInfos = locationsDatabase.getLocationInfos(urls);
                List<Hit> hitsToRemove = new ArrayList<Hit>();
                for (Hit hit: resultList) {
                    LocationInfo locationInfo = locationInfos.get(hit.url);
                    if (locationInfo == null) {
                        hitsToRemove.add(hit); 
                    } else {
                        hit.locationInfo = locationInfo;
                        hit.frecencyBoost = locationInfo.getFrecencyBoost(now);
                    }
                }
                resultList.removeAll(hitsToRemove);
                return resultList;
            } finally {
                searcher.close();
            }
        } catch (Exception e) {
            throw new IndexerException("Error running query '" + queryString + "': " + e, e);
        }
    }
    
    private List<Location> getLocationList(List<HitInfo> hits) {
        List<Location> result = new ArrayList<Location>();
        for (HitInfo hitInfo: hits) {
            result.add(new Location(hitInfo.hit.url, hitInfo.hit.title, hitInfo.combinedScore, 
                    hitInfo.frecencyBoost, hitInfo.getOverallScore()));
        }
        return result;
    }
    
    private void sortHitInfoList(List<HitInfo> input) {
        Collections.sort(input, new Comparator<HitInfo>() {

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
    }
    
    protected class MergedQueryResult {
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
    
    protected class Hit {
        public Hit(String url, String title, float luceneScore) {
            this.url = url;
            this.title = title;
            this.luceneScore = luceneScore;
        }
        
        public Hit(String url, String title, float luceneScore, float frecencyBoost) {
            this.url = url;
            this.title = title;
            this.luceneScore = luceneScore;
            this.frecencyBoost = frecencyBoost;
        }
        
        public String url;
        public String title;
        public float luceneScore;
        public float frecencyBoost;
        
        public LocationInfo locationInfo;
    }
        
    protected class HitInfo {
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
            return frecencyBoost * combinedScore;
        }
    }

    // TODO: Consider unescaping URL's before checking for version numbers.
    protected class LatestVersionHitInfo {
        public String[] splitUrl;
        public HitInfo hitInfo;
        
        public LatestVersionHitInfo(HitInfo info) {
            hitInfo = info;
            splitUrl = splitUrlWithVersionNumber(info.hit.url);
        }
        
        public boolean tryCombine(HitInfo testInfo) {
            if (splitUrl != null) {
                String[] testSplitUrl = splitUrlWithVersionNumber(testInfo.hit.url);
                if (testSplitUrl != null && testSplitUrl.length == splitUrl.length) {
                    int misses = 0;
                    int missIndex = 0;
                    for (int i = 0; i < splitUrl.length; i++) {
                        if (!splitUrl[i].equals(testSplitUrl[i])) {
                            misses++;
                            if (misses > 1) {
                                break;
                            }
                            missIndex = i;
                        }
                    }
                    if (misses == 0) {
                        // URL's are identical -- should never happen.
                        hitInfo.frecencyBoost = Math.min(
                                hitInfo.frecencyBoost + testInfo.frecencyBoost, LocationInfo.MAX_FRECENCY_BOOST);
                        return true;
                    }
                    if (misses == 1) {
                        Matcher m1 = VERSION_NUMBER_PATTERN.matcher(splitUrl[missIndex]);
                        Matcher m2 = VERSION_NUMBER_PATTERN.matcher(testSplitUrl[missIndex]);
                        if (m1.matches() && m2.matches()) {
                            try {
                                if (compareVersionNumbers(splitUrl[missIndex], testSplitUrl[missIndex]) < 0) {
                                    testInfo.frecencyBoost = Math.min(
                                            hitInfo.frecencyBoost + testInfo.frecencyBoost, LocationInfo.MAX_FRECENCY_BOOST);
                                    hitInfo = testInfo;
                                    splitUrl = testSplitUrl;
                                } else {
                                    hitInfo.frecencyBoost = Math.min(
                                            hitInfo.frecencyBoost + testInfo.frecencyBoost, LocationInfo.MAX_FRECENCY_BOOST);
                                }
                                return true;
                            } catch (NumberFormatException e) {
                                // Should never happen, since we already matched VERSION_NUMBER_PATTERN.
                                return false;
                            }
                        }
                    }
                }
            }
            return false;
        }
        
        private int compareVersionNumbers(String a, String b) throws NumberFormatException {
            String[] aSplit = a.split(VERSION_FIELD_SEP);
            String[] bSplit = b.split(VERSION_FIELD_SEP);
            int minLength = Math.min(aSplit.length, bSplit.length);
            for (int i = 0; i < minLength; i++) {
                int aVal = Integer.parseInt(aSplit[i]);
                int bVal = Integer.parseInt(bSplit[i]);
                if (aVal > bVal) {
                    return 1;
                }
                if (bVal > aVal) {
                    return -1;
                }
            }
            if (aSplit.length > bSplit.length) {
                return 1;
            }
            if (bSplit.length > aSplit.length) {
                return -1;
            }
            return 0;
        }
        
        private String[] splitUrlWithVersionNumber(String url) {
            List<String> result = new ArrayList<String>();
            Matcher matcher = TEXT_PLUS_VERSION_NUMBER_PATTERN.matcher(url);
            boolean foundMatch = false;
            int matchEnd = 0;
            while (matcher.find()) {
                foundMatch = true;
                matchEnd = matcher.end();
                for (int i = 1; i <= matcher.groupCount(); i++) {
                    if (matcher.group(i) != null) {
                        result.add(matcher.group(i));
                    }
                }
            }
            if (foundMatch) {
                if (matchEnd < url.length() - 1) {
                    result.add(url.substring(matchEnd));
                }
                return result.toArray(new String[] {});
            }
            return null;
        }

    }
    
}
