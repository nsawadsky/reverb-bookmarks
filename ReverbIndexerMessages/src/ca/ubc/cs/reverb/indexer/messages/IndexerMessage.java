package ca.ubc.cs.reverb.indexer.messages;

import org.codehaus.jackson.annotate.JsonTypeInfo;
import org.codehaus.jackson.annotate.JsonSubTypes;

@JsonTypeInfo(use=JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
@JsonSubTypes({
                @JsonSubTypes.Type(value=BatchQueryRequest.class, name="batchQueryRequest"),
                @JsonSubTypes.Type(value=BatchQueryReply.class, name="batchQueryReply"),
                @JsonSubTypes.Type(value=DeleteLocationRequest.class, name="deleteLocationRequest"),
                @JsonSubTypes.Type(value=DeleteLocationReply.class, name="deleteLocationReply"),
                @JsonSubTypes.Type(value=UpdatePageInfoRequest.class, name="updatePageInfoRequest"),
                @JsonSubTypes.Type(value=CodeQueryRequest.class, name="codeQueryRequest"),
                @JsonSubTypes.Type(value=CodeQueryReply.class, name="codeQueryReply"),
                @JsonSubTypes.Type(value=UploadLogsRequest.class, name="uploadLogsRequest"),
                @JsonSubTypes.Type(value=UploadLogsReply.class, name="uploadLogsReply"),
                @JsonSubTypes.Type(value=LogClickRequest.class, name="logClickRequest"),
                @JsonSubTypes.Type(value=LogClickReply.class, name="logClickReply"),
              })
public interface IndexerMessage {
}
