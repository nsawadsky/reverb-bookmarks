package ca.ubc.cs.reverb.eclipseplugin;

import java.util.ArrayList;
import java.util.List;

import ca.ubc.cs.reverb.eclipseplugin.reports.LocationRating;

/**
 * This class is serialized/deserialized to/from JSON using the Jackson library.
 */
public class StudyState {
    /**
     * View used to define fields which are not to be included in study data sent to server, only serialized locally to study state.
     */
    public static class LocalUseOnly { };
    
    /**
     * Activity is measured in 15 minute intervals.
     */
    public final static int ACTIVITY_INTERVAL_MSECS = 15 * 60 * 1000;
    //public final static int ACTIVITY_INTERVAL_MSECS = 15 * 1000;
    
    /**
     * At 3 and 6 hours, we prompt to upload logs.
     */
    public final static int LOG_UPLOAD_INTERVALS = 12; 
    //public final static int LOG_UPLOAD_INTERVALS = 2; 

    /**
     * Number of successful log uploads to complete study.
     */
    public final static int LOG_UPLOADS_REQUIRED = 2;
    
    /**
     * Recommendations clicked by the user, along with rating information.
     */
    public List<LocationRating> locationRatings = new ArrayList<LocationRating>();
    
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
