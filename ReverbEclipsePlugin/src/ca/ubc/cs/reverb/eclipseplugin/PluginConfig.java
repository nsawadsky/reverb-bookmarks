package ca.ubc.cs.reverb.eclipseplugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.util.DefaultPrettyPrinter;

/**
 * Class must be thread-safe.
 */
public class PluginConfig {
    private static String LOCAL_APPDATA_ENV_VAR = "LOCALAPPDATA";
    
    private String settingsPath;
    
    private String pluginStatePath;
    
    private String studyDataLogFolderPath;
    
    private String userId;
    
    private String userIdKey;
    
    private PluginSettings pluginSettings;
    
    public PluginConfig() throws PluginException {
        String localAppDataPath = System.getenv(LOCAL_APPDATA_ENV_VAR);
        if (localAppDataPath == null) {
            throw new PluginException("APPDATA environment variable not found");
        }
        String dataPath = localAppDataPath + File.separator + "cs.ubc.ca" + File.separator + "Reverb" + File.separator + "data";
        settingsPath = dataPath + File.separator + "settings";

        initializeUserId();
        
        pluginStatePath = dataPath + File.separator + "plugin";
        File pluginStateDir = new File(pluginStatePath);
        if (!pluginStateDir.exists()) {
            if (!pluginStateDir.mkdirs()) {
                throw new PluginException("Could not create directory '" + pluginStatePath + "'");
            }
        }

        loadPluginSettings();

        studyDataLogFolderPath = dataPath + File.separator + "logs";
        File logFolder = new File(studyDataLogFolderPath);
        if (!logFolder.exists()) {
            if (!logFolder.mkdirs()) {
                throw new PluginException("Could not create directory '" + studyDataLogFolderPath + "'");
            }
        }
        
    }
    
    public String getUserId() {
        return userId;
    }
    
    public String getUserIdKey() {
        return userIdKey;
    }
    
    public String getStudyStatePath() {
        return pluginStatePath + File.separator + "studystate.txt";
    }

    public String getStudyDataLogFolderPath() {
        return studyDataLogFolderPath;
    }
    
    public PluginSettings getPluginSettings() {
        return pluginSettings;
    }
    
    private void initializeUserId() throws PluginException {
        try {
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
            userIdKey = initializeUserIdKey(userId);
        } catch (PluginException e) {
            throw e;
        } catch (Exception e) {
            throw new PluginException("Error getting/creating user ID: " + e, e);
        }
    }
    
    private String initializeUserIdKey(String inputUserId) throws PluginException {
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
            throw new PluginException("Error creating user ID key: " + e, e);
        }
    }
    
    private String getUserIdPath() {
        return settingsPath + File.separator + "uid.txt";
    }
    
    private String getPluginSettingsPath() {
        return pluginStatePath + File.separator + "settings.txt";
    }
    
    private void loadPluginSettings() throws PluginException {
        try {
            File pluginSettingsFile = new File(getPluginSettingsPath());
            if (!pluginSettingsFile.exists()) {
                pluginSettings = new PluginSettings();
                return;
            }
            ObjectMapper mapper = new ObjectMapper();
            pluginSettings = mapper.readValue(pluginSettingsFile, PluginSettings.class);
        } catch (Exception e) {
            throw new PluginException("Error loading plugin settings: " + e, e);
        }
    }
    
    private void savePluginSettings() throws PluginException {
        JsonGenerator jsonGenerator = null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            jsonGenerator = mapper.getJsonFactory().createJsonGenerator(new File(getPluginSettingsPath()), 
                    JsonEncoding.UTF8);
            jsonGenerator.setPrettyPrinter(new DefaultPrettyPrinter());

            mapper.writeValue(jsonGenerator, pluginSettings);
        } catch (Exception e) {
            throw new PluginException("Error saving plugin settings: " + e, e);
        } finally {
            if (jsonGenerator != null) {
                try { 
                    jsonGenerator.close();
                } catch (IOException e) { } 
            }
        }
    }
    
}
