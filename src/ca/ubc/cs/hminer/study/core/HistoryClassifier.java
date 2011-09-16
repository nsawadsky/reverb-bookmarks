package ca.ubc.cs.hminer.study.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

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
import org.apache.http.util.EntityUtils;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.RootLogger;

import ca.ubc.cs.hminer.indexer.messages.PageInfo;

public class HistoryClassifier {
    private static Logger log = Logger.getLogger(HistoryClassifier.class);
    
    private final static int POOL_SIZE = 20; 
    private final static String FIREFOX_USER_AGENT = 
        "Mozilla/5.0 (Windows NT 6.0; rv:5.0) Gecko/20100101 Firefox/5.0";
    private final static int CODE_RELATED_MATCH_THRESHOLD = 2;
    private final static int PAGE_TIMEOUT_MSECS = 8000;
    
    // Access to each list must be synchronized on the list.
    private List<LocationAndVisits> locationsToClassify = new LinkedList<LocationAndVisits>();
    private List<LocationAndVisits> locationsClassified = new ArrayList<LocationAndVisits>(); 
    
    private volatile boolean isShutdown = false;
    
    // TODO: Better handling for other locales
    public final static String GOOGLE_PREFIX = "http://www.google.";
    public final static String GOOGLE_SEARCH_TITLE = "Google Search";
    public final static String GOOGLE_SEARCH_PATTERN = "http://www\\.google\\.\\S+/search";
    
    /**
     * Starts with lower-case and contains at least one upper-case 
     *   OR
     * Starts with upper-case and contains at least one lower-case, followed by at least one upper-case
     *   OR
     * Starts with two or more upper-case and contains at least one lower-case
     *   OR
     * Starts with letter, contains letters, decimal digits, and at least one underscore.
     */
    private final static String IDENTIFIER_PATTERN = 
        "(?x) \\b (?: [a-z] \\w*? [A-Z] \\w* | [A-Z] \\w*? [a-z] \\w*? [A-Z] \\w* | [A-Z]{2,} \\w*? [a-z] \\w* |" + 
                "[a-zA-Z] \\w*? _ [a-zA-Z0-9] \\w* )";
    
    /**
     * Starts with an identifier, followed by open-bracket, followed by
     * at most 40 non-close-bracket characters, followed by an identifier. 
     */
    private final static String METHOD_PATTERN = 
        "(?x)" + IDENTIFIER_PATTERN + "\\s* \\( [^\\)]{0,40}?" + IDENTIFIER_PATTERN;
    
    /**
     * Starts with a word that begins with a lower- or upper-case letter, followed by
     * empty brackets.
     */
    private final static String NO_ARGS_METHOD_PATTERN = 
            "(?x) \\b [a-zA-Z]\\w* \\s* \\( \\s* \\)";

    /**
     * Starts with a word that begins with a lower- or upper-case letter, followed
     * by a dot or "->", followed by another word starting with a lower- or upper-case letter,
     * followed by open-bracket, followed by up to 200 characters, followed by 
     * close-bracket.
     */
    private final static String METHOD_INVOCATION_PATTERN = 
        "(?x) \\b [a-zA-Z]\\w* \\s* (?: \\. | ->) \\s* [a-zA-Z]\\w* \\s* \\( .{0,200}? \\)";
    
    private List<HistoryVisit> visitList;
    private Pattern methodPattern;
    private Pattern methodInvocationPattern;
    private Pattern noArgsMethodPattern;
    private Pattern googleSearchPattern;
    private int initialVisitCount = 0;
    private int redirectVisitCount = 0;
    private double overallRevisitRate = 0.0;
    private int googleVisitCount = 0;
    private int locationsToClassifyCount = 0;
    private WebBrowserType webBrowserType;
    private HttpClient httpClient = null;
    private IndexerConnection indexerConnection = null;
    
    public HistoryClassifier(List<HistoryVisit> visitList, WebBrowserType webBrowserType, boolean indexMode) 
            throws HistoryMinerException {
        init(visitList, webBrowserType);
        
        if (indexMode) {
            indexerConnection = new IndexerConnection();
        }
    }
    
    public HistoryClassifier(List<HistoryVisit> visitList, WebBrowserType webBrowserType) {
        init(visitList, webBrowserType);
    }
    
    private void init(List<HistoryVisit> visitList, WebBrowserType webBrowserType) {
        this.webBrowserType = webBrowserType;
        
        this.visitList = visitList;
        this.methodPattern = Pattern.compile(METHOD_PATTERN, Pattern.DOTALL);
        this.noArgsMethodPattern = Pattern.compile(NO_ARGS_METHOD_PATTERN, Pattern.DOTALL);
        this.methodInvocationPattern = Pattern.compile(METHOD_INVOCATION_PATTERN, Pattern.DOTALL);
        
        this.googleSearchPattern = Pattern.compile(GOOGLE_SEARCH_PATTERN);
    }

