package ca.ubc.cs.reverb.indexer.messages;

import org.codehaus.jackson.annotate.JsonTypeInfo;
import org.codehaus.jackson.annotate.JsonSubTypes;

@JsonTypeInfo(use=JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
@JsonSubTypes({
                @JsonSubTypes.Type(value=BatchQueryRequest.class, name="batchQueryRequest"),
                @JsonSubTypes.Type(value=BatchQueryReply.class, name="batchQueryReply"),
                @JsonSubTypes.Type(value=DeleteLocationRequest.class, name="deleteLocationRequest"),
                @JsonSubTypes.Type(value=DeleteLocationReply.class, name="deleteLocationReply"),
                @JsonSubTypes.Type(value=UpdatePageInfoRequest.class, name="pageInfo"),
              })
public interface IndexerMessage {
}
