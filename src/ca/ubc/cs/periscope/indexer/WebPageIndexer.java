package ca.ubc.cs.periscope.indexer;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
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

import ca.ubc.cs.periscope.indexer.messages.PageInfo;

public class WebPageIndexer {
    private static Logger log = Logger.getLogger(WebPageIndexer.class);
    
    public final static String URL_FIELD_NAME = "url";
    public final static String TITLE_FIELD_NAME = "title";
    public final static String CONTENT_FIELD_NAME = "content";
    
    private IndexerConfig config;
    private IndexWriter indexWriter;
    
    public WebPageIndexer(IndexerConfig config) throws IndexerException {
        this.config = config;

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
    
    public IndexWriter getIndexWriter() {
        return this.indexWriter;
    }
    
    public void indexPage(PageInfo info) throws CorruptIndexException, IOException {
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
        Field contentField = new Field(CONTENT_FIELD_NAME, new StringReader(text));
        doc.add(contentField);

        // Existing index (an old copy of this page may have been indexed) so 
        // we use updateDocument instead to replace the old one matching the exact 
        // URL, if present:
        indexWriter.updateDocument(new Term(URL_FIELD_NAME, info.url), doc);
        indexWriter.commit();
    }
    
}