    public static void main(String[] args) {
        BasicConfigurator.configure();
        RootLogger.getRootLogger().setLevel(Level.INFO);
        log.setLevel(Level.DEBUG);
        
        HistoryClassifier classifier = new HistoryClassifier(null, WebBrowserType.MOZILLA_FIREFOX);
        classifier.testClassifier(args[0]);
    }
    
    public void testClassifier(String url) {
        Location location = new Location(0, url, "");
        httpClient = createHttpClient();
        classifyLocation(location, true);

        switch (location.locationType) {
        case CODE_RELATED: {
            log.info("Classified as code-related");
            break;
        }
        case NON_CODE_RELATED: {
            log.info("Classified as non-code-related");
            break;
        }
        default : {
            break;
        }
        }

        httpClient.getConnectionManager().shutdown();
    }
    
    public void startClassifying() {
        this.initialVisitCount = visitList.size();
        
        // First filter out visits we are not interested in.
        List<HistoryVisit> redirectsFiltered = filterRedirects(visitList);

        this.redirectVisitCount = initialVisitCount - redirectsFiltered.size();
        
        // Get unique location ID's in filtered list.
        Set<Long> uniqueIds = new HashSet<Long>();
        for (HistoryVisit visit: redirectsFiltered) {
            uniqueIds.add(visit.locationId);
        }

        // Calculate overall revisit rate.
        if ( redirectsFiltered.size() > 0) {
            this.overallRevisitRate = (double)(redirectsFiltered.size() - uniqueIds.size())/redirectsFiltered.size();
        }
        log.info("Overall revisit rate = " + overallRevisitRate);
        
        // Filter visits to Google search, since we will get tagged as a
        // bot if we start trying to classify those visits.
        List<HistoryVisit> googleVisitsFiltered = new ArrayList<HistoryVisit>();
        
        for (HistoryVisit visit: redirectsFiltered) {
            if ((visit.url.startsWith(GOOGLE_PREFIX) && visit.title != null && visit.title.contains(GOOGLE_SEARCH_TITLE)) ||
                    googleSearchPattern.matcher(visit.url).find()) {
                visit.isGoogleSearch = true;
            } else { 
                googleVisitsFiltered.add(visit);
            }
        }
        
        this.googleVisitCount = redirectsFiltered.size() - googleVisitsFiltered.size();
        log.info("Total Google visits = " + googleVisitCount);
        
        Map<Long, LocationAndVisits> locationsById = new HashMap<Long, LocationAndVisits>();
        for (HistoryVisit visit: googleVisitsFiltered) {
            LocationAndVisits locationAndVisits = locationsById.get(visit.locationId);
            if (locationAndVisits == null) {
                locationAndVisits = new LocationAndVisits(new Location(visit.locationId, visit.url, visit.title));
                locationAndVisits.visits.add(visit);
                locationsById.put(visit.locationId, locationAndVisits);
            } else {
                locationAndVisits.visits.add(visit);
            }
        }
        locationsToClassifyCount = locationsById.size();
        
        synchronized (locationsToClassify) {
            for (LocationAndVisits locationAndVisits: locationsById.values()) {
                locationsToClassify.add(locationAndVisits);
            }
        }
        
        httpClient = createHttpClient();
        for (int i = 0; i < POOL_SIZE; i++) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    LocationAndVisits next = null;
                    do {
                        next = getNextLocation();
                        if (next != null) {
                            classifyLocation(next.location, false);
                            addClassifiedLocation(next);
                        }
                    } while (!isShutdown && next != null);
                }
            });
            thread.setDaemon(true);
            thread.start();
        }
    }

    public int getLocationsToClassifyCount() {
        return locationsToClassifyCount;
    }
    
    public int getLocationsClassifiedCount() {
        synchronized (locationsClassified) {
            return locationsClassified.size();
        }
    }
    
    /**
     * Returns all already-classified locations.
     */
    public ClassifierData getResults() {
        List<Location> locationList = new ArrayList<Location>();
        List<LocationAndVisits> locationAndVisitsList = new ArrayList<LocationAndVisits>();
        synchronized (locationsClassified) {
            for (LocationAndVisits locationAndVisits: locationsClassified) {
                locationList.add(locationAndVisits.location);
                locationAndVisitsList.add(locationAndVisits);
            }
        }
        List<LocationAndVisits> codeRelatedLocations = new ArrayList<LocationAndVisits>();
        List<LocationAndVisits> nonCodeRelatedLocations = new ArrayList<LocationAndVisits>();
        List<LocationAndVisits> unknownLocations = new ArrayList<LocationAndVisits>();
        for (LocationAndVisits locationAndVisits: locationAndVisitsList) {
            if (locationAndVisits.location.locationType == LocationType.CODE_RELATED) {
                codeRelatedLocations.add(locationAndVisits);
            } else if (locationAndVisits.location.locationType == LocationType.NON_CODE_RELATED) {
                nonCodeRelatedLocations.add(locationAndVisits);
            } else {
                unknownLocations.add(locationAndVisits);
            }
        }
        return new ClassifierData(initialVisitCount, redirectVisitCount, overallRevisitRate,
                googleVisitCount, locationsToClassifyCount, locationList.size(),
                codeRelatedLocations.size(), nonCodeRelatedLocations.size(), unknownLocations.size(), 
                codeRelatedLocations, locationList, visitList);
    }
    
    public void shutdown() {
        isShutdown = true;
        if (httpClient != null) {
            httpClient.getConnectionManager().shutdown();
        }
    }
    
    protected void classifyLocation(Location location, boolean dumpFile) {
        try {
            long startTimeMsecs = System.currentTimeMillis();
            
            InputStream inputStream = null;
            HttpGet httpGet = null;
            ByteArrayOutputStream page = null;
            HttpEntity responseEntity = null;
            try {
                if (location.url.startsWith("file:")) {
                    inputStream = new FileInputStream(new File(new URI(location.url)));
                } else {
                    httpGet = new HttpGet(location.url);
                    httpGet.setHeader("User-Agent", FIREFOX_USER_AGENT);
    
                    HttpResponse response = httpClient.execute(httpGet);
                    responseEntity = response.getEntity();
                    inputStream = responseEntity.getContent();
                    Header contentType = responseEntity.getContentType();
                    if (contentType == null || contentType.getValue() == null) {
                        throw new HistoryMinerException("Missing content type header");
                    }
                    String contentTypeValue = contentType.getValue();
                    if (!(contentTypeValue.startsWith("text/") || contentTypeValue.startsWith("application/xml") || 
                            contentTypeValue.startsWith("application/xhtml+xml"))) {
                        throw new HistoryMinerException("Unsupported content type '" + contentTypeValue + "'");
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
                            throw new HistoryMinerException("Page exceeded max size (" + 
                                    MAX_PAGE_SIZE + " bytes): " + location.url);
                        }
                    }
                }
                
                if (responseEntity != null) {
                    // Ensures input stream is closed and connection is released.
                    EntityUtils.consume(responseEntity);
                }
            } catch (Exception e) {
                if (httpGet != null) {
                    // Recommended cleanup for HTTP request in case of exception.
                    httpGet.abort();
                }
                throw e;
            } finally {
                // Need this for the case where input stream is associated with a file.
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (Exception e) { }
                }
            }
            
            ByteArrayInputStream byteStream = new ByteArrayInputStream(page.toByteArray());
            
            Document doc = Jsoup.parse(byteStream, null, location.url);
            
            long endTimeMsecs = System.currentTimeMillis();
            long pageGetTime = endTimeMsecs - startTimeMsecs;
            log.debug("Page get/parse time = " + pageGetTime + " msecs");
            location.locationType = classifyDocument(doc, dumpFile);
            
            try {
                if (indexerConnection != null) {
                    indexerConnection.indexPage(new PageInfo(location.url, doc.outerHtml()));
                }
            } catch (HistoryMinerException e) {
                log.error("Error submitting page for indexing: " + location.url, e);
            }
            
            // baseUri attribute is not reliable.
            // location.url = doc.baseUri();
            location.title = doc.title();
        } catch (Exception e) {
            log.info("Exception processing URL '" + location.url + "': " + e);
            location.locationType = LocationType.UNKNOWN;
        } 
    }
    
    protected LocationType classifyDocument(Document doc, boolean dumpFile) {
            
        long startTimeMsecs = System.currentTimeMillis();
        Elements scripts = doc.select("script");
        for (Element script: scripts) {
            script.remove();
        }
        Elements plaintext = doc.select("plaintext");
        for (Element element: plaintext) {
            element.remove();
        }
        Elements allElements = doc.getAllElements();
        for (Element element: allElements) {
            if (element.hasText()) {
                element.appendText(" ");
            }
        }

        String text = doc.text();
        
        Matcher matcher = methodPattern.matcher(text);
        List<String> matches = new ArrayList<String>();
        while (matches.size() < CODE_RELATED_MATCH_THRESHOLD && matcher.find()) {
            String match = text.substring(matcher.start(), matcher.end());
            log.debug("Found pattern: " + match);
            matches.add(match);
        }
        
        if (matches.size() < CODE_RELATED_MATCH_THRESHOLD) {
            matcher = noArgsMethodPattern.matcher(text);
            while (matches.size() < CODE_RELATED_MATCH_THRESHOLD && matcher.find()) {
                String match = text.substring(matcher.start(), matcher.end());
                log.debug("Found pattern: " + match);
                matches.add(match);
            }
        }

        // False positives on this pattern, e.g. references at bottom of page http://en.wikipedia.org/wiki/Fukushima_Daiichi_nuclear_disaster
        /*
        if (matches.size() < CODE_RELATED_MATCH_THRESHOLD) {
            matcher = methodInvocationPattern.matcher(text);
            while (matches.size() < CODE_RELATED_MATCH_THRESHOLD && matcher.find()) {
                String match = text.substring(matcher.start(), matcher.end());
                log.debug("Found pattern: " + match);
                matches.add(match);
            }
        }
        */
        
        long endTimeMsecs = System.currentTimeMillis();
        long classifyTime = endTimeMsecs - startTimeMsecs;
        
        log.debug("Page classify time = " + classifyTime + " msecs");
        if (dumpFile) {
            
            try {
                Writer writer = null;
                try {
                    writer = new FileWriter(new File("page.txt"));
                    writer.write(text);
                    writer.close();
                    writer = new FileWriter(new File("fullpage.html"));
                    writer.write(doc.outerHtml());
                } finally {
                    if (writer != null) { writer.close(); } 
                }
            } catch (IOException e) {
                log.error("Exception writing page to file", e);
            }

        }

        if (matches.size() >= CODE_RELATED_MATCH_THRESHOLD) {
            return LocationType.CODE_RELATED;
        } else {
            return LocationType.NON_CODE_RELATED;
        }
    }

    private List<HistoryVisit> filterRedirects(List<HistoryVisit> visitList) {
        if (webBrowserType == WebBrowserType.MOZILLA_FIREFOX) {
            return filterRedirectsFirefox(visitList);
        }
        return filterRedirectsChrome(visitList);
    }
    
    private List<HistoryVisit> filterRedirectsFirefox(List<HistoryVisit> visitList) {
        List<HistoryVisit> result= new ArrayList<HistoryVisit>();
        for (HistoryVisit visit: visitList) {
            if (visit.visitType == FirefoxVisitType.LINK ||
                    visit.visitType == FirefoxVisitType.TYPED ||
                    visit.visitType == FirefoxVisitType.BOOKMARK) {
                result.add(visit);
            }
        }
        return result;
    }
    
    private List<HistoryVisit> filterRedirectsChrome(List<HistoryVisit> visitList) {
        List<HistoryVisit> result= new ArrayList<HistoryVisit>();
        for (HistoryVisit visit: visitList) {
            // Transition is not a redirect, or is end of redirect chain.
            // For Chrome, not all redirect chains start with a CHAIN_START transition (e.g.
            // transition generated by a search at the Wikipedia home page).  As a result, 
            // unlike Firefox, we include the *end* of the redirect chain, rather than the start.
            if ((visit.visitType & 0xF0000000) == 0 || (visit.visitType & ChromeVisitType.CHAIN_END) != 0) {
                int maskedVisitType = (visit.visitType & ChromeVisitType.CORE_MASK);
                if (maskedVisitType == ChromeVisitType.LINK ||
                        maskedVisitType == ChromeVisitType.TYPED ||
                        maskedVisitType == ChromeVisitType.AUTO_BOOKMARK ||
                        maskedVisitType == ChromeVisitType.GENERATED ||
                        maskedVisitType == ChromeVisitType.START_PAGE ||
                        maskedVisitType == ChromeVisitType.FORM_SUBMIT ||
                        maskedVisitType == ChromeVisitType.KEYWORD) {
                    result.add(visit);
                }
                
            }
        }
        return result;
    }

    private LocationAndVisits getNextLocation() {
        synchronized (locationsToClassify) {
            if (locationsToClassify.isEmpty()) {
                return null;
            }
            return locationsToClassify.remove(0);
        }
    }
    
    private void addClassifiedLocation(LocationAndVisits locationAndVisits) {
        synchronized (locationsClassified) {
            locationsClassified.add(locationAndVisits);
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
