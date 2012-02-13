package ca.ubc.cs.reverb.indexer;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;

import org.apache.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import ca.ubc.cs.reverb.indexer.LocationsDatabase.UpdateLocationResult;
import ca.ubc.cs.reverb.indexer.messages.DeleteLocationRequest;
import ca.ubc.cs.reverb.indexer.messages.UpdatePageInfoRequest;
import ca.ubc.cs.reverb.indexer.study.BrowserVisitEvent;
import ca.ubc.cs.reverb.indexer.study.DeleteLocationEvent;
import ca.ubc.cs.reverb.indexer.study.LocationsIndexedMilestoneEvent;
import ca.ubc.cs.reverb.indexer.study.StudyDataCollector;

/**
 * This class is thread-safe, because of the thread-safety of IndexerConfig, LocationsDatabase, and IndexWriter.
 */
public class WebPageIndexer {
    private static Logger log = Logger.getLogger(WebPageIndexer.class);
    
    public final static String URL_FIELD_NAME = "url";
    public final static String TITLE_FIELD_NAME = "title";
    public final static String CONTENT_FIELD_NAME = "content";
    
    private IndexerConfig config;
    private IndexWriter indexWriter;
    private LocationsDatabase locationsDatabase;
    /**
     * May be null.
     */
    private StudyDataCollector collector;
    
    /**
     * You can pass null for the collector parameter (e.g. for unit testing).
     */
    public WebPageIndexer(IndexerConfig config, LocationsDatabase locationsDatabase, StudyDataCollector collector) throws IndexerException {
        this.config = config;
        this.locationsDatabase = locationsDatabase;
        this.collector = collector;

        try {
            Directory index = FSDirectory.open(new File(config.getIndexPath()));
            IndexWriterConfig indexWriterConfig = new IndexWriterConfig(Version.LUCENE_33, new WebPageAnalyzer());
            
            // Add new documents to the existing index:
            indexWriterConfig.setOpenMode(OpenMode.CREATE_OR_APPEND);
            
            indexWriter = new IndexWriter(index, indexWriterConfig);
        } catch (Exception e) {
            throw new IndexerException("Exception initializing web page indexer: " + e, e);
        }
    }
    
    public void close() throws IndexerException {
        try {
            indexWriter.close(true);
        } catch (Exception e) {
            throw new IndexerException("Error closing indexWriter: " + e, e);
        }
    }
    
    public SharedIndexReader getNewIndexReader() throws IndexerException {
        try {
            // IndexReader is thread-safe, share it for efficiency.
            return new SharedIndexReader(IndexReader.open(indexWriter, true));
        } catch (IOException e) {
            throw new IndexerException("Error opening index reader: " + e, e);
        }
    }
    
    public void commitChanges() throws IndexerException {
        try {
            indexWriter.commit();
            
            // Ensure index changes are committed first, since a row in the database can prevent indexing
            // for up to a day.  
            // Rare interleavings of commitChanges, deleteLocation, and indexPage
            // could still result in a page being absent from the index, but not indexable for up to a day.
            // We accept this risk to avoid the performance hit of synchronizing these three
            // methods (especially commitChanges).
            locationsDatabase.commitChanges();
        } catch (IndexerException e) {
            throw e;
        } catch (Exception e) {
            throw new IndexerException("Error committing changes: " + e, e);
        }
    }

    /**
     * The delete is committed immediately (along with any pending updates).
     */
    public void deleteLocation(DeleteLocationRequest request) throws IndexerException {
        try {
            // First delete from locations database, since a row in the database can prevent indexing
            // for up to a day.
            // Rare interleavings of commitChanges, deleteLocation, and indexPage
            // could still result in a page being absent from the index, but not indexable for up to a day.
            // We accept this risk to avoid the performance hit of synchronizing these three
            // methods (especially commitChanges).
            LocationInfo deletedInfo = locationsDatabase.deleteLocationInfo(request.url);
            
            indexWriter.deleteDocuments(new Term(URL_FIELD_NAME, request.url));
            indexWriter.commit();
            
            if (collector != null) {
                long now = System.currentTimeMillis();
                collector.logEvent(new DeleteLocationEvent(now, deletedInfo, deletedInfo.getFrecencyBoost(now)));
            }
        } catch (Exception e) {
            throw new IndexerException("Exception deleting document: " + e, e);
        }
    }
    
