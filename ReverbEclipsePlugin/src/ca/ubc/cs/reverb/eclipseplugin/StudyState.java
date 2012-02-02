package ca.ubc.cs.reverb.eclipseplugin;

import java.util.ArrayList;
import java.util.List;

import ca.ubc.cs.reverb.indexer.messages.Location;

/**
 * This class is serialized/deserialized to/from JSON using the Jackson library.
 */
public class StudyState {
    /**
     * Activity is measured in 15 minute intervals.
     */
    //public final static int ACTIVITY_INTERVAL_MSECS = 15 * 60 * 1000;
    public final static int ACTIVITY_INTERVAL_MSECS = 15 * 1000;
    
    /**
     * At 3 and 6 hours, we prompt to upload logs.
     */
    //public final static int LOG_UPLOAD_INTERVALS = 12; 
    public final static int LOG_UPLOAD_INTERVALS = 2; 

    /**
     * Number of successful log uploads to complete study.
     */
    public final static int LOG_UPLOADS_REQUIRED = 2;
    
    /**
     * Recommendations clicked by the user since last prompt for feedback.
     */
    public List<Location> recommendationsClicked = new ArrayList<Location>();
    
    /**
     * Intervals counted in which the user has actively viewed or edited Java code.
     */
    public int activeIntervals = 0;
    
    /**
     * Last interval in which activity occurred.
     */
    public long lastActiveInterval = 0;
    
    /**
     * Is a log upload pending?
     */
    public boolean uploadPending = false;
    
    /**
     * Number of successful log uploads.
     */
    public int successfulLogUploads = 0;
}
