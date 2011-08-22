package ca.ubc.cs.hminer.indexer;

public class IndexerException extends Exception {

    public IndexerException() {
        super();
    }

    public IndexerException(String message, Throwable cause) {
        super(message, cause);
    }

    public IndexerException(String message) {
        super(message);
    }

    public IndexerException(Throwable cause) {
        super(cause);
    }

}
