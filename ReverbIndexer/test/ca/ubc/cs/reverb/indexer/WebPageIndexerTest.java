package ca.ubc.cs.reverb.indexer;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
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
            
            String localHostUrl = "http://127.0.0.1:55009/help/nftopic/Action.html";
            String actionClassPageText = "This is about the Action class.";
            UpdatePageInfoRequest request = new UpdatePageInfoRequest(localHostUrl, actionClassPageText);
            assertFalse(indexer.indexPage(request));
            
            final String testUrl1 = "http://www.test.com/testurl1";
            final String pageText = "This page is about an elephant.";
            request = new UpdatePageInfoRequest(testUrl1, pageText);
            
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
                indexer.shutdown();
            }
            if (locationsDatabase != null) {
                locationsDatabase.close();
            }
            
            recursiveDeleteFileOrFolder(dataFolder);
        }
            
    }
    
    @Test
    public void testTokenization() throws IOException, IndexerException {
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
            
            final String testUrl = "http://www.test.com/testurl1";
            InputStreamReader testFileReader = new InputStreamReader(WebPageSearcherTest.class.getResourceAsStream("IndexerTestFile.txt"));
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[1024];
            int bytesRead = 0;
            while ((bytesRead = testFileReader.read(buffer)) > 0) {
                builder.append(buffer, 0, bytesRead);
            }
            testFileReader.close();
            
            UpdatePageInfoRequest request = new UpdatePageInfoRequest(testUrl, builder.toString());

            assertTrue(indexer.indexPage(request));
            
            assertNull(getSingleQueryResult(searcher, "giraffe"));
            
            Location firstHit = getSingleQueryResult(searcher, "Declared_Class");
            assertNotNull(firstHit);
            assertEquals(testUrl, firstHit.url);

            assertNotNull(getSingleQueryResult(searcher, "testpackage.subpackage"));
            assertNotNull(getSingleQueryResult(searcher, "testpackage"));
            assertNotNull(getSingleQueryResult(searcher, "subpackage"));
            assertNotNull(getSingleQueryResult(searcher, "importedpackage.importedsubpackage"));
            assertNotNull(getSingleQueryResult(searcher, "importedpackage"));
            assertNotNull(getSingleQueryResult(searcher, "importedsubpackage"));
            assertNotNull(getSingleQueryResult(searcher, "usedClass.invokedMethod"));
            assertNotNull(getSingleQueryResult(searcher, "usedClass"));
            assertNotNull(getSingleQueryResult(searcher, "invokedMethod"));
            assertNotNull(getSingleQueryResult(searcher, "AnnotationReference"));
            assertNotNull(getSingleQueryResult(searcher, "annotationreference"));
            
        } finally {
            if (reader != null) {
                reader.close();
            }
            if (indexer != null) {
                indexer.shutdown();
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
