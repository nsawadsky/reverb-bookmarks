package ca.ubc.cs.reverb.eclipseplugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

/**
 * Class must be thread-safe.
 */
public class PluginConfig {
    private static String LOCAL_APPDATA_ENV_VAR = "LOCALAPPDATA";
    
    private String settingsPath;
    
    private String userId;
    
    public PluginConfig() throws PluginException {
        String localAppDataPath = System.getenv(LOCAL_APPDATA_ENV_VAR);
        if (localAppDataPath == null) {
            throw new PluginException("APPDATA environment variable not found");
        }
        String basePath = localAppDataPath + File.separator + "cs.ubc.ca" + File.separator + "Reverb";
        settingsPath = basePath + File.separator + "settings";
        try {
            initializeUserId();
        } catch (Exception e) {
            throw new PluginException("Error getting/creating user ID: " + e, e);
        }
    }
    
    public String getUserId() {
        return userId;
    }
    
    public String getStudyStatePath() {
        return settingsPath + File.separator + "studystate.txt";
    }

    private void initializeUserId() throws IOException, IllegalArgumentException, PluginException {
        String userIdPath = getUserIdPath();
        File userIdFile = new File(userIdPath);
        if (!userIdFile.exists()) {
            File settingsDir = new File(settingsPath);
            if (!settingsDir.exists()) {
                if (!settingsDir.mkdirs()) {
                    throw new PluginException("Could not create directory '" + settingsPath + "'");
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
                throw new PluginException("Empty uid.txt file");
            }
            UUID uuid = UUID.fromString(new String(Arrays.copyOfRange(buffer, 0, charsRead)));
            userId = uuid.toString();
        }
    }
    
    private String getUserIdPath() {
        return settingsPath + File.separator + "uid.txt";
    }
    
}
