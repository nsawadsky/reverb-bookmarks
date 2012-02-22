package ca.ubc.cs.reverb.indexer.installer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.log4j.Logger;

import ca.ubc.cs.reverb.indexer.IndexerException;
import ca.ubc.cs.reverb.indexer.messages.UpdatePageInfoRequest;

public class HistoryIndexer {
    private static Logger log = Logger.getLogger(HistoryIndexer.class);
    
    private final static int POOL_SIZE = 20; 
    private final static String FIREFOX_USER_AGENT = 
        "Mozilla/5.0 (Windows NT 6.0; rv:5.0) Gecko/20100101 Firefox/5.0";
    private final static int PAGE_TIMEOUT_MSECS = 8000;
    
    // Access to each list must be synchronized on the list.
    private List<LocationAndVisits> locationsToIndex = new LinkedList<LocationAndVisits>();
    private List<LocationAndVisits> locationsIndexed = new ArrayList<LocationAndVisits>(); 
    
    private volatile boolean isShutdown = false;
    
    // TODO: Better handling for other locales
    public final static String GOOGLE_PREFIX = "http://www.google.";
    public final static String GOOGLE_HTTPS_PREFIX = "https://www.google.";
    public final static String GOOGLE_SEARCH_TITLE = "Google Search";
    public final static String GOOGLE_SEARCH_PATTERN = "^https?://www\\.google\\.\\S+/search";
    
    private List<HistoryVisit> visitList;
    private Pattern googleSearchPattern;
    private int locationsToIndexCount = 0;
    private HttpClient httpClient = null;
    
    public HistoryIndexer(List<HistoryVisit> visitList) {
        this.visitList = visitList;
        
        this.googleSearchPattern = Pattern.compile(GOOGLE_SEARCH_PATTERN);
    }
    