    /**
     * The update is not committed immediately (a separate call to commitChanges is
     * necessary).  The page will not be indexed if it matches certain filtering criteria.
     * It will also not be indexed (but its entry in the locations database, if it exists, will still be updated)
     * if the info.html field is null or empty.  This occurs if the browser plugin's cache indicates that the 
     * page has already been indexed within the last 24 hours.
     * 
     * @return true if the page was indexed, false otherwise.
     */
    public boolean indexPage(UpdatePageInfoRequest info) throws IndexerException {
        try {
            String normalizedUrl = normalizeUrl(info.url);

            boolean isJavadoc = false;
            boolean isCodeRelated = false;
            
            boolean htmlProvided = info.html != null && !info.html.isEmpty();
            
            if (htmlProvided) {
                // make a new, empty document
                Document doc = new Document();
        
                org.jsoup.nodes.Document jsoupDoc = Jsoup.parse(info.html);
    
                Elements framesets = jsoupDoc.select("frameset");
                if (framesets.size() > 0) {
                    // Filter out parent frameset pages.  We may still want the child frames, but the frameset parent
                    // does not usually have useful content, and may add redundant results to the index 
                    // (e.g. if title of parent page matches title of a frame).
                    return false;
                }
                
                // Add the URL of the page as a field named "url".  Use a
                // field that is indexed (i.e. searchable), but don't tokenize 
                // the field into separate words and don't index term frequency
                // or positional information:
                Field urlField = new Field(URL_FIELD_NAME, normalizedUrl, Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS);
                urlField.setOmitTermFreqAndPositions(true);
                doc.add(urlField);
                
                Field titleField = new Field(TITLE_FIELD_NAME, jsoupDoc.title(), Field.Store.YES, Field.Index.ANALYZED);
                titleField.setBoost(3.0F);
                doc.add(titleField);
                
                Elements scripts = jsoupDoc.select("script");
                for (Element script: scripts) {
                    script.remove();
                }
                Elements plaintext = jsoupDoc.select("plaintext");
                for (Element element: plaintext) {
                    element.remove();
                }
                
                isJavadoc = WebPageClassifier.checkIsJavadoc(jsoupDoc);
                
                Elements allElements = jsoupDoc.getAllElements();
                for (Element element: allElements) {
                    if (element.hasText()) {
                        element.appendText(" ");
                    }
                }
                
                String text = jsoupDoc.text();
                
                isCodeRelated = WebPageClassifier.checkIsCodeRelated(text);
                
                // Add the content of the page to a field named "content".  Specify a Reader,
                // so that the text of the file is tokenized and indexed, but not stored.
                Field contentField = new Field(CONTENT_FIELD_NAME, new StringReader(text));
                doc.add(contentField);
        
                // An old copy of this page may have been indexed so 
                // we use updateDocument instead to replace the old one matching the exact 
                // URL, if present:
                indexWriter.updateDocument(new Term(URL_FIELD_NAME, normalizedUrl), doc);
            }
            
            long now = System.currentTimeMillis();
            
            // Rare interleavings of commitChanges, deleteLocation, and indexPage
            // could still result in a page being absent from the index, but not indexable for up to a day.
            // We accept this risk to avoid the performance hit of synchronizing these three
            // methods (especially commitChanges).
            UpdateLocationResult updated = locationsDatabase.updateLocationInfo(normalizedUrl, info.visitTimes, 
                    htmlProvided, isJavadoc, isCodeRelated, now);
            
            if (updated == null) {
                // requireLocationExists was set to true, but no existing entry was found in the database.
                return false;
            }
            
            // Log event when maximum location ID crosses certain thresholds.
            if (updated.rowCreated && updated.locationInfo.id > 1) {
                int prevLogId = (int)Math.floor(Math.log10(updated.locationInfo.id-1));
                int newLogId = (int)Math.floor(Math.log10(updated.locationInfo.id));
                if (newLogId != prevLogId) {
                    long maxId = locationsDatabase.getMaxLocationId();
                    if (maxId == updated.locationInfo.id) {
                        collector.logEvent(new LocationsIndexedMilestoneEvent(now, maxId));
                    }
                }
            }
            
            // Only record non-batch updates in the study data log
            if (info.visitTimes == null || info.visitTimes.isEmpty()) {
                collector.logEvent(new BrowserVisitEvent(now, updated.locationInfo,
                        updated.locationInfo.getFrecencyBoost(now)));
            }

            return true;
        } catch (Exception e) {
            throw new IndexerException("Exception indexing page: " + e);
        }
    }
    
    private String normalizeUrl(String inputUrl) {
        int fragmentIndex = inputUrl.lastIndexOf('#');
        if (fragmentIndex != -1) {
            String result = inputUrl.substring(0, fragmentIndex);
            return result;
        }
        return inputUrl;
    }
    
}
