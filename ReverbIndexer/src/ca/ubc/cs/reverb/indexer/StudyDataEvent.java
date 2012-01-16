package ca.ubc.cs.reverb.indexer;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class StudyDataEvent {
    private List<Field> fields = new ArrayList<Field>();
    
    private final static String TIMESTAMP_FIELD = "Timestamp";
    private final static String EVENT_TYPE = "EventType";
    private final static String LOCATION_ID = "LocationId";
    private final static String IS_JAVADOC = "IsJavadoc";
    
    public class Field {
        public Field(String fieldName, String fieldValue) {
            this.fieldName = fieldName;
            this.fieldValue = fieldValue;
        }
        
        public String fieldName;
        public String fieldValue;
    }
   
    public static StudyDataEvent createEvent(Date timestamp, StudyEventType eventType,
            long locationId) {
        StudyDataEvent result = new StudyDataEvent();
        result.addField(TIMESTAMP_FIELD, Long.toString(timestamp.getTime()));
        result.addField(EVENT_TYPE, eventType.toString());
        result.addField(LOCATION_ID, Long.toString(locationId));
        return result;
    }
    
    public static StudyDataEvent createEvent(Date timestamp, StudyEventType eventType, long locationId, boolean isJavadoc) {
        StudyDataEvent result = createEvent(timestamp, eventType, locationId);
        result.addField(IS_JAVADOC, Boolean.toString(isJavadoc));
        return result;
    }
    
    public void addField(String fieldName, String fieldValue) {
        this.fields.add(new Field(fieldName, fieldValue));
    }
    
    public List<Field> getFields() {
        return fields;
    }
}