    public void startIndexing() throws IndexerException {
        // First filter out visits we are not interested in.
        List<HistoryVisit> redirectsFiltered = filterRedirects(visitList);

        // Get unique location ID's in filtered list.
        Set<Long> uniqueIds = new HashSet<Long>();
        for (HistoryVisit visit: redirectsFiltered) {
            uniqueIds.add(visit.locationId);
        }

        // Filter visits to Google search, since we will get tagged as a
        // bot if we start trying to classify those visits.
        List<HistoryVisit> googleVisitsFiltered = new ArrayList<HistoryVisit>();
        
        for (HistoryVisit visit: redirectsFiltered) {
            if (isGoogleSearch(visit.url, visit.title)) {
                visit.isGoogleSearch = true;
            } else { 
                googleVisitsFiltered.add(visit);
            }
        }
        
        Map<Long, LocationAndVisits> locationsById = new HashMap<Long, LocationAndVisits>();
        for (HistoryVisit visit: googleVisitsFiltered) {
            LocationAndVisits locationAndVisits = locationsById.get(visit.locationId);
            if (locationAndVisits == null) {
                locationAndVisits = new LocationAndVisits(new HistoryLocation(visit.locationId, visit.url, visit.title));
                locationAndVisits.visits.add(visit);
                locationsById.put(visit.locationId, locationAndVisits);
            } else {
                locationAndVisits.visits.add(visit);
            }
        }
        locationsToIndexCount = locationsById.size();
        
        synchronized (locationsToIndex) {
            for (LocationAndVisits locationAndVisits: locationsById.values()) {
                locationsToIndex.add(locationAndVisits);
            }
        }
        
        httpClient = createHttpClient();
        for (int i = 0; i < POOL_SIZE; i++) {
            final IndexerConnection indexerConnection = new IndexerConnection();
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    LocationAndVisits next = null;
                    do {
                        next = getNextLocation();
                        if (next != null) {
                            indexLocation(next, false, indexerConnection);
                            addIndexedLocation(next);
                        }
                    } while (!isShutdown && next != null);
                }
            });
            thread.setDaemon(true);
            thread.start();
        }
    }

    public int getLocationsToIndexCount() {
        return locationsToIndexCount;
    }
    
    public int getLocationsIndexedCount() {
        synchronized (locationsIndexed) {
            return locationsIndexed.size();
        }
    }
    
    /**
     * Returns all already-classified locations.
     */
    public List<LocationAndVisits> getResults() {
        List<LocationAndVisits> locationAndVisitsList = new ArrayList<LocationAndVisits>();
        synchronized (locationsIndexed) {
            for (LocationAndVisits locationAndVisits: locationsIndexed) {
                locationAndVisitsList.add(locationAndVisits);
            }
        }
        return locationAndVisitsList;
    }
    
    public void shutdown() {
        isShutdown = true;
        if (httpClient != null) {
            httpClient.getConnectionManager().shutdown();
        }
    }
    
    protected boolean isGoogleSearch(String url, String title) {
        return ((url.startsWith(GOOGLE_PREFIX) || url.startsWith(GOOGLE_HTTPS_PREFIX)) 
                && title != null && title.contains(GOOGLE_SEARCH_TITLE)) ||
                googleSearchPattern.matcher(url).find();
    }
    
    protected void indexLocation(LocationAndVisits locationAndVisits, boolean dumpFile, IndexerConnection indexerConnection) {
        HistoryLocation location = locationAndVisits.location;
        try {
            long startTimeMsecs = System.currentTimeMillis();
            
            InputStream inputStream = null;
            ByteArrayOutputStream page = null;
            try {
                if (location.url.startsWith("file:")) {
                    inputStream = new FileInputStream(new File(new URI(location.url)));
                } else {
                    HttpGet httpGet = new HttpGet(location.url);
                    httpGet.setHeader("User-Agent", FIREFOX_USER_AGENT);
                    
                    HttpResponse response = httpClient.execute(httpGet);
                    HttpEntity responseEntity = response.getEntity();
                    inputStream = responseEntity.getContent();
                    Header contentType = responseEntity.getContentType();
                    if (contentType == null || contentType.getValue() == null) {
                        throw new IndexerException("Missing content type header");
                    }
                    String contentTypeValue = contentType.getValue();
                    if (!(contentTypeValue.startsWith("text/") || contentTypeValue.startsWith("application/xml") || 
                            contentTypeValue.startsWith("application/xhtml+xml"))) {
                        throw new IndexerException("Unsupported content type '" + contentTypeValue + "'");
                    }
                }
                
                page = new ByteArrayOutputStream();
                
                final int BUF_SIZE = 10 * 1024;
                final int MAX_PAGE_SIZE = 2 * 1024 * 1024;
                byte buffer[] = new byte[BUF_SIZE];
                
                int bytesRead = 0;
                while (bytesRead > -1) {
                    bytesRead = inputStream.read(buffer);
                    if (bytesRead > -1) {
                        page.write(buffer, 0, bytesRead);
                        if (page.size() > MAX_PAGE_SIZE) {
                            throw new IndexerException("Page exceeded max size (" + 
                                    MAX_PAGE_SIZE + " bytes): " + location.url);
                        }
                    }
                }
                
            } finally {
                if (inputStream != null) {
                    try { 
                        inputStream.close();
                    } catch (IOException e) { }
                }
            }
            
            ByteArrayInputStream byteStream = new ByteArrayInputStream(page.toByteArray());
            
            Document doc = Jsoup.parse(byteStream, null, location.url);
            
            long endTimeMsecs = System.currentTimeMillis();
            long pageGetTime = endTimeMsecs - startTimeMsecs;
            log.debug("Page get/parse time = " + pageGetTime + " msecs");
            
            try {
                if (indexerConnection != null) {
                    UpdatePageInfoRequest info = new UpdatePageInfoRequest(location.url, doc.outerHtml());
                    for (HistoryVisit visit: locationAndVisits.visits){
                        info.visitTimes.add(visit.visitDate.getTime());
                    }
                    indexerConnection.indexPage(info);
                }
            } catch (IndexerException e) {
                log.error("Error submitting page for indexing: " + location.url, e);
            }
            
        } catch (Exception e) {
            log.info("Exception processing URL '" + location.url + "': " + e);
        } 
    }
    
    private String normalizeUrl(String inputUrl) {
        int fragmentIndex = inputUrl.lastIndexOf('#');
        if (fragmentIndex != -1) {
            String result = inputUrl.substring(0, fragmentIndex);
            return result;
        }
        return inputUrl;
    }
    
    private List<HistoryVisit> filterRedirects(List<HistoryVisit> visitList) {
        List<HistoryVisit> result = new ArrayList<HistoryVisit>();
        for (HistoryVisit visit: visitList) {
            if (!visit.isRedirect()) {
                result.add(visit);
            }
        }
        return result;
    }
    
    private LocationAndVisits getNextLocation() {
        synchronized (locationsToIndex) {
            if (locationsToIndex.isEmpty()) {
                return null;
            }
            return locationsToIndex.remove(0);
        }
    }
    
    private void addIndexedLocation(LocationAndVisits locationAndVisits) {
        synchronized (locationsIndexed) {
            locationsIndexed.add(locationAndVisits);
        }
    }
    
    private HttpClient createHttpClient() {
        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, PAGE_TIMEOUT_MSECS);
        HttpConnectionParams.setSoTimeout(params, PAGE_TIMEOUT_MSECS);
        
        params.setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.IGNORE_COOKIES);
        
        ThreadSafeClientConnManager connectionManager = new ThreadSafeClientConnManager();
        connectionManager.setMaxTotal(POOL_SIZE + 5);
        connectionManager.setDefaultMaxPerRoute(5);
        return new DefaultHttpClient(connectionManager, params);
    }
    
}
