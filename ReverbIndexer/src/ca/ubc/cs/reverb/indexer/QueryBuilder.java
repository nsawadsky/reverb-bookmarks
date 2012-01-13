package ca.ubc.cs.reverb.indexer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import ca.ubc.cs.reverb.indexer.messages.CodeElement;
import ca.ubc.cs.reverb.indexer.messages.CodeElementError;
import ca.ubc.cs.reverb.indexer.messages.CodeElementType;
import ca.ubc.cs.reverb.indexer.messages.IndexerQuery;

public class QueryBuilder {
    private static Logger log = Logger.getLogger(QueryBuilder.class);
    
    private List<QueryElementsForKey> queryElementsByKey = null;
    private List<CodeElementError> errorElements = null;
    private List<IndexerQuery> queries = null;
    private List<CodeElement> codeElements = null;
    
    /**
     * Define a pattern which matches identifiers which are "selective" -- i.e. unlikely to match
     * ordinary English words.
     * 
     * Starts with lower-case and contains at least one upper-case 
     *   OR
     * Starts with upper-case and contains at least one lower-case, followed by at least one upper-case
     *   OR
     * Starts with two or more upper-case and contains at least one lower-case
     *   OR
     * Starts with letter, contains letters, decimal digits, and at least one underscore.
     */
    private final static String IDENTIFIER_PATTERN = 
        "(?x) (?: [a-z] [\\.\\w]*? [A-Z] [\\.\\w]* | [A-Z] [\\.\\w]*? [a-z] [\\.\\w]*? [A-Z] [\\.\\w]* | [A-Z]{2,} [\\.\\w]*? [a-z] [\\.\\w]* |" + 
                "[a-zA-Z] [\\.\\w]*? _ [a-zA-Z0-9] [\\.\\w]* )";
    
    private final static Pattern SELECTIVE_IDENTIFIER = Pattern.compile(IDENTIFIER_PATTERN);
    
    public QueryBuilder(List<CodeElement> codeElements) {
        this.codeElements = codeElements;
    }
    
    public void buildQueries() {
        errorElements = new ArrayList<CodeElementError>();
        
        queryElementsByKey = new ArrayList<QueryElementsForKey>();
        for (CodeElement codeElement: codeElements) {
            addQueryElementsForCodeElement(codeElement);
        }
        
        queries = new ArrayList<IndexerQuery>();
        for (QueryElementsForKey queryElementsForKey: queryElementsByKey) {
            List<QueryElement> elementList = queryElementsForKey.queryElements;
            List<QueryElement> mergedList = new ArrayList<QueryElement>();
            for (QueryElement element: elementList) {
                boolean merged = false;
                for (QueryElement mergedElement: mergedList) {
                    // Merge queries which have the same *required* query.
                    if (mergedElement.tryMerge(element)) {
                        merged = true;
                        break;
                    }
                }
                if (!merged) {
                    mergedList.add(element);
                }
            }
            // All queries for a given type are combined in to a single query string.
            StringBuilder query = new StringBuilder();
            StringBuilder display = new StringBuilder();
            Set<String> allDisplayTexts = new HashSet<String>();
            for (QueryElement mergedElement: mergedList) {
                if (query.length() > 0) {
                    query.append(" OR ");
                }
                query.append("(");
                query.append(mergedElement.getQuery());
                query.append(")");
                for (String displayText: mergedElement.getDisplayTexts()) {
                    if (!allDisplayTexts.contains(displayText)) {
                        allDisplayTexts.add(displayText);
                        if (display.length() > 0) {
                            display.append(" ");
                        }
                        display.append(displayText);
                    }
                }
            }
            queries.add(new IndexerQuery(query.toString(), display.toString()));
        }
    }
    
    public List<IndexerQuery> getQueries() {
        return this.queries;
    }
    
    public List<CodeElementError> getErrorElements() {
        return this.errorElements;
    }
    
    private void addQueryElementsForCodeElement(CodeElement codeElement) {
        try {
            switch (codeElement.elementType) {
            case TYPE_DECL:
            case TYPE_REF:
            {
                addToQueryElements(getTypeQueryElement(codeElement));
                break;
            }
            case METHOD_DECL:
            case METHOD_CALL:
            {
                if (codeElement.memberName == null) {
                    throw new IndexerException("Method decl or method call element missing member name");
                }
                if (codeElement.packageName == null && codeElement.className == null) {
                    if (!nameNeedsResolution(codeElement.memberName)) {
                        // If type information is not available, but the method name itself is selective enough,
                        // then include just the method name in the query.
                        addToQueryElements(new QueryElement(codeElement.memberName, 
                                codeElement.memberName, codeElement.memberName));
                    } else {
                        throw new IndexerException("Code element member name not selective enough to be used on its own");
                    }
                }
                QueryElement queryElement = getTypeQueryElement(codeElement);
                queryElement.addOptionalQuery(codeElement.memberName, codeElement.memberName);
                addToQueryElements(queryElement);
                break;
            }
            case STATIC_FIELD_REF:
            case STATIC_METHOD_CALL: 
            {
                if (codeElement.className == null || codeElement.memberName == null) {
                    throw new IndexerException("Static member reference element missing class name or member name");
                }
                try {
                    // Static methods and fields may be referenced without any qualifiers in two cases: if the class
                    // containing the member has been imported with "import static", or if we inherit from (or 
                    // implement) the containing class.  This query tries to capture these two cases.
                    QueryElement typeQueryElement = getTypeQueryElement(codeElement);
                    typeQueryElement.addOptionalQuery(codeElement.memberName, null);
                    // We do not add static field names to the displayed query.
                    if (codeElement.elementType == CodeElementType.STATIC_METHOD_CALL) {
                        typeQueryElement.addDisplayText(codeElement.memberName);
                    }
                    addToQueryElements(typeQueryElement);
                } catch (IndexerException e) { }
                    
                // The other (perhaps more common) way to reference a static member is with the containing class
                // as a qualifier.  This form is quite selective, so for this query we do not add the fully qualified
                // type name or package.
                String key = codeElement.className;
                if (codeElement.packageName != null) {
                    key = getFullyQualifiedName(codeElement);
                }
                QueryElement qualifiedRef = new QueryElement(
                        key, codeElement.className + "." + codeElement.memberName,
                        codeElement.className);
                // We do not add static field names to the displayed query.
                if (codeElement.elementType == CodeElementType.STATIC_METHOD_CALL) {
                    qualifiedRef.addDisplayText(codeElement.memberName);
                }
                addToQueryElements(qualifiedRef);
                break;
            }
            default: 
            {
                throw new IndexerException("Unknown code element type: " + codeElement.elementType);
            }
            }
        } catch (IndexerException e) {
            errorElements.add(new CodeElementError(codeElement, e.toString()));
            log.info("Code element could not be used to build query: " + codeElement, e);
        }
    }
    
