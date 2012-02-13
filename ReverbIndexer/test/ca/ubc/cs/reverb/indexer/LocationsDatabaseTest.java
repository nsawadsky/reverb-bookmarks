package ca.ubc.cs.reverb.indexer;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Arrays;
import java.util.Map;

import org.junit.Test;

import ca.ubc.cs.reverb.indexer.LocationsDatabase.UpdateLocationResult;

public class LocationsDatabaseTest {
    private final static long MSECS_PER_MONTH = 30 * 24 * 60 * 60 * 1000L;
    
    @Test
    public void testDeleteUpdateGetLocationInfo() throws Exception {
        final File databasePath = File.createTempFile("reverbtest", "db");
        databasePath.deleteOnExit();
                
        IndexerConfig config = new IndexerConfig() {
            @Override
            public String getLocationsDatabasePath() {
                return databasePath.getAbsolutePath();
            }
        };
        
        LocationsDatabase db = new LocationsDatabase(config);
        
        final String testUrl = "http://mytesturl.com/testurl";
        
        db.deleteLocationInfo(testUrl);
        assertNull(db.getLocationInfo(testUrl));
        assertNull(db.getLastVisitTime(testUrl));
        
        UpdateLocationResult result = db.updateLocationInfo(testUrl, Arrays.asList(new Long[] {MSECS_PER_MONTH * 2, MSECS_PER_MONTH}), 
                false, true, true, MSECS_PER_MONTH * 3);
        assertNull(result);

        assertEquals(0, db.getMaxLocationId());
        
        result = db.updateLocationInfo(testUrl, Arrays.asList(new Long[] {MSECS_PER_MONTH * 2, MSECS_PER_MONTH}), 
                true, true, true, MSECS_PER_MONTH * 3);
        assertTrue(result.rowCreated);
        LocationInfo updated = result.locationInfo;

        assertEquals(1, updated.id);
        assertEquals(updated.id, db.getMaxLocationId());
        
        assertTrue(updated.isCodeRelated);
        assertTrue(updated.isJavadoc);
        
        assertEquals(testUrl, updated.url);
        assertEquals(2, updated.visitCount);
        assertEquals(MSECS_PER_MONTH * 2, updated.lastVisitTime);
        float expectedFrecencyBoost = (float)Math.exp(LocationInfo.FRECENCY_DECAY * MSECS_PER_MONTH) + 1.0F;
        assertEquals(expectedFrecencyBoost, updated.storedFrecencyBoost, .0001);
        
        db.commitChanges();
        
        assertEquals(MSECS_PER_MONTH * 2, (long)db.getLastVisitTime(testUrl));
        
        LocationInfo dbInfo = db.getLocationInfo(testUrl);
        assertEquals(updated, dbInfo);
        
        // Test behavior when htmlProvided is false and location already exists in database. 
        result = db.updateLocationInfo(testUrl, null, false, false, false, MSECS_PER_MONTH * 3);
        assertFalse(result.rowCreated);
        updated = result.locationInfo;
        db.commitChanges();
        
        dbInfo = db.getLocationInfo(testUrl);
        assertEquals(updated, dbInfo);
        
        assertEquals(3, dbInfo.visitCount);

        // Values for these two fields are unchanged (same as what was already in the database).
        assertTrue(dbInfo.isCodeRelated);
        assertTrue(dbInfo.isJavadoc);
        
        // In this case, the value passed for the currentTime parameter is used as the last visit time.
        assertEquals(MSECS_PER_MONTH * 3, dbInfo.lastVisitTime);
        
        expectedFrecencyBoost = (float)Math.exp(LocationInfo.FRECENCY_DECAY * 2 * MSECS_PER_MONTH) + 
                (float)Math.exp(LocationInfo.FRECENCY_DECAY * MSECS_PER_MONTH) + 1.0F;
        assertEquals(expectedFrecencyBoost, dbInfo.storedFrecencyBoost, .0001);
        
        final String testUrl2 = "http://mytesturl2.com/testurl2";
        result = db.updateLocationInfo(testUrl2, null, true, false, false, MSECS_PER_MONTH * 3);
        
        LocationInfo dbInfo2 = result.locationInfo;
        
        assertEquals(2, dbInfo2.id);
        assertEquals(dbInfo2.id, db.getMaxLocationId());

        db.commitChanges();
        
        Map<String, LocationInfo> results = db.getLocationInfos(Arrays.asList(new String[] {testUrl, testUrl2, "missingurl"}));

        assertEquals(dbInfo, results.get(testUrl));
        assertEquals(dbInfo2, results.get(testUrl2));
        assertEquals(2, results.size());
        
        LocationInfo deletedInfo = db.deleteLocationInfo(testUrl);
        assertEquals(dbInfo, deletedInfo);
        assertNull(db.getLocationInfo(testUrl));
        assertNull(db.deleteLocationInfo(testUrl));
    }

}
