package ca.ubc.cs.hminer.indexer;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.concurrent.BlockingQueue;
import org.apache.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
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

public class WebPageIndexer implements Runnable {
    private static Logger log = Logger.getLogger(WebPageIndexer.class);
    
    private final String URL_FIELD_NAME = "url";
    private final String TITLE_FIELD_NAME = "title";
    private final String CONTENT_FIELD_NAME = "content";
    
    private IndexerConfig config;
    private IndexWriterConfig indexWriterConfig;
    private BlockingQueue<PageInfo> pagesQueue;
    private Directory index;
    
    public WebPageIndexer(IndexerConfig config, BlockingQueue<PageInfo> pagesQueue) {
        this.config = config;
        this.pagesQueue = pagesQueue;
    }
    
    public void start() throws IndexerException {
        try {
            index = FSDirectory.open(new File(config.getIndexPath()));
            indexWriterConfig = new IndexWriterConfig(Version.LUCENE_33, new WebPageAnalyzer());
            
            // Add new documents to the existing index:
            indexWriterConfig.setOpenMode(OpenMode.CREATE_OR_APPEND);

            new Thread(this).start();
        } catch (Exception e) {
            throw new IndexerException("Exception initializing web page indexer: " + e, e);
        }
    }
    
    public void run() {
        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

        try {
            while (true) {
                PageInfo info = pagesQueue.take();
                try {
                    indexPage(info);
                } catch (Exception e) {
                    log.error("Exception indexing page '" + info.url + "'", e);
                }
            }
            
        } catch (InterruptedException e) {
            log.error("Indexer thread interrupted", e);
        }
    }
    
    private void indexPage(PageInfo info) throws CorruptIndexException, IOException {
        IndexWriter indexWriter = new IndexWriter(index, indexWriterConfig);

        try {
            // make a new, empty document
            Document doc = new Document();
    
            // Add the URL of the page as a field named "url".  Use a
            // field that is indexed (i.e. searchable), but don't tokenize 
            // the field into separate words and don't index term frequency
            // or positional information:
            Field urlField = new Field(URL_FIELD_NAME, info.url, Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS);
            urlField.setOmitTermFreqAndPositions(true);
            doc.add(urlField);
    
            org.jsoup.nodes.Document jsoupDoc = Jsoup.parse(info.html);
            
            Field titleField = new Field(TITLE_FIELD_NAME, jsoupDoc.title(), Field.Store.YES, Field.Index.ANALYZED);
            titleField.setBoost(3.0f);
            doc.add(titleField);
            
            Elements scripts = jsoupDoc.select("script");
            for (Element script: scripts) {
                script.remove();
            }
            Elements plaintext = jsoupDoc.select("plaintext");
            for (Element element: plaintext) {
                element.remove();
            }
            
            Elements allElements = jsoupDoc.getAllElements();
            for (Element element: allElements) {
                if (element.hasText()) {
                    element.appendText(" ");
                }
            }
            
            String text = jsoupDoc.text();
            
            // Add the content of the page to a field named "content".  Specify a Reader,
            // so that the text of the file is tokenized and indexed, but not stored.
            // Note that FileReader expects the file to be in UTF-8 encoding.
            // If that's not the case searching for special characters will fail.
            doc.add(new Field(CONTENT_FIELD_NAME, new StringReader(text)));
    
            // Existing index (an old copy of this page may have been indexed) so 
            // we use updateDocument instead to replace the old one matching the exact 
            // URL, if present:
            indexWriter.updateDocument(new Term(URL_FIELD_NAME, info.url), doc);
        } finally {
            indexWriter.close();
        }
    }
    
}
