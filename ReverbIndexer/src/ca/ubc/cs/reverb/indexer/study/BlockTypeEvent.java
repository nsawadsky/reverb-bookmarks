package ca.ubc.cs.reverb.indexer.study;

public class BlockTypeEvent extends StudyDataEvent {
    public BlockTypeEvent(long timestamp) {
        super(timestamp, StudyEventType.BLOCK_TYPE);
    }

}
