package ca.ubc.cs.reverb.eclipseplugin;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import ca.ubc.cs.reverb.indexer.messages.IndexerQuery;

public class QueryBuilderASTVisitor extends ASTVisitor {
    private List<QueryElementsForKey> queryElementsByKey = new ArrayList<QueryElementsForKey>();
    private int startPosition;
    private int endPosition;
    private Pattern selectiveIdentifier;
    private ITypeBinding objectBinding;
    
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
        "(?x) (?: [a-z] [\\.\\w]*? [A-Z] [\\.\\w]* | [A-Z] [\\.\\w]*? [a-z] [\\.\\w]*? [A-Z] [\\.\\w]* | [A-Z]{2,} [\\.\\w]*? [a-z] [\\.\\w]* |" + 
                "[a-zA-Z] [\\.\\w]*? _ [a-zA-Z0-9] [\\.\\w]* )";
    
    private static List<String> PRIMITIVES = Arrays.asList(
            "java.lang.String", "String", "byte", "short", "int", "long", "float", "double", "boolean", "char");
    
    public QueryBuilderASTVisitor(int startPosition, int endPosition) {
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        selectiveIdentifier = Pattern.compile(IDENTIFIER_PATTERN);
    }
    
    public List<IndexerQuery> getQueries() {
        List<IndexerQuery> queries = new ArrayList<IndexerQuery>();
        for (QueryElementsForKey elementsForKey: queryElementsByKey) {
            List<QueryElement> elementList = elementsForKey.elements;
            List<QueryElement> mergedList = new ArrayList<QueryElement>();
            for (QueryElement element: elementList) {
                boolean merged = false;
                for (QueryElement mergedElement: mergedList) {
                    if (mergedElement.tryMerge(element)) {
                        merged = true;
                        break;
                    }
                }
                if (!merged) {
                    mergedList.add(element);
                }
            }
            StringBuilder query = new StringBuilder();
            StringBuilder display = new StringBuilder();
            Set<String> allDisplayTexts = new HashSet<String>();
            for (QueryElement mergedElement: mergedList) {
                if (query.length() > 0) {
                    query.append(" OR ");
                }
                if (mergedList.size() > 1) {
                    query.append("(");
                }
                query.append(mergedElement.getQuery());
                if (mergedList.size() > 1) {
                    query.append(")");
                }
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
        return queries;
    }
    
    // TODO: Add support for annotations.
    
    @Override 
    public boolean visit(QualifiedName node) {
        return visit(node.getName());
    }
    
    @Override 
    public boolean visit(SimpleName node) {
        if (!nodeOverlaps(node)) {
            return false;
        }

        IBinding binding = node.resolveBinding();
        if (binding instanceof IVariableBinding) {
            IVariableBinding varBinding = (IVariableBinding)binding;

            int modifiers = varBinding.getModifiers();
            if (varBinding.isField() && Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers)) {
                ITypeBinding declarer = varBinding.getDeclaringClass();
                if (declarer != null) {
                    QueryElement element = getStaticMemberQueryElement(declarer, node.getIdentifier());
                    if (element != null) {
                        addToQueryElements(element);
                    }
                }
            }
            
        }
        // No need to visit children of SimpleName.
        return false;
    }

    @Override
    public boolean visit(MethodDeclaration node) {
        if (!nodeOverlaps(node)) {
            return false;
        }
        if (node.isConstructor()) {
            return true;
        }
        IMethodBinding methodBinding = node.resolveBinding();
        if (methodBinding == null) {
            return true;
        }
        ITypeBinding typeBinding = methodBinding.getDeclaringClass();
        if (typeBinding == null) {
            return true;
        }
    
        ITypeBinding superClass = typeBinding.getSuperclass();
        if (superClass != null && ! superClass.equals(getWellKnownObjectBinding(node.getAST())) &&
                typeBindingHasMethod(superClass, methodBinding)) {
            QueryElement element = getTypeQueryElement(superClass, null);
            if (element != null) {
                String identifier = node.getName().getIdentifier();
                element.addOptionalQuery(identifier, identifier);
                addToQueryElements(element);
            }
        } else {
            for (ITypeBinding itfc: typeBinding.getInterfaces()) {
                if (typeBindingHasMethod(itfc, methodBinding)) {
                    QueryElement element = getTypeQueryElement(itfc, null);
                    if (element != null) {
                        String identifier = node.getName().getIdentifier();
                        element.addOptionalQuery(identifier, identifier);
                        addToQueryElements(element);
                    }
                    break;
                }
            }
        }
        return true;
    }
    
    @Override
    public boolean visit(MethodInvocation node) {
        if (!nodeOverlaps(node)) {
            return false;
        }

        IMethodBinding methodBinding = node.resolveMethodBinding();
        if (methodBinding == null) {
            String identifier = node.getName().getIdentifier();
            if (!nameNeedsResolution(identifier)) {
                QueryElement result = new QueryElement(identifier);
                result.addRequiredQuery(identifier, identifier);
                addToQueryElements(result);
            }
            return true;
        }
        ITypeBinding typeBinding = methodBinding.getDeclaringClass();
        if (typeBinding == null) {
            return true;
        }
        String identifier = node.getName().getIdentifier();
        if (Modifier.isStatic(methodBinding.getModifiers())) {
            QueryElement methodElement = getStaticMemberQueryElement(typeBinding, identifier);
            if (methodElement != null) {
                addToQueryElements(methodElement);
            }
        } else {
            QueryElement typeElement = getTypeQueryElement(typeBinding, null);
            if (typeElement != null) {
                typeElement.addOptionalQuery(identifier, identifier);
                addToQueryElements(typeElement);
            }
        }
        return true;
    }

    @Override
    public boolean visit(EnumDeclaration node) {
        if (nodeOverlaps(node)) {
            String identifier = node.getName().getFullyQualifiedName();
            QueryElement element = getTypeQueryElement(node.resolveBinding(), identifier);
            if (element != null) {
                addToQueryElements(element);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean visit(TypeDeclaration node) {
        if (nodeOverlaps(node)) {
            String identifier = node.getName().getFullyQualifiedName();
            QueryElement element = getTypeQueryElement(node.resolveBinding(), identifier);
            if (element != null) {
                addToQueryElements(element);
            }
            return true;
        }
        return false;
    }
    
    @Override
    public boolean visit(SimpleType node) {
        if (nodeOverlaps(node)) {
            String name = node.getName().getFullyQualifiedName();
            if (!PRIMITIVES.contains(name)) {
                QueryElement element = getTypeQueryElement(node.resolveBinding(), name);
                if (element != null) {
                    addToQueryElements(element);
                }
            }
        }
        // No need to visit child nodes for SimpleType.
        return false;
    }

    private QueryElement getStaticMemberQueryElement(ITypeBinding typeBinding, String memberIdentifier) {
        String fullyQualifiedName = typeBinding.getQualifiedName();
        if (fullyQualifiedName.isEmpty()) {
            return null;
        }
        
        String partlyQualifiedName = getPartlyQualifiedName(typeBinding);
        if (partlyQualifiedName == null) {
            return null;
        }
        
        QueryElement result = new QueryElement(fullyQualifiedName);
        String memberReference = partlyQualifiedName + "." + memberIdentifier;
        result.addOptionalQuery(memberReference, memberReference);
        result.addOptionalQuery("(" + fullyQualifiedName + " AND " + memberIdentifier + ")", null);
        return result;
    }
    
    private QueryElement getTypeQueryElement(ITypeBinding typeBinding, String typeIdentifier) {
        QueryElement result = null;
        if (typeBinding == null) {
            if (typeIdentifier != null && !nameNeedsResolution(typeIdentifier)) {
                result = new QueryElement(typeIdentifier);
                result.addRequiredQuery(typeIdentifier, typeIdentifier);
                return result;
            } else {
                return null;
            }
        }
        
        if (typeBinding.isParameterizedType() || typeBinding.isRawType()) {
            typeBinding = typeBinding.getTypeDeclaration();
            if (typeBinding == null) {
                return null;
            }
        }

        IPackageBinding pkg = typeBinding.getPackage();
        if (pkg == null) {
            return null;
        }
        String pkgName = pkg.getName();
        if (pkgName.isEmpty()) {
            return null;
        }
        String fullyQualifiedName = typeBinding.getQualifiedName();
        if (fullyQualifiedName.isEmpty()) {
            return null;
        }
        
        String partlyQualifiedName = getPartlyQualifiedName(typeBinding);
        if (partlyQualifiedName == null) {
            return null;
        }
        
        if (!nameNeedsResolution(partlyQualifiedName)) {
            result = new QueryElement(fullyQualifiedName);
            result.addRequiredQuery(partlyQualifiedName, partlyQualifiedName);
            return result;
        }
        
        // Note that our tokenizer will remove the trailing ".*" from package imports, so 
        // pkgName alone will match such imports.
        result = new QueryElement(fullyQualifiedName);
        result.addRequiredQuery("(" + fullyQualifiedName + " OR " + pkgName + ")", partlyQualifiedName);
        result.addOptionalQuery(partlyQualifiedName, null);
        return result;
    }
    
    private String getPartlyQualifiedName(ITypeBinding binding) {
        IPackageBinding pkg = binding.getPackage();
        if (pkg == null) {
            return null;
        }
        String pkgName = pkg.getName();
        if (pkgName.isEmpty()) {
            return null;
        }
        String fullyQualifiedName = binding.getQualifiedName();
        if (fullyQualifiedName.isEmpty()) {
            return null;
        }
        if (fullyQualifiedName.length() < pkgName.length() + 1) {
            return null;
        }
        return fullyQualifiedName.substring(pkgName.length() + 1);
    }
    
    private boolean nameNeedsResolution(String name) {
        Matcher matcher = selectiveIdentifier.matcher(name);
        if (matcher.matches()) {
            return false;
        } 
        return true;
    }
    
    private void addToQueryElements(QueryElement element) {
        QueryElementsForKey container = null;
        for (QueryElementsForKey elementsForKey: queryElementsByKey) {
            if (elementsForKey.key.equals(element.getKey())) {
                container = elementsForKey;
                break;
            }
        }
        if (container == null) {
            container = new QueryElementsForKey(element.getKey());
            queryElementsByKey.add(container);
        }
        container.elements.add(element);
    }
    
    private boolean nodeOverlaps(ASTNode node) {
        int nodeStartPosition = node.getStartPosition();
        int nodeEndPosition = nodeStartPosition + node.getLength();
        return (nodeStartPosition < this.endPosition && nodeEndPosition > this.startPosition);
    }
 
    private boolean typeBindingHasMethod(ITypeBinding typeBinding, IMethodBinding methodBinding) {
        for (IMethodBinding method: typeBinding.getDeclaredMethods()) {
            if (method.getName().equals(methodBinding.getName())) {
                return true;
            }
        }
        return false;
    }
    
    private ITypeBinding getWellKnownObjectBinding(AST ast) {
        if (objectBinding == null) {
            objectBinding = ast.resolveWellKnownType("java.lang.Object");
        }
        return objectBinding;
    }
    
    private class QueryElementsForKey {
        public String key;
        public List<QueryElement> elements = new ArrayList<QueryElement>();
        
        public QueryElementsForKey(String key) {
            this.key = key;
        }
    }
    
    private class QueryElement {
        private String key;
        private List<String> requiredQueries = new ArrayList<String>();
        private List<String> optionalQueries = new ArrayList<String>();
        private List<String> queryDisplayTexts = new ArrayList<String>();
        
        public QueryElement(String key) {
            this.key = key;
        }
        
        public String getKey() {
            return key;
        }
        
        public boolean tryMerge(QueryElement query) {
            if (requiredQueries.size() == query.requiredQueries.size() && 
                   (requiredQueries.size() == 0 || requiredQueries.containsAll(query.requiredQueries))) {
                for (String optional: query.optionalQueries) {
                    addOptionalQuery(optional, null);
                }
                for (String display: query.queryDisplayTexts) {
                    if (!queryDisplayTexts.contains(display)) {
                        queryDisplayTexts.add(display);
                    }
                }
                return true;
            }
            return false;
        }
        
        public void addRequiredQuery(String query, String displayText) {
            if (!requiredQueries.contains(query)) {
                optionalQueries.remove(query);
                requiredQueries.add(query);
                if (displayText != null) {
                    if (!queryDisplayTexts.contains(displayText)) {
                        queryDisplayTexts.add(displayText);
                    }
                }
            }
        }
        
        public void addOptionalQuery(String query, String displayText) {
            if (!requiredQueries.contains(query) && !optionalQueries.contains(query)) {
                optionalQueries.add(query);
                if (displayText != null) {
                    if (!queryDisplayTexts.contains(displayText)) {
                        queryDisplayTexts.add(displayText);
                    }
                }
            }
        }

        public String getQuery () {
            StringBuilder queryBuilder = new StringBuilder();
            for (String query: requiredQueries) {
                if (queryBuilder.length() > 0) {
                    queryBuilder.append(" ");
                }
                queryBuilder.append("+");
                queryBuilder.append(query);
            }
            for (String query: optionalQueries) {
                if (queryBuilder.length() > 0) {
                    queryBuilder.append(" ");
                }
                queryBuilder.append(query);
            }
            return queryBuilder.toString();
        }
        
        public List<String> getDisplayTexts() {
            return queryDisplayTexts;
        }
    }
}
