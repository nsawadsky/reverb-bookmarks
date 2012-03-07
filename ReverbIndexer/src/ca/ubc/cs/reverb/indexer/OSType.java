package ca.ubc.cs.reverb.indexer;

public enum OSType {
    WINDOWS_XP,
    // Vista or later
    WINDOWS_VISTA_OR_LATER,
    MAC,
    LINUX;
    
    public static OSType getOSType() {
        String os = System.getProperty("os.name");
        OSType osType = OSType.WINDOWS_VISTA_OR_LATER;
        if (os != null) {
            if (os.contains("Windows XP")) {
                osType = OSType.WINDOWS_XP;
            } else if (os.contains("Mac")) {
                osType = OSType.MAC;
            } else if (os.contains("Linux")) {
                osType = OSType.LINUX;
            }
        }
        return osType;
    }
    
}
