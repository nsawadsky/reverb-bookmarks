package ca.ubc.cs.reverb.eclipseplugin.reports;

import java.util.ArrayList;
import java.util.List;


public class RatingsReport {
    public RatingsReport() { }
    
    public RatingsReport(List<LocationRating> ratings) { 
        this.locationRatings = ratings;
    }

    public List<LocationRating> locationRatings = new ArrayList<LocationRating>();
}
