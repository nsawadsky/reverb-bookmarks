package ca.ubc.cs.reverb.indexer.messages;

public class LogClickReply extends IndexerReply {

    public LogClickReply() {
    }

    public LogClickReply(boolean errorOccurred, String errorMessage) {
        super(errorOccurred, errorMessage);
    }

}
