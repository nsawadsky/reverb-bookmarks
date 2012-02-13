package ca.ubc.cs.reverb.indexer;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import ca.ubc.cs.reverb.indexer.messages.BatchQueryReply;
import ca.ubc.cs.reverb.indexer.messages.IndexerQuery;
import ca.ubc.cs.reverb.indexer.messages.Location;
import ca.ubc.cs.reverb.indexer.messages.QueryResult;
import ca.ubc.cs.reverb.indexer.messages.UpdatePageInfoRequest;
import ca.ubc.cs.reverb.indexer.study.StudyDataCollector;

public class WebPageIndexerTest {

    @Test
    public void testIndexPage() throws IOException, IndexerException {
        LocationsDatabase locationsDatabase = null;
        WebPageIndexer indexer = null;
        SharedIndexReader reader = null;
        
        final File dataFolder = createTempFolder();
        try {
            IndexerConfig config = new IndexerConfig() {
                @Override
                public String getBasePath() {
                    return dataFolder.getAbsolutePath();
                }
            };
            
            locationsDatabase = new LocationsDatabase(config);
            StudyDataCollector collector = new StudyDataCollector(config);
            indexer = new WebPageIndexer(config, locationsDatabase, collector);
            reader = indexer.getNewIndexReader();
            
            WebPageSearcher searcher = new WebPageSearcher(config, reader, locationsDatabase, collector);
            
            final String testUrl1 = "http://www.test.com/testurl1";
            final String pageText = "This page is about an elephant.";
            UpdatePageInfoRequest request = new UpdatePageInfoRequest(testUrl1, pageText);
            
            assertTrue(indexer.indexPage(request));
            
            assertNull(getSingleQueryResult(searcher, "giraffe"));
            
            Location firstHit = getSingleQueryResult(searcher, "elephant");
            assertNotNull(firstHit);
            assertEquals(testUrl1, firstHit.url);

            request = new UpdatePageInfoRequest(testUrl1, null);
            
            assertTrue(indexer.indexPage(request));
            
            firstHit = getSingleQueryResult(searcher, "elephant");
            assertNotNull(firstHit);
            assertEquals(testUrl1, firstHit.url);
            
            final String testUrl2 = "http://www.test.com/testurl2";
            request = new UpdatePageInfoRequest(testUrl2, null);
            
            assertFalse(indexer.indexPage(request));
            
        } finally {
            if (reader != null) {
                reader.close();
            }
            if (indexer != null) {
                indexer.close();
            }
            if (locationsDatabase != null) {
                locationsDatabase.close();
            }
            
            recursiveDeleteFileOrFolder(dataFolder);
        }
            
    }

    private Location getSingleQueryResult(WebPageSearcher searcher, String queryString) throws IndexerException {
        IndexerQuery query = new IndexerQuery(queryString, null);
        
        BatchQueryReply reply = searcher.performSearch(Arrays.asList(query));
        
        assertFalse(reply.errorOccurred);
        List<QueryResult> results = reply.queryResults;
        if (results.size() == 0) {
            return null;
        }
        assertEquals(1, results.size());
       
        QueryResult result = results.get(0);
        
        if (result.locations.size() == 0) {
            return null;
        }
        
        assertEquals(1, result.locations.size());
        
        return result.locations.get(0);
    }
    
    private static File createTempFolder() throws IOException {
        File tempFolder = File.createTempFile("reverb", null);

        if(!tempFolder.delete()) {
            throw new IOException("Could not delete temp file: " + tempFolder.getAbsolutePath());
        }

        if (!tempFolder.mkdir()) {
            throw new IOException("Could not create temp directory: " + tempFolder.getAbsolutePath());
        }

        return tempFolder;
    }

    private static void recursiveDeleteFileOrFolder(File fileOrFolder) throws IOException {
        if (fileOrFolder.isDirectory()) {
            for (File contained : fileOrFolder.listFiles())
                recursiveDeleteFileOrFolder(contained);
        }
        if (!fileOrFolder.delete()) {
            throw new IOException("Failed to delete file/folder: " + fileOrFolder);
        }
    }
}
