package ca.ubc.cs.reverb.indexer.messages;

public class UploadLogsRequest implements IndexerMessage {
    public UploadLogsRequest() { }
    public UploadLogsRequest(boolean isFinalUpload) {
        this.isFinalUpload = isFinalUpload;
    }
    
    public boolean isFinalUpload = false;
}
