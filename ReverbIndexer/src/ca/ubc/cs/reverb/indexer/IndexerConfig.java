package ca.ubc.cs.reverb.indexer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.UUID;

/**
 * Class must be thread-safe.
 */
public class IndexerConfig {
    private static String LOCAL_APPDATA_ENV_VAR = "LOCALAPPDATA";
    
    private String indexPath;
    
    private String locationsDatabasePath;
    
    private String settingsPath;
    
    private String userId;
    
    public IndexerConfig() throws IndexerException {
        String localAppDataPath = System.getenv(LOCAL_APPDATA_ENV_VAR);
        if (localAppDataPath == null) {
            throw new IndexerException("APPDATA environment variable not found");
        }
        String basePath = localAppDataPath + File.separator + "cs.ubc.ca" + File.separator + "Reverb";
        settingsPath = basePath + File.separator + "settings";
        String dataPath = basePath + File.separator + "data";
        indexPath = dataPath + File.separator + "index";
        File dir = new File(indexPath);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IndexerException("Could not create directory '" + indexPath + "'");
            }
        }
        locationsDatabasePath = dataPath + File.separator + "locations.sqlite";
        
        try {
            initializeUserId();
        } catch (Exception e) {
            throw new IndexerException("Error getting/creating user ID: " + e, e);
        }
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

    public String getUserId() {
        return userId;
    }
    
    private void initializeUserId() throws IOException, IllegalArgumentException, IndexerException {
        String userIdPath = getUserIdPath();
        File userIdFile = new File(userIdPath);
        if (!userIdFile.exists()) {
            File settingsDir = new File(settingsPath);
            if (!settingsDir.exists()) {
                settingsDir.mkdirs();
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
            FileReader reader = new FileReader(userIdFile);
            final int BUF_SIZE = 1024;
            char[] buffer = new char[BUF_SIZE];
            int charsRead = 0;
            try {
                charsRead = reader.read(buffer);
            } finally {
                reader.close();
            }
            if (charsRead <= 0) {
                throw new IndexerException("Empty uid.txt file");
            }
            UUID uuid = UUID.fromString(new String(Arrays.copyOfRange(buffer, 0, charsRead)));
            userId = uuid.toString();
        }
    }
    
    private String getUserIdPath() {
        return settingsPath + File.separator + "uid.txt";
    }
}
