package ca.ubc.cs.reverb.indexer.installer;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import ca.ubc.cs.reverb.indexer.IndexerException;

public class ChromeHistoryExtractor extends HistoryExtractor {
    private static Logger log = Logger.getLogger(ChromeHistoryExtractor.class);   
    
    private static final String HOME_ENV_VAR = "HOME";
    private static final String USER_PROFILE_ENV_VAR = "USERPROFILE";
    private static final String WINDOWS_CHROME_SETTINGS_PATH = "Google\\Chrome\\User Data\\Default";
    private static final String WINDOWS_CHROMIUM_SETTINGS_PATH = "Chromium\\User Data\\Default";
    private static final String LINUX_CHROME_SETTINGS_PATH = ".config/google-chrome/Default";
    private static final String LINUX_CHROMIUM_SETTINGS_PATH = ".config/chromium/Default";
    private static final String MAC_CHROME_SETTINGS_PATH = "Library/Application Support/Google/Chrome/Default";
    private static final String MAC_CHROMIUM_SETTINGS_PATH = "Library/Application Support/Chromium/Default";
    private static final String HISTORY_DB = "History";
    private static final String ARCHIVED_HISTORY_DB = "Archived History";
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");
    private static final String JDBC_SQLITE = "jdbc:sqlite:";
    private static final String NULL = "(null)";
    
    // Microseconds between midnight, Jan 1, 1601 and midnight, Jan 1, 1970.
    // Used to convert between Chrome timestamp and Java time.
    private static final long MICROSECOND_OFFSET = 11644473600000000L;
    
    private boolean isChromium = false;
    
    public ChromeHistoryExtractor(boolean isChromium) throws IndexerException {
        this.isChromium = isChromium;
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (Exception e) {
            throw new IndexerException("Exception loading SQLite JDBC driver: " + e, e);
        }
    }
    
    public Date getEarliestVisitDate() throws IndexerException {
        try {
            return getDbEarliestVisitDate(true);
        } catch (IndexerException e) {
            return getDbEarliestVisitDate(false);
        }
    }
    
    public List<HistoryVisit> extractHistory(Date startDate, Date endDate) throws IndexerException {
        List<HistoryVisit> results = null;
        try {
            results = extractDbHistory(startDate, endDate, true);
        } catch (IndexerException e) { }
        
        List<HistoryVisit> currResults = extractDbHistory(startDate, endDate, false);
        if (results != null) {
            results.addAll(currResults);
        } else {
            results = currResults;
        }
        ensureIdsUnique(results);
        return results;
    }
    
    @Override
    public boolean historyDbExists() {
        boolean result = false;
        try {
            result = new File(getChromeHistoryDbPath()).exists();
        } catch (Exception e) { }
        return result;
    }
    
    private void ensureIdsUnique(List<HistoryVisit> visits) {
        long currVisitId = 1;
        long currLocationId = 1;
        Map<String, Long> locationIdsByUrl = new HashMap<String, Long>();
        for (HistoryVisit visit: visits) {
            Long locationId = locationIdsByUrl.get(visit.url);
            if (locationId == null) {
                locationId = currLocationId++;
                locationIdsByUrl.put(visit.url, locationId);
            }
            visit.locationId = locationId;
            visit.visitId = currVisitId++;
        }
    }
    
