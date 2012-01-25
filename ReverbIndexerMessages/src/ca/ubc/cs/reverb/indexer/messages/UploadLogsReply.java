package ca.ubc.cs.reverb.indexer.messages;

public class UploadLogsReply extends IndexerReply {

    public UploadLogsReply() {
    }

    public UploadLogsReply(boolean errorOccurred, String errorMessage) {
        super(errorOccurred, errorMessage);
    }

}
