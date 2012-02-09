package ca.ubc.cs.reverb.eclipseplugin;

import java.util.ArrayList;
import java.util.List;

public class RatingsReport {
    public RatingsReport() { }
    
    public RatingsReport(List<LocationAndRating> ratings) { 
        this.locationRatings = ratings;
    }

    public List<LocationAndRating> locationRatings = new ArrayList<LocationAndRating>();
}
