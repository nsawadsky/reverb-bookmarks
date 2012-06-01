package ca.ubc.cs.reverb.indexer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.Scanner;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;

/**
 * Class must be thread-safe.
 */
public class IndexerConfig {
    private static String USER_PROFILE_ENV_VAR = "USERPROFILE";
    
    private String indexPath;
    
    private String locationsDatabasePath;
    
    private String settingsPath;
    
    private String studyDataLogFolderPath;
    
    private String debugLogFolderPath;
    
    private String userId;
    
    private String userIdKey;
    
    private String dataPath;
    
    private BlockedTypes blockedTypes;
    
    public IndexerConfig() throws IndexerException {
        dataPath = getBasePath() + File.separator + "data";
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
        
        blockedTypes = BlockedTypes.load(getBlockedTypesPath());
    }
    
    public String getDataPath() {
        return dataPath;
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
    
    public String getIndexerStudyStatePath() {
        return settingsPath + File.separator + "indexer-study-state.txt";
    }
    
    public String getCurrentIndexerInstallPath() throws IndexerException {
        File indexerVersionFile = new File(getIndexerInstallPointerPath());
        Scanner scanner = null;
        try {
            scanner = new Scanner(indexerVersionFile);
            return scanner.nextLine().trim();
        } catch (Exception e) { 
            throw new IndexerException("Failed to get current indexer install path: " + e, e);
        } finally {
            if (scanner != null) { 
                scanner.close();
            }
        }
    }
    
    public String getIndexerInstallPointerPath() {
        return settingsPath + File.separator + "indexer-install-path.txt";
    }
    
    public BlockedTypes getBlockedTypes() {
        return blockedTypes;
    }
    
    protected String getBasePath() throws IndexerException {
        return getLocalAppDataPath() + File.separator + "cs.ubc.ca" + File.separator + "Reverb";
    }
    
    private String getLocalAppDataPath() throws IndexerException {
        String userProfilePath = System.getenv(USER_PROFILE_ENV_VAR);
        if (userProfilePath == null) {
            throw new IndexerException("USERPROFILE environment variable not found");
        }
        if (OSType.getOSType() == OSType.WINDOWS_XP) {
            return userProfilePath + "\\Local Settings\\Application Data"; 
        } else {
            return userProfilePath + "\\AppData\\Local";
        }
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
                OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(userIdFile), "UTF-8");
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

    private String getBlockedTypesPath() {
        return settingsPath + File.separator + "blocked-types.txt";
    }
    
}
