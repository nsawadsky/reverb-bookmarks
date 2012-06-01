package ca.ubc.cs.reverb.indexer;

import java.io.IOException;

import org.apache.lucene.index.IndexReader;

/**
 * Wraps Lucene's IndexReader, allowing multiple consumers to obtain the 
 * latest IndexReader instance (given that any consumer may choose to reopen the instance).
 * 
 * IndexReader is thread-safe, but we must synchronize public methods of this class to ensure
 * that all threads see the latest IndexReader reference.
 */
public class SharedIndexReader {
    private IndexReader reader;
    
    public SharedIndexReader(IndexReader reader) {
        this.reader = reader;
    }
    
    public synchronized IndexReader reopen() throws IOException {
        IndexReader newReader = reader.reopen();
        if (newReader != reader) {
            try {
                reader.close();
            } catch (IOException e) { }
        }
        reader = newReader;
        return reader;
    }
    
    public synchronized void close() throws IOException {
        reader.close();
    }
}