    /*
SELECT visits.id, visits.visit_time, visits.transition, visits.segment_id, urls.id, urls.url, urls.title, from_urls.url 
FROM visits JOIN urls ON visits.url = urls.id 
LEFT OUTER JOIN visits AS from_visits ON visits.from_visit = from_visits.id 
LEFT OUTER JOIN urls AS from_urls ON from_visits.url = from_urls.id 
ORDER BY visits.id DESC;
     */
    private List<HistoryVisit> extractDbHistory(Date startDate, Date endDate, boolean archived) throws IndexerException {
        final String queryTemplate = 
            "SELECT visits.id, visits.visit_time, visits.transition, urls.id, urls.url, urls.title, " +
                "from_visits.id, from_urls.url " + 
            "FROM visits JOIN urls ON visits.url = urls.id " +
            "LEFT OUTER JOIN visits AS from_visits ON visits.from_visit = from_visits.id " + 
            "LEFT OUTER JOIN urls AS from_urls ON from_visits.url = from_urls.id " +
            "WHERE visits.visit_time > %d " + 
            "AND visits.visit_time < %d " +
            "ORDER BY visits.id";

        List<HistoryVisit> results = new ArrayList<HistoryVisit>();
        
        try {
            Connection conn = null;
            Statement stmt = null;
            try {
                conn = DriverManager.getConnection(JDBC_SQLITE + this.getChromeHistoryDbPath(archived));
                stmt = conn.createStatement();
                
                String query = String.format(queryTemplate, dateToChromeTimestamp(startDate), dateToChromeTimestamp(endDate));
                ResultSet rs = stmt.executeQuery(query);
                
                while (rs.next()) {
                    long visitId = rs.getLong(1);
                    Date visitDate = chromeTimestampToDate(rs.getLong(2));
                    int visitType = rs.getInt(3);
                    long locationId = rs.getLong(4);
                    String url = rs.getString(5);
                    String title = rs.getString(6);
                    long fromVisitId = rs.getLong(7);
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
                    
                    // Because we are combining results from "Archived History" and "History" databases,
                    // we will later assign new values for visitId and locationId.  However, we have
                    // not written the code to correct the fromVisitId, so here we set it to 0.
                    // Also, it appears that the from_visit column in the "Archived History" database
                    // may always be set to 0.
                    results.add(new HistoryVisit(WebBrowserType.GOOGLE_CHROME, visitId, visitDate, visitType, 0, locationId, url, title, 0, fromUrl));
                }
            } finally {
                if (stmt != null) { stmt.close(); }
                if (conn != null) { conn.close(); }
            }
        } catch (Exception e) {
            throw new IndexerException(
                    "Error while processing SQLite output and generating file: " + e, e);
        } 
        return results;
    }
    
    private Date getDbEarliestVisitDate(boolean archived) throws IndexerException {
        final String query = "SELECT min(visit_time) FROM visits";
        
        Date result = new Date();
        try {
            Connection conn = null;
            Statement stmt = null;
            try {
                conn = DriverManager.getConnection(JDBC_SQLITE + this.getChromeHistoryDbPath(archived));
                stmt = conn.createStatement();
                
                ResultSet rs = stmt.executeQuery(query);
                
                while (rs.next()) {
                    result = chromeTimestampToDate(rs.getLong(1));
                }
            } finally {
                if (stmt != null) { stmt.close(); }
                if (conn != null) { conn.close(); }
            }
        } catch (Exception e) {
            throw new IndexerException(
                    "Error retrieving earliest visit date: " + e, e);
        } 
        return result;
    }
    
    private String getChromeHistoryDbPath() throws IndexerException {
        return getChromeHistoryDbPath(false);
    }
    
    private String getChromeHistoryDbPath(boolean archived) throws IndexerException {
        String settingsPath = null;
        OSType osType = getOSType();
        switch (osType) {
        case LINUX: {
            String homePath = System.getenv(HOME_ENV_VAR);
            if (homePath == null) {
                throw new IndexerException("HOME environment variable not found");
            }
            if (isChromium) {
                settingsPath = homePath + File.separator + LINUX_CHROMIUM_SETTINGS_PATH;
            } else {
                settingsPath = homePath + File.separator + LINUX_CHROME_SETTINGS_PATH;
            }
            break;
        }
        case MAC: {
            String homePath = System.getenv(HOME_ENV_VAR);
            if (homePath == null) {
                throw new IndexerException("HOME environment variable not found");
            }
            if (isChromium) {
                settingsPath = homePath + File.separator + MAC_CHROMIUM_SETTINGS_PATH;
            } else {
                settingsPath = homePath + File.separator + MAC_CHROME_SETTINGS_PATH;
            }
            break;
        } 
        // WINDOWS_XP or WINDOWS_VISTA_OR_LATER
        default: {
            String userProfilePath = System.getenv(USER_PROFILE_ENV_VAR);
            if (userProfilePath == null) {
                throw new IndexerException("USERPROFILE environment variable not found");
            }
            String localAppData = null;
            if (osType == OSType.WINDOWS_XP) {
                localAppData = userProfilePath + "\\Local Settings\\Application Data"; 
            } else {
                localAppData = userProfilePath + "\\AppData\\Local";
            }
            if (isChromium) {
                settingsPath = localAppData + File.separator + WINDOWS_CHROMIUM_SETTINGS_PATH;
            } else {
                settingsPath = localAppData + File.separator + WINDOWS_CHROME_SETTINGS_PATH;
            }
            break;
        } 
        }
        if (archived) {
            return settingsPath + File.separator + ARCHIVED_HISTORY_DB;
        }
        return settingsPath + File.separator + HISTORY_DB;
    }
    
    private long dateToChromeTimestamp(Date date) {
        return date.getTime() * 1000 + MICROSECOND_OFFSET;
    }
    
    private Date chromeTimestampToDate(long timestamp) {
        return new Date((timestamp - MICROSECOND_OFFSET)/1000);
    }
    
}
