package ca.ubc.cs.reverb.indexer.study;

import java.util.List;

public class GenericClientEvent extends StudyDataEvent {

    public GenericClientEvent(long timestamp, String clientEventType, List<String> attributes) {
        super(timestamp, StudyEventType.GENERIC_CLIENT_EVENT);
        this.eventType = clientEventType;
        this.attributes = attributes;
    }

    public String eventType;
    public List<String> attributes;
    
    @Override
    public String getLogLine() {
        StringBuilder logLine = new StringBuilder(super.getLogLine());
        logLine.append(", ");
        logLine.append(eventType);
        for (String attr: attributes) {
            logLine.append(", ");
            logLine.append(attr);
        }
        return logLine.toString();
    }
}
