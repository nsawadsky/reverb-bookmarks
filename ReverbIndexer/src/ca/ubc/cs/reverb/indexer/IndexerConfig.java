package ca.ubc.cs.reverb.indexer;

import java.io.File;
import java.io.FileWriter;
import java.util.Scanner;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;

/**
 * Class must be thread-safe.
 */
public class IndexerConfig {
    private static String LOCAL_APPDATA_ENV_VAR = "LOCALAPPDATA";
    
    private String indexPath;
    
    private String locationsDatabasePath;
    
    private String settingsPath;
    
    private String studyDataLogFolderPath;
    
    private String debugLogFolderPath;
    
    private String userId;
    
    private String userIdKey;
    
    public IndexerConfig() throws IndexerException {
        String dataPath = getBasePath() + File.separator + "data";
        settingsPath = dataPath + File.separator + "settings";
        String dbPath = dataPath + File.separator + "db";
        indexPath = dbPath + File.separator + "index";
        File dir = new File(indexPath);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IndexerException("Could not create directory '" + indexPath + "'");
            }
        }
        locationsDatabasePath = dbPath + File.separator + "locations.sqlite";
        
        studyDataLogFolderPath = dataPath + File.separator + "studylogs";
        File studyDataLogFolder = new File(studyDataLogFolderPath);
        if (!studyDataLogFolder.exists()) {
            if (!studyDataLogFolder.mkdirs()) {
                throw new IndexerException("Could not create directory '" + studyDataLogFolderPath + "'");
            }
        }
        
        debugLogFolderPath = dataPath + File.separator + "debuglogs";
        File debugLogFolder = new File(debugLogFolderPath);
        if (!debugLogFolder.exists()) {
            if (!debugLogFolder.mkdirs()) {
                throw new IndexerException("Could not create directory '" + debugLogFolderPath + "'");
            }
        }
        
        initializeUserId();
    }
    
    public String getIndexerPipeName() {
        return "reverb-index";
    }
    
    public String getQueryPipeName() {
        return "reverb-query";
    }
    
    public String getIndexPath() {
        return indexPath;
    }
    
    public String getLocationsDatabasePath() {
        return locationsDatabasePath;
    }

    public String getStudyDataLogFolderPath() {
        return studyDataLogFolderPath;
    }
    
    public String getDebugLogFilePath() {
        return debugLogFolderPath + File.separator + "indexer-debug";
    }
    
    public String getUserId() {
        return userId;
    }
    
    public String getUserIdKey() {
        return userIdKey;
    }
    
    protected String getBasePath() throws IndexerException {
        String localAppDataPath = System.getenv(LOCAL_APPDATA_ENV_VAR);
        if (localAppDataPath == null) {
            throw new IndexerException("APPDATA environment variable not found");
        }
        return localAppDataPath + File.separator + "cs.ubc.ca" + File.separator + "Reverb";
    }
    
    private void initializeUserId() throws IndexerException {
        try {
            String userIdPath = getUserIdPath();
            File userIdFile = new File(userIdPath);
            if (!userIdFile.exists()) {
                File settingsDir = new File(settingsPath);
                if (!settingsDir.exists()) {
                    if (!settingsDir.mkdirs()) {
                        throw new IndexerException("Could not create directory '" + settingsPath + "'");
                    }
                }
                UUID uuid = UUID.randomUUID();
                FileWriter writer = new FileWriter(userIdFile);
                try { 
                    writer.write(uuid.toString());
                } finally { 
                    writer.close();
                }
                userId = uuid.toString();
            } else {
                Scanner scanner = new Scanner(userIdFile);
                String uuidString = scanner.nextLine();
                scanner.close();
                UUID uuid = UUID.fromString(uuidString);
                userId = uuid.toString();
            }
            userIdKey = initializeUserIdKey(userId);
        } catch (IndexerException e) {
            throw e;
        } catch (Exception e) {
            throw new IndexerException("Error getting/creating user ID: " + e, e);
        }
    }
    
    private String initializeUserIdKey(String inputUserId) throws IndexerException {
        try {
            String shortName = "IndexerConfig";
            SecretKeySpec keySpec = new SecretKeySpec(
                    shortName.getBytes("UTF-8"), "HmacSHA256");
    
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(keySpec);
            byte[] bytes = mac.doFinal(inputUserId.getBytes("UTF-8"));
    
            String base64 = Base64.encodeBase64String(bytes);
            if (base64.indexOf('\r') != -1) {
                // Strip the carriage-return and line-feed characters.
                base64 = base64.substring(0, base64.indexOf('\r'));
            }
            return base64;
        } catch (Exception e) {
            throw new IndexerException("Error creating user ID key: " + e, e);
        }
    }
    
    private String getUserIdPath() {
        return settingsPath + File.separator + "uid.txt";
    }
}
