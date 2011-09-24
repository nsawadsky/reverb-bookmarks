package ca.ubc.cs.periscope.indexer;

import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryParser.MultiFieldQueryParser;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.Version;

import ca.ubc.cs.periscope.indexer.messages.Location;

public class WebPageSearcher {
    private final static int MAX_RESULTS = 5;
    
    private IndexerConfig config;
    private IndexReader reader;
    private QueryParser parser;
    
    public WebPageSearcher(IndexerConfig config, IndexReader reader) {
        this.config = config;
        
        parser = new MultiFieldQueryParser(Version.LUCENE_33, 
                new String[] {WebPageIndexer.TITLE_FIELD_NAME, WebPageIndexer.CONTENT_FIELD_NAME}, 
                new WebPageAnalyzer());

        this.reader = reader;
    }
    
    public List<Location> performSearch(String queryString) throws IndexerException {
        try {
            Query query = parser.parse(queryString);
            
            reader.reopen();
            IndexSearcher searcher = new IndexSearcher(reader);
            
            try {
                List<Location> resultList = new ArrayList<Location>();
                TopDocs topDocs = searcher.search(query, MAX_RESULTS);
                
                for (ScoreDoc scoreDoc: topDocs.scoreDocs) {
                    Document doc = searcher.doc(scoreDoc.doc);
                    String url = doc.get(WebPageIndexer.URL_FIELD_NAME);
                    String title = doc.get(WebPageIndexer.TITLE_FIELD_NAME);
                    resultList.add(new Location(url, title));
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
