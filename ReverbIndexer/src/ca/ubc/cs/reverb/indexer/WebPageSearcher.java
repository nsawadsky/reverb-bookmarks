package ca.ubc.cs.reverb.indexer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
    private final static int MAX_RESULTS = 10;
    
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
    
    public BatchQueryResult performSearch(List<String> queryStrings) throws IndexerException {
        IndexSearcher indexSearcher = null;
        
        try {
            // Must create new IndexSearcher if you are creating a new IndexReader 
            // (through reopen).
            indexSearcher = new IndexSearcher(reader.reopen());
        } catch (IOException e) {
            throw new IndexerException("Error reopening IndexReader: " + e, e);
        }

        BatchQueryResult result = new BatchQueryResult();
        for (String queryString: queryStrings) {
            result.queryResults.add(new QueryResult(queryString, performSearch(indexSearcher, queryString)));
        }
        return result;
    }
    
    private List<Location> performSearch(IndexSearcher searcher, String queryString) throws IndexerException {
        try {
            Query query = parser.parse(queryString);
            
            try {
                List<Location> resultList = new ArrayList<Location>();
                TopDocs topDocs = searcher.search(query, 50);
                
                List<String> urls = new ArrayList<String>();
                for (ScoreDoc scoreDoc: topDocs.scoreDocs) {
                    Document doc = searcher.doc(scoreDoc.doc);
                    String url = doc.get(WebPageIndexer.URL_FIELD_NAME);
                    String title = doc.get(WebPageIndexer.TITLE_FIELD_NAME);
                    resultList.add(new Location(url, title, scoreDoc.score));
                    urls.add(url);
                }
                Map<String, Float> frecencyBoosts = locationsDatabase.getFrecencyBoosts(urls);
                for (Location location: resultList) {
                    Float frecencyBoost = frecencyBoosts.get(location.url);
                    if (frecencyBoost != null) {
                        location.frecencyBoost = frecencyBoost;
                        location.overallScore = frecencyBoost * location.luceneScore;
                    }
                }
                Collections.sort(resultList, new Comparator<Location>() {

                    @Override
                    public int compare(Location o1, Location o2) {
                        if (o1.overallScore > o2.overallScore) {
                            return -1;
                        }
                        if (o1.overallScore < o2.overallScore) {
                            return 1;
                        }
                        return 0;
                    }
                    
                });
                if (resultList.size() > MAX_RESULTS) {
                    resultList = resultList.subList(0, MAX_RESULTS-1);
                }
                return resultList;
            } finally {
                searcher.close();
            }
        } catch (Exception e) {
            throw new IndexerException("Error running query '" + queryString + "': " + e, e);
        }
    }
        
}
