package ca.ubc.cs.hminer.study.core;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.ini4j.Ini;
import org.ini4j.Profile;

public class ChromeHistoryExtractor extends HistoryExtractor {
    private static Logger log = Logger.getLogger(ChromeHistoryExtractor.class);   
    
    private static final String APPDATA_ENV_VAR = "APPDATA";
    private static final String HOME_ENV_VAR = "HOME";
    private static final String WINDOWS_FIREFOX_SETTINGS_PATH = "Mozilla" + File.separator + "Firefox";
    private static final String LINUX_FIREFOX_SETTINGS_PATH = ".mozilla" + File.separator + "firefox";
    private static final String PROFILES_INI = "profiles.ini";
    private static final String FIREFOX_PROFILE_SECTION_PREFIX = "Profile";
    private static final String PLACES_SQLITE = "places.sqlite";
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");
    private static final String JDBC_SQLITE = "jdbc:sqlite:";
    private static final String NULL = "(null)";
    
    public ChromeHistoryExtractor() throws HistoryMinerException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (Exception e) {
            throw new HistoryMinerException("Exception loading SQLite JDBC driver: " + e, e);
        }
    }
    
    public Date getEarliestVisitDate() throws HistoryMinerException {
        final String query = "SELECT min(visit_date) FROM moz_historyvisits";
        
        Date result = new Date();
        /*
        try {
            Connection conn = null;
            Statement stmt = null;
            try {
                conn = DriverManager.getConnection(JDBC_SQLITE + this.getFirefoxHistoryDbPath());
                stmt = conn.createStatement();
                
                ResultSet rs = stmt.executeQuery(query);
                
                while (rs.next()) {
                    result = prTimeToDate(rs.getLong(1));
                }
            } finally {
                if (conn != null) { conn.close(); }
                if (stmt != null) { stmt.close(); }
            }
        } catch (Exception e) {
            throw new HistoryMinerException(
                    "Error retrieving earliest visit date: " + e, e);
        } 
        */
        return result;
    }
    
    public List<HistoryVisit> extractHistory(Date startDate, Date endDate) throws HistoryMinerException {
        final String queryTemplate = 
            "SELECT visits.id, visits.visit_date, visits.visit_type, visits.session, places.id, places.url, places.title, " +
                "from_places.url " + 
            "FROM moz_historyvisits AS visits JOIN moz_places AS places ON visits.place_id = places.id " +
            "LEFT OUTER JOIN moz_historyvisits AS from_visits ON visits.from_visit = from_visits.id " + 
            "LEFT OUTER JOIN moz_places AS from_places ON from_visits.place_id = from_places.id " +
            "WHERE visits.visit_date > %d " + 
            "AND visits.visit_date < %d " +
            "ORDER BY visits.id";

        List<HistoryVisit> results = new ArrayList<HistoryVisit>();
        
        /*
        try {
            Connection conn = null;
            Statement stmt = null;
            try {
                conn = DriverManager.getConnection(JDBC_SQLITE + this.getFirefoxHistoryDbPath());
                stmt = conn.createStatement();
                
                String query = String.format(queryTemplate, dateToPRTime(startDate), dateToPRTime(endDate));
                ResultSet rs = stmt.executeQuery(query);
                
                while (rs.next()) {
                    long visitId = rs.getLong(1);
                    Date visitDate = prTimeToDate(rs.getLong(2));
                    int visitType = rs.getInt(3);
                    long sessionId = rs.getLong(4);
                    long locationId = rs.getLong(5);
                    String url = rs.getString(6);
                    String title = rs.getString(7);
                    String fromUrl = rs.getString(8);
                    
                    if (log.isTraceEnabled()) {
                        StringBuilder logMsg = new StringBuilder();
                        logMsg.append(DateFormat.getInstance().format(visitDate));
                        logMsg.append('|');
                        logMsg.append(Integer.toString(visitType));
                        logMsg.append('|');
                        logMsg.append(url == null ? NULL : url);
                        logMsg.append('|');
                        logMsg.append(title == null ? NULL : title);
                        logMsg.append('|');
                        logMsg.append(fromUrl == null ? NULL : fromUrl);
                        logMsg.append(LINE_SEPARATOR);
                        log.trace(logMsg.toString());
                    }
                    
                    results.add(new HistoryVisit(visitId, visitDate, visitType, sessionId, locationId, url, title, fromUrl));
                }
            } finally {
                if (conn != null) { conn.close(); }
                if (stmt != null) { stmt.close(); }
            }
        } catch (Exception e) {
            throw new HistoryMinerException(
                    "Error while processing SQLite output and generating file: " + e, e);
        } 
        */
        return results;
    }
    
}
