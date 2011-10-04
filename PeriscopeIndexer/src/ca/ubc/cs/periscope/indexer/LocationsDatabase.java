package ca.ubc.cs.periscope.indexer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocationsDatabase {
    private static final String JDBC_SQLITE = "jdbc:sqlite:";
    private static final int VISIT_HALF_LIFE_MSECS = 3 * 30 * 24 * 60 * 60 * 1000;
    private static final double DECAY = Math.log(0.5) / VISIT_HALF_LIFE_MSECS;
    
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
    
    public synchronized void updateLocationInfo(String url) throws IndexerException { 
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(JDBC_SQLITE + config.getLocationsDatabasePath());
            Statement stmt = conn.createStatement();
    
            String query = "SELECT last_visit_time, visit_count, frecency_boost FROM locations WHERE url = '" + url + "'";
            ResultSet rs = stmt.executeQuery(query);
            int visitCount = 0;
            double frecencyBoost = 0.0;
            long lastVisitTime = 0L;
            long currentTime = new Date().getTime();
            if (rs.next()) {
                lastVisitTime = rs.getLong(1);
                visitCount = rs.getInt(2);
                frecencyBoost = rs.getDouble(3);
                
                long timeDelta = currentTime - lastVisitTime;
                frecencyBoost = frecencyBoost * Math.exp(DECAY * timeDelta);
            }
            lastVisitTime = currentTime;
            visitCount += 1;
            frecencyBoost += 1.0;
            
            StringBuilder update 
            
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
                            "last_visit_time INTEGER NOT NULL, visit_count INTEGER NOT NULL, frecency_boost DOUBLE NOT NULL)";
                    
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
