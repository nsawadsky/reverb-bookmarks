package ca.ubc.cs.reverb.indexer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wrapper around the SQLITE locations database.  We prevent concurrent access to this database by synchronizing the
 * public accessors of this class (and by the fact that there should only ever be one instance of the indexer process).
 */
public class LocationsDatabase {
    private static final long VISIT_HALF_LIFE_MSECS = 6 * 30 * 24 * 60 * 60 * 1000L;
    public static final float FRECENCY_DECAY = (float)Math.log(0.5) / VISIT_HALF_LIFE_MSECS;

    private static final String JDBC_SQLITE = "jdbc:sqlite:";
    
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
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            throw new IndexerException("Exception connecting to locations database: " + e, e);
        }
        createLocationsTableIfNecessary();
    }
    
    public synchronized Map<String, LocationInfo> getLocationInfos(List<String> urls) throws IndexerException {
        Map<String, LocationInfo> results = new HashMap<String, LocationInfo>();
        if (urls.size() == 0) {
            return results;
        }
        try {
            Date now = new Date();
            Statement stmt = connection.createStatement();
            
            StringBuilder query = new StringBuilder("SELECT id, url, last_visit_time, visit_count, frecency_boost, is_javadoc, is_code_related FROM locations WHERE url IN ('");
            boolean isFirst = true;
            for (String url: urls) {
                if (isFirst) {
                    isFirst = false;
                } else {
                    query.append(", '");
                }
                query.append(escapeForSQL(url));
                query.append("'");
            }
            query.append(")");
            ResultSet rs = stmt.executeQuery(query.toString());
            while (rs.next()) {
                long id = rs.getLong(1);
                String url = rs.getString(2);
                long lastVisitTime = rs.getLong(3);
                int visitCount = rs.getInt(4);
                
                Float frecencyBoost = rs.getFloat(5);
                
                boolean isJavadoc = (rs.getInt(6) != 0);
                boolean isCodeRelated = (rs.getInt(7) != 0);
                
                results.put(url, new LocationInfo(id, url, lastVisitTime, visitCount, frecencyBoost, isJavadoc, isCodeRelated));
            }
        } catch (SQLException e) {
            throw new IndexerException("Error getting frecency boosts: " + e, e);
        } 
        return results;
    }
    
    public synchronized LocationInfo getLocationInfo(String url)  throws IndexerException {
        Map<String, LocationInfo> resultMap = getLocationInfos(Arrays.asList(url));
        return resultMap.get(url);
    }
    
    /**
     * The delete is committed immediately (along with any pending updates).
     */
    public synchronized void deleteLocationInfo(String url) throws IndexerException { 
        try {
            Statement stmt = connection.createStatement();
            String update = "DELETE FROM locations WHERE (url = '" + escapeForSQL(url) + "')";
            stmt.executeUpdate(update);
            
            connection.commit();
        } catch (SQLException e1) {
            try { 
                connection.rollback(); 
            } catch (SQLException e2) { }
            throw new IndexerException("Error deleting location info: " + e1, e1);
        }
    }
    
    public synchronized void commitChanges() throws IndexerException {
        try {
            connection.commit();
        } catch (SQLException e1) {
            try { 
                connection.rollback(); 
            } catch (SQLException e2) { }
            throw new IndexerException("Exception committing changes: " + e1, e1);
        }
    }
    
    public synchronized Date getLastVisitDate(String url) throws IndexerException {
        try {
            Statement stmt = connection.createStatement();
    
            String query = "SELECT last_visit_time FROM locations WHERE url = '" + escapeForSQL(url) + "'";
            ResultSet rs = stmt.executeQuery(query);
            if (rs.next()) {
                long lastVisitTime = rs.getLong(1);
                return new Date(lastVisitTime);
            }
            return null;
        } catch (SQLException e) {
            throw new IndexerException("Error getting last visit date for url '" + url + "': " + e, e); 
        }
    }
    
    /**
     * Note that the change is not committed immediately.  A separate call to commitChanges
     * is required.
     * 
     * @return The updated location info.
     */
    public synchronized LocationInfo updateLocationInfo(String url, List<Long> inputVisitTimes, Boolean isJavadoc, Boolean isCodeRelated,
            long currentTime) throws IndexerException { 
        try {
            List<Long> visitTimes = null;
            if (inputVisitTimes == null || inputVisitTimes.size() == 0) {
                visitTimes = new ArrayList<Long>();
                visitTimes.add(currentTime);
            } else {
                visitTimes = new ArrayList<Long>(inputVisitTimes);
            }
            Collections.sort(visitTimes);
            
            long lastVisitTime = visitTimes.get(visitTimes.size()-1);

            int visitCount = 0;
            float frecencyBoost = 0.0F;
            long id = -1;
            boolean prevIsJavadoc = false;
            boolean prevIsCodeRelated = false;
            
            Statement stmt = connection.createStatement();
    
            String query = "SELECT id, last_visit_time, visit_count, frecency_boost, is_javadoc, is_code_related FROM locations WHERE url = '" + escapeForSQL(url) + "'";
            ResultSet rs = stmt.executeQuery(query);
            if (rs.next()) {
                id = rs.getLong(1);
                
                long oldLastVisitTime = rs.getLong(2);
                visitCount = rs.getInt(3);
                frecencyBoost = rs.getFloat(4);
                
                long timeDelta = lastVisitTime - oldLastVisitTime;
                frecencyBoost = frecencyBoost * (float)Math.exp(FRECENCY_DECAY * timeDelta);
                
                prevIsJavadoc = (rs.getInt(5) != 0);
                prevIsCodeRelated = (rs.getInt(6) != 0);
            }
            
            if (isJavadoc == null) {
                isJavadoc = prevIsJavadoc;
            }
            
            if (isCodeRelated == null) {
                isCodeRelated = prevIsCodeRelated;
            }

            visitCount += visitTimes.size();
            for (Long visitTime: visitTimes) {
                frecencyBoost += (float)Math.exp(FRECENCY_DECAY * (lastVisitTime - visitTime));
            }
            
            StringBuilder update = new StringBuilder("INSERT OR REPLACE INTO locations (id, url, last_visit_time, visit_count, frecency_boost, is_javadoc, is_code_related) VALUES " +
                    "(?, ?, ?, ?, ?, ?, ?)");
            
            PreparedStatement prep = connection.prepareStatement(update.toString());
            if (id != -1) {
                prep.setLong(1, id);
            } 
            prep.setString(2, url);
            prep.setLong(3, lastVisitTime);
            prep.setInt(4, visitCount);
            prep.setFloat(5, frecencyBoost);
            prep.setInt(6, isJavadoc ? 1 : 0);
            prep.setInt(7, isCodeRelated ? 1 : 0);
            
            prep.execute();
            
            if (id != -1) {
                return new LocationInfo(id, url, lastVisitTime, visitCount, frecencyBoost, isJavadoc, isCodeRelated);
            } else {
                return getLocationInfo(url);
            }
        } catch (SQLException e) {
            throw new IndexerException("Error updating location info: " + e, e);
        } 
    }
    
    private String escapeForSQL(String str) {
        return str.replace("'", "''");
    }
    
    private synchronized void createLocationsTableIfNecessary() throws IndexerException {
        try {
            Statement stmt = connection.createStatement();
            
            String query = "SELECT name FROM sqlite_master WHERE type='table' AND name='locations'";
            
            ResultSet rs = stmt.executeQuery(query);
            if (!rs.next()) {
                
                try {
                    stmt = connection.createStatement();
                    
                    String update = "CREATE TABLE locations(id INTEGER PRIMARY KEY, url LONGVARCHAR NOT NULL, " +
                            "last_visit_time INTEGER NOT NULL, visit_count INTEGER NOT NULL, frecency_boost FLOAT NOT NULL, is_javadoc INTEGER NOT NULL, is_code_related INTEGER NOT NULL)";
                    
                    stmt.executeUpdate(update);
                    
                    update = "CREATE UNIQUE INDEX locations_by_url ON locations(url)";

                    stmt.executeUpdate(update);
                    
                    update = "CREATE INDEX locations_by_last_visit_time ON locations(last_visit_time)";
                    
                    stmt.executeUpdate(update);
                    
                    connection.commit();
                } catch (SQLException e1) {
                    try {
                        connection.rollback();
                    } catch (SQLException e2) { }
                    throw e1;
                }
            }
        } catch (SQLException e) {
            throw new IndexerException("Error checking for/creating locations table: " + e, e);
        } 
    }
}
