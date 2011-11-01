package ca.ubc.cs.reverb.indexer;

import java.io.File;

public class IndexerConfig {
    private static String LOCAL_APPDATA_ENV_VAR = "LOCALAPPDATA";
    
    private String indexPath;
    
    private String locationsDatabasePath;
    
    public IndexerConfig() throws IndexerException {
        String localAppDataPath = System.getenv(LOCAL_APPDATA_ENV_VAR);
        if (localAppDataPath == null) {
            throw new IndexerException("APPDATA environment variable not found");
        }
        indexPath = localAppDataPath + File.separator + "Reverb" + File.separator + "data" + File.separator + "index";
        File dir = new File(indexPath);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IndexerException("Could not create directory '" + indexPath + "'");
            }
        }
        locationsDatabasePath = localAppDataPath + File.separator + "Reverb" + File.separator + "data" + File.separator + "locations.sqlite";
    }
    
    public String getIndexerPipeName() {
        return "historyminer-index";
    }
    
    public String getQueryPipeName() {
        return "historyminer-query";
    }
    
    public String getIndexPath() {
        return indexPath;
    }
    
    public String getLocationsDatabasePath() {
        return locationsDatabasePath;
    }
}
