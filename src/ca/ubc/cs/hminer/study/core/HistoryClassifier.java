package ca.ubc.cs.hminer.study.core;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

public class HistoryClassifier {
    private static Logger log = Logger.getLogger(HistoryClassifier.class);
    
    private final static int POOL_SIZE = 20; 
    private final static String FIREFOX_USER_AGENT = 
        "Mozilla/5.0 (Windows NT 6.0; rv:5.0) Gecko/20100101 Firefox/5.0";
    private final static int CODE_RELATED_MATCH_THRESHOLD = 2;
    private final static int PAGE_TIMEOUT_MSECS = 5000;
    
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
     */
    private final static String CAMEL_CASE_PATTERN = 
        "(?x) \\b (?: [a-z] \\w*? [A-Z] \\w* | [A-Z] \\w*? [a-z] \\w*? [A-Z] \\w* | [A-Z]{2,} \\w*? [a-z] \\w* )";
    
    /**
     * Starts with a camel-case word, followed by open-bracket, followed by
     * at most 40 non-close-bracket characters, followed by a camel-case word. 
     */
    private final static String METHOD_PATTERN = 
        "(?x)" + CAMEL_CASE_PATTERN + "\\s* \\( [^\\)]{0,40}?" + CAMEL_CASE_PATTERN;
    
    /**
     * Starts with a word that begins with a lower- or upper-case letter, followed by
     * empty brackets.
     */
    private final static String NO_ARGS_METHOD_PATTERN = 
            "(?x) \\b [a-zA-Z]\\w* \\s* \\( \\s* \\)";

    /**
     * Starts with a word that begins with a lower- or upper-case letter, followed
     * by a dot, followed by another word starting with a lower- or upper-case letter,
     * followed by open-bracket, followed by up to 200 characters, followed by 
     * close-bracket and semi-colon.
     */
    private final static String METHOD_INVOCATION_PATTERN = 
        "(?x) \\b [a-zA-Z]\\w* \\s* \\. \\s* [a-zA-Z]\\w* \\s* \\( .{0,200}? \\);";
    
    private List<HistoryVisit> visitList;
    private Pattern methodPattern;
    private Pattern methodInvocationPattern;
    private Pattern noArgsMethodPattern;
    private Pattern googleSearchPattern;
    private ThreadPoolExecutor executor;
    private int initialVisitCount = 0;
    private int redirectVisitCount = 0;
    private double overallRevisitRate = 0.0;
    private int googleVisitCount = 0;
    private int locationsToClassifyCount = 0;
    
    private Queue<LocationAndVisits> locationsClassified;  

    public HistoryClassifier(List<HistoryVisit> visitList) {
        ThreadFactory factory = new ThreadFactory() {

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable);
                thread.setDaemon(true);
                return thread;
            }
            
        };
        this.executor = new ThreadPoolExecutor(POOL_SIZE, POOL_SIZE, 0, TimeUnit.SECONDS, 
                new LinkedBlockingQueue<Runnable>(), factory);
        this.locationsClassified = new LinkedBlockingQueue<LocationAndVisits>();
        
        this.visitList = visitList;
        this.methodPattern = Pattern.compile(METHOD_PATTERN, Pattern.DOTALL);
        this.noArgsMethodPattern = Pattern.compile(NO_ARGS_METHOD_PATTERN, Pattern.DOTALL);
        this.methodInvocationPattern = Pattern.compile(METHOD_INVOCATION_PATTERN, Pattern.DOTALL);
        
        this.googleSearchPattern = Pattern.compile(GOOGLE_SEARCH_PATTERN);
    }
    
    public static void main(String[] args) {
        BasicConfigurator.configure();
        
        HistoryClassifier classifier = new HistoryClassifier(null);
        classifier.testClassifier(args[0]);
    }
    
    public void testClassifier(String url) {
        Location location = new Location(0, url, "");
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
    }
    
    public void startClassifying() {
        this.initialVisitCount = visitList.size();
        
        // First filter out visits we are not interested in.
        List<HistoryVisit> redirectsFiltered = new ArrayList<HistoryVisit>();
        for (HistoryVisit visit: visitList) {
            if (visit.visitType == FirefoxVisitType.LINK ||
                    visit.visitType == FirefoxVisitType.TYPED ||
                    visit.visitType == FirefoxVisitType.BOOKMARK) {
                redirectsFiltered.add(visit);
            }
        }
        
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
        
        for (final LocationAndVisits locationAndVisits: locationsById.values()) {
            executor.submit(new Runnable() {

                @Override
                public void run() {
                    classifyLocation(locationAndVisits.location, false);
                    locationsClassified.add(locationAndVisits);
                }
                
            });
        }
    }

    public int getLocationsToClassifyCount() {
        return locationsToClassifyCount;
    }
    
    public int getLocationsClassifiedCount() {
        return locationsClassified.size();
    }
    
    /**
     * Drains the queue of already-classified locations.
     */
    public ClassifierData getResults() {
        List<Location> locationList = new ArrayList<Location>();
        List<LocationAndVisits> locationAndVisitsList = new ArrayList<LocationAndVisits>();
        while (!locationsClassified.isEmpty()) {
            LocationAndVisits locationAndVisits = locationsClassified.remove();
            locationList.add(locationAndVisits.location);
            locationAndVisitsList.add(locationAndVisits);
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
    
    public boolean isDone() {
        return getLocationsClassifiedCount() == getLocationsToClassifyCount();
    }
    
    public void shutdown() {
        executor.shutdown();
    }
    
    protected void classifyLocation(Location location, boolean dumpFile) {
        try {
            long startTimeMsecs = System.currentTimeMillis();
            Document doc;
            if (location.url.startsWith("file:")) {
                doc = Jsoup.parse(new File(new URI(location.url)), null);
            } else {
                doc = Jsoup.connect(location.url)
                        .userAgent(FIREFOX_USER_AGENT)
                        .timeout(PAGE_TIMEOUT_MSECS)
                        .get();
            }
            long endTimeMsecs = System.currentTimeMillis();
            long pageGetTime = endTimeMsecs - startTimeMsecs;
            log.debug("Page get time = " + pageGetTime + " msecs");
            location.locationType = classifyDocument(doc, dumpFile);
            
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
        
        // TODO: Only capture as many matches as are needed to classify page.
        Matcher matcher = methodPattern.matcher(text);
        List<String> matches = new ArrayList<String>();
        while (matcher.find()) {
            String match = text.substring(matcher.start(), matcher.end());
            log.debug("Found pattern: " + match);
            matches.add(match);
        }
        
        matcher = noArgsMethodPattern.matcher(text);
        while (matcher.find()) {
            String match = text.substring(matcher.start(), matcher.end());
            log.debug("Found pattern: " + match);
            matches.add(match);
        }

        matcher = methodInvocationPattern.matcher(text);
        while (matcher.find()) {
            String match = text.substring(matcher.start(), matcher.end());
            log.debug("Found pattern: " + match);
            matches.add(match);
        }
        
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
    
    
}
