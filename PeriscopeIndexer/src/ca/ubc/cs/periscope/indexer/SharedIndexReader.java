package ca.ubc.cs.periscope.indexer;

import java.io.IOException;

import org.apache.lucene.index.IndexReader;

/**
 * Wraps Lucene's IndexReader, allowing multiple consumers to obtain the 
 * latest IndexReader instance (given that any consumer may choose to reopen the instance).
 */
public class SharedIndexReader {
    private volatile IndexReader reader;
    
    public SharedIndexReader(IndexReader reader) {
        this.reader = reader;
    }
    
    public IndexReader reopen() throws IOException {
        reader = reader.reopen();
        return reader;
    }
    
    public IndexReader get() {
        return reader;
    }
}
