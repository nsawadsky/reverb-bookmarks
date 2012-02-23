package ca.ubc.cs.reverb.indexer.study;

public class PluginViewStateChangedEvent extends StudyDataEvent {

    public PluginViewStateChangedEvent(long timestamp, boolean isViewOpen) {
        super(timestamp, StudyEventType.PLUGIN_VIEW_STATE_CHANGED);
        this.isViewOpen = isViewOpen;
    }

    public boolean isViewOpen;
    
    @Override
    public String getLogLine() {
        return super.getLogLine() + ", " + isViewOpen;
    }
}
