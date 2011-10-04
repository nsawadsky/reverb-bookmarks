package ca.ubc.cs.periscope.indexer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class LocationsDatabase {
    private static final String JDBC_SQLITE = "jdbc:sqlite:";

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
    
    private void createLocationsTableIfNecessary() throws IndexerException {
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
                            "last_visit_time INTEGER NOT NULL, frecency_boost FLOAT NOT NULL)";
                    
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
            throw new IndexerException("Error checking whether locations table exists: " + e);
        } finally {
            if (conn != null) { 
                try { 
                    conn.close(); 
                } catch (SQLException e) {} 
            }
        }
    }
}