    private QueryElement getTypeQueryElement(CodeElement codeElement) throws IndexerException {
        if (codeElement.packageName != null && codeElement.className != null) {
            String fullyQualifiedName = getFullyQualifiedName(codeElement);
            if (!nameNeedsResolution(codeElement.className)) {
                // If the type name is selective enough, allow it to match on its own.
                return new QueryElement(fullyQualifiedName, "(" + codeElement.className + " OR " + fullyQualifiedName + ")", 
                        codeElement.className);
            } 
            // Require that results also contain either the fully-qualified type name, or the package name and the class name.
            // Note that our tokenizer will remove the trailing ".*" from package imports, so 
            // pkgName alone will match such imports.
            String requiredQuery = "(" + fullyQualifiedName + " OR (" + codeElement.packageName + " AND " + codeElement.className + "))";
            QueryElement result = new QueryElement(fullyQualifiedName, requiredQuery, codeElement.className);
            result.addOptionalQuery(codeElement.className, null);
            return result;
        } else if (codeElement.className != null) {
            if (!nameNeedsResolution(codeElement.className)) {
                return new QueryElement(codeElement.className, codeElement.className, codeElement.className);
            }
            throw new IndexerException("Code element class name not selective enough to be used without package name");
        } 
        throw new IndexerException("Code element missing class name");
    }
    
    /**
     * Is the name alone selective enough for use in a query?
     */
    private boolean nameNeedsResolution(String name) {
        Matcher matcher = SELECTIVE_IDENTIFIER.matcher(name);
        if (matcher.matches()) {
            return false;
        } 
        return true;
    }
    
    /**
     * We group query elements by type -- a single query will eventually be generated for all
     * the query elements matching a given type.
     */
    private void addToQueryElements(QueryElement element) {
        QueryElementsForKey container = null;
        for (QueryElementsForKey queryElementsForKey: queryElementsByKey) {
            if (queryElementsForKey.key.equals(element.getKey())) {
                container = queryElementsForKey;
                break;
            }
        }
        if (container == null) {
            container = new QueryElementsForKey(element.getKey());
            queryElementsByKey.add(container);
        }
        container.queryElements.add(element);
    }
    
    private String getFullyQualifiedName(CodeElement element) {
        if (element.packageName == null || element.className == null) {
            return null;
        }
        return element.packageName + "." + element.className;
    }
    
    private class QueryElementsForKey {
        public QueryElementsForKey(String key) {
            this.key = key;
        }
        
        /**
         *  Key is usually the fully-qualified type name.
         */
        public String key;
        public List<QueryElement> queryElements = new ArrayList<QueryElement>();
    }
    
    private class QueryElement {
        /**
         *  Key is usually the fully-qualified type name.
         */
        private String key;
        private String requiredQuery;
        private List<String> optionalQueries = new ArrayList<String>();
        private List<String> queryDisplayTexts = new ArrayList<String>();
        
        public QueryElement(String key, String requiredQuery, String requiredQueryDisplayText) {
            this.key = key;
            this.requiredQuery = requiredQuery;
            if (requiredQueryDisplayText != null) {
                queryDisplayTexts.add(requiredQueryDisplayText);
            }
        }
        
        public String getKey() {
            return key;
        }
        
        public boolean tryMerge(QueryElement query) {
            if (requiredQuery.equals(query.requiredQuery)) {
                for (String optional: query.optionalQueries) {
                    addOptionalQuery(optional, null);
                }
                for (String display: query.queryDisplayTexts) {
                    addDisplayText(display);
                }
                return true;
            }
            return false;
        }
        
        public void addOptionalQuery(String query, String displayText) {
            if (!optionalQueries.contains(query)) {
                optionalQueries.add(query);
                if (displayText != null) {
                    addDisplayText(displayText);
                }
            }
        }
        
        public String getQuery () {
            StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("+");
            queryBuilder.append(requiredQuery);
            for (String query: optionalQueries) {
                queryBuilder.append(" ");
                queryBuilder.append(query);
            }
            return queryBuilder.toString();
        }

        public void addDisplayText(String displayText) {
            if (!queryDisplayTexts.contains(displayText)) {
                queryDisplayTexts.add(displayText);
            }
        }
        
        public List<String> getDisplayTexts() {
            return queryDisplayTexts;
        }

    }
    
}
