package ca.ubc.cs.reverb.indexer.messages;

import org.codehaus.jackson.annotate.JsonTypeInfo;
import org.codehaus.jackson.annotate.JsonSubTypes;

@JsonTypeInfo(use=JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
@JsonSubTypes({
                @JsonSubTypes.Type(value=IndexerReply.class, name="indexerReply"),
                @JsonSubTypes.Type(value=BatchQueryRequest.class, name="batchQueryRequest"),
                @JsonSubTypes.Type(value=BatchQueryReply.class, name="batchQueryReply"),
                @JsonSubTypes.Type(value=DeleteLocationRequest.class, name="deleteLocationRequest"),
                @JsonSubTypes.Type(value=UpdatePageInfoRequest.class, name="updatePageInfoRequest"),
                @JsonSubTypes.Type(value=CodeQueryRequest.class, name="codeQueryRequest"),
                @JsonSubTypes.Type(value=CodeQueryReply.class, name="codeQueryReply"),
                @JsonSubTypes.Type(value=UploadLogsRequest.class, name="uploadLogsRequest"),
                @JsonSubTypes.Type(value=LogClickRequest.class, name="logClickRequest"),
                @JsonSubTypes.Type(value=ShutdownRequest.class, name="shutdownRequest"),
                @JsonSubTypes.Type(value=LogPluginViewStateRequest.class, name="logViewStateRequest"),
              })
public interface IndexerMessage {
}
