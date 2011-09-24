package ca.ubc.cs.periscope.indexer.messages;

import org.codehaus.jackson.annotate.JsonTypeInfo;
import org.codehaus.jackson.annotate.JsonSubTypes;

@JsonTypeInfo(use=JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
@JsonSubTypes({
                @JsonSubTypes.Type(value=IndexerBatchQuery.class, name="indexerBatchQuery"),
                @JsonSubTypes.Type(value=BatchQueryResult.class, name="batchQueryResult"),
                @JsonSubTypes.Type(value=PageInfo.class, name="pageInfo"),
              })
public interface IndexerMessage {
}
