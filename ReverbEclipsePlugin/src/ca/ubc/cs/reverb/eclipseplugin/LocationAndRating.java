package ca.ubc.cs.reverb.eclipseplugin;

import ca.ubc.cs.reverb.indexer.messages.Location;

public class LocationAndRating {
    public LocationAndRating(Location location) {
        this.location = location;
    }
    
    public Location location;
    public int rating = 0;
    public String comment;
}
