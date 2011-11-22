package ca.ubc.cs.reverb.indexer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wrapper around the SQLITE locations database.  We prevent concurrent access to this database by synchronizing the
 * public accessors of this class (and by the fact that there should only ever be one instance of the indexer process).
 */
public class LocationsDatabase {
    public final static float MAX_FRECENCY_BOOST = 5.0F;
    
    private static final String JDBC_SQLITE = "jdbc:sqlite:";
    private static final long VISIT_HALF_LIFE_MSECS = 6 * 30 * 24 * 60 * 60 * 1000L;
    private static final float DECAY = (float)Math.log(0.5) / VISIT_HALF_LIFE_MSECS;
    
    private IndexerConfig config;
    private Connection connection;

    public LocationsDatabase(IndexerConfig config) throws IndexerException {
        this.config = config;
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (Exception e) {
            throw new IndexerException("Exception loading SQLite JDBC driver: " + e, e);
        }
        try {
            connection = DriverManager.getConnection(JDBC_SQLITE + config.getLocationsDatabasePath());
        } catch (SQLException e) {
            throw new IndexerException("Exception connecting to locations database: " + e, e);
        }
        createLocationsTableIfNecessary();
    }
    
    public synchronized Map<String, Float> getFrecencyBoosts(List<String> urls) throws IndexerException {
        Map<String, Float> results = new HashMap<String, Float>();
        if (urls.size() == 0) {
            return results;
        }
        try {
            Date now = new Date();
            Statement stmt = connection.createStatement();
            
            StringBuilder query = new StringBuilder("SELECT url, last_visit_time, visit_count, frecency_boost FROM locations WHERE url IN ('");
            boolean isFirst = true;
            for (String url: urls) {
                if (isFirst) {
                    isFirst = false;
                } else {
                    query.append(", '");
                }
                query.append(url);
                query.append("'");
            }
            query.append(")");
            ResultSet rs = stmt.executeQuery(query.toString());
            while (rs.next()) {
                String url = rs.getString(1);
                long lastVisitTime = rs.getLong(2);
                //int visitCount = rs.getInt(3);
                
                Float frecencyBoost = rs.getFloat(4);
                frecencyBoost = frecencyBoost * (float)Math.exp(DECAY * (now.getTime() - lastVisitTime));
                
                results.put(url, (float)Math.min(frecencyBoost, MAX_FRECENCY_BOOST));
            }
        } catch (SQLException e) {
            throw new IndexerException("Error getting frecency boosts: " + e, e);
        } 
        return results;
    }
    
    public synchronized void updateLocationInfo(String url, List<Long> visitTimes) throws IndexerException { 
        try {
            long currentTime = new Date().getTime();
            if (visitTimes == null || visitTimes.size() == 0) {
                visitTimes = new ArrayList<Long>();
                visitTimes.add(currentTime);
            }

            int visitCount = 0;
            float frecencyBoost = 0.0F;
            long id = -1;
            
            Statement stmt = connection.createStatement();
    
            String query = "SELECT id, last_visit_time, visit_count, frecency_boost FROM locations WHERE url = '" + url + "'";
            ResultSet rs = stmt.executeQuery(query);
            if (rs.next()) {
                id = rs.getLong(1);
                
                long lastVisitTime = rs.getLong(2);
                visitCount = rs.getInt(3);
                frecencyBoost = rs.getFloat(4);
                
                long timeDelta = currentTime - lastVisitTime;
                frecencyBoost = frecencyBoost * (float)Math.exp(DECAY * timeDelta);
            }

            visitCount += visitTimes.size();
            for (Long visitTime: visitTimes) {
                frecencyBoost += (float)Math.exp(DECAY * (currentTime - visitTime));
            }
            
            StringBuilder update = new StringBuilder("INSERT OR REPLACE INTO locations (id, url, last_visit_time, visit_count, frecency_boost) VALUES " +
                    "(?, ?, ?, ?, ?)");
            
            PreparedStatement prep = connection.prepareStatement(update.toString());
            if (id != -1) {
                prep.setLong(1, id);
            } 
            prep.setString(2, url);
            prep.setLong(3, currentTime);
            prep.setInt(4, visitCount);
            prep.setFloat(5, frecencyBoost);
            
            prep.execute();
            
        } catch (SQLException e) {
            throw new IndexerException("Error updating location info: " + e, e);
        } 
    }
    
    private synchronized void createLocationsTableIfNecessary() throws IndexerException {
        try {
            Statement stmt = connection.createStatement();
            
            String query = "SELECT name FROM sqlite_master WHERE type='table' AND name='locations'";
            
            ResultSet rs = stmt.executeQuery(query);
            if (!rs.next()) {
                connection.setAutoCommit(false);
                
                try {
                    stmt = connection.createStatement();
                    
                    String update = "CREATE TABLE locations(id INTEGER PRIMARY KEY, url LONGVARCHAR NOT NULL, " +
                            "last_visit_time INTEGER NOT NULL, visit_count INTEGER NOT NULL, frecency_boost FLOAT NOT NULL)";
                    
                    stmt.executeUpdate(update);
                    
                    update = "CREATE UNIQUE INDEX locations_by_url ON locations(url)";

                    stmt.executeUpdate(update);
                    
                    update = "CREATE INDEX locations_by_last_visit_time ON locations(last_visit_time)";
                    
                    stmt.executeUpdate(update);
                    
                    connection.commit();
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                }
                finally {
                    connection.setAutoCommit(true);
                }
            }
        } catch (SQLException e) {
            throw new IndexerException("Error checking for/creating locations table: " + e, e);
        } 
    }
}