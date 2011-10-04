package ca.ubc.cs.periscope.indexer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wrapper around the SQLITE locations database.  We prevent concurrent access to this database by synchronizing the
 * public accessors of this class (and by the fact that there should only ever be one instance of the indexer process).
 */
public class LocationsDatabase {
    private static final String JDBC_SQLITE = "jdbc:sqlite:";
    private static final long VISIT_HALF_LIFE_MSECS = 3 * 30 * 24 * 60 * 60 * 1000L;
    private static final float DECAY = (float)Math.log(0.5) / VISIT_HALF_LIFE_MSECS;
    
    private IndexerConfig config;

    public LocationsDatabase(IndexerConfig config) throws IndexerException {
        this.config = config;
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (Exception e) {
            throw new IndexerException("Exception loading SQLite JDBC driver: " + e, e);
        }
        createLocationsTableIfNecessary();
    }
    
    public synchronized Map<String, Float> getFrecencyBoosts(List<String> urls) throws IndexerException {
        Connection conn = null;
        Map<String, Float> results = new HashMap<String, Float>();
        if (urls.size() == 0) {
            return results;
        }
        try {
            conn = DriverManager.getConnection(JDBC_SQLITE + config.getLocationsDatabasePath());
            Statement stmt = conn.createStatement();
            
            StringBuilder query = new StringBuilder("SELECT url, frecency_boost FROM locations WHERE url IN ('");
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
                Float frecencyBoost = rs.getFloat(2);
                results.put(url, frecencyBoost);
            }
        } catch (SQLException e) {
            throw new IndexerException("Error getting frecency boosts: " + e);
        } finally {
            if (conn != null) { 
                try { 
                    conn.close(); 
                } catch (SQLException e) {} 
            }
        }
        return results;
    }
    
    public synchronized Date updateLocationInfo(String url) throws IndexerException { 
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(JDBC_SQLITE + config.getLocationsDatabasePath());
            Statement stmt = conn.createStatement();
    
            String query = "SELECT id, last_visit_time, visit_count, frecency_boost FROM locations WHERE url = '" + url + "'";
            ResultSet rs = stmt.executeQuery(query);
            long id = -1;
            int visitCount = 0;
            float frecencyBoost = 0.0F;
            long lastVisitTime = 0;
            long currentTime = new Date().getTime();
            if (rs.next()) {
                id = rs.getLong(1);
                lastVisitTime = rs.getLong(2);
                visitCount = rs.getInt(3);
                frecencyBoost = rs.getFloat(4);
                
                long timeDelta = currentTime - lastVisitTime;
                frecencyBoost = frecencyBoost * (float)Math.exp(DECAY * timeDelta);
            }
            visitCount += 1;
            frecencyBoost += 1.0;
            
            StringBuilder update = new StringBuilder("INSERT OR REPLACE INTO locations (id, url, last_visit_time, visit_count, frecency_boost) VALUES " +
                    "(?, ?, ?, ?, ?)");
            
            PreparedStatement prep = conn.prepareStatement(update.toString());
            if (id != -1) {
                prep.setLong(1, id);
            } 
            prep.setString(2, url);
            prep.setLong(3, currentTime);
            prep.setInt(4, visitCount);
            prep.setFloat(5, frecencyBoost);
            
            prep.execute();
            
            if (lastVisitTime == 0) {
                return null;
            }
            return new Date(lastVisitTime);
        } catch (SQLException e) {
            throw new IndexerException("Error updating location info: " + e);
        } finally {
            if (conn != null) { 
                try { 
                    conn.close(); 
                } catch (SQLException e) {} 
            }
        }
    }
    
    private synchronized void createLocationsTableIfNecessary() throws IndexerException {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(JDBC_SQLITE + config.getLocationsDatabasePath());
            Statement stmt = conn.createStatement();
            
            String query = "SELECT name FROM sqlite_master WHERE type='table' AND name='locations'";
            
            ResultSet rs = stmt.executeQuery(query);
            if (!rs.next()) {
                conn.setAutoCommit(false);
                
                try {
                    stmt = conn.createStatement();
                    
                    String update = "CREATE TABLE locations(id INTEGER PRIMARY KEY, url LONGVARCHAR NOT NULL, " +
                            "last_visit_time INTEGER NOT NULL, visit_count INTEGER NOT NULL, frecency_boost FLOAT NOT NULL)";
                    
                    stmt.executeUpdate(update);
                    
                    update = "CREATE UNIQUE INDEX locations_by_url ON locations(url)";

                    stmt.executeUpdate(update);
                    
                    update = "CREATE INDEX locations_by_last_visit_time ON locations(last_visit_time)";
                    
                    stmt.executeUpdate(update);
                    
                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            }
        } catch (SQLException e) {
            throw new IndexerException("Error checking for/creating locations table: " + e);
        } finally {
            if (conn != null) { 
                try { 
                    conn.close(); 
                } catch (SQLException e) {} 
            }
        }
    }
}
