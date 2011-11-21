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
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import ca.ubc.cs.reverb.indexer.messages.IndexerQuery;

public class QueryBuilderASTVisitor extends ASTVisitor {
    private List<QueryElementsForKey> queryElementsByKey = new ArrayList<QueryElementsForKey>();
    private int startPosition;
    private int endPosition;
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
    
    private final static Pattern SELECTIVE_IDENTIFIER = Pattern.compile(IDENTIFIER_PATTERN);
    
    private static List<String> SKIP_TYPES = Arrays.asList(
            "java.lang.String", "String", "java.lang.Override", "java.lang.Deprecated", 
            "byte", "short", "int", "long", "float", "double", "boolean", "char");
    
    public QueryBuilderASTVisitor(AST ast, int startPosition, int endPosition) {
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        objectBinding = ast.resolveWellKnownType("java.lang.Object");
    }
    
    public List<IndexerQuery> getQueries() {
        List<IndexerQuery> queries = new ArrayList<IndexerQuery>();
        for (QueryElementsForKey queryElementsForKey: queryElementsByKey) {
            List<QueryElement> elementList = queryElementsForKey.queryElements;
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
        return queries;
    }
    
    @Override 
    public boolean visit(NormalAnnotation node) {
        return visitAnnotation(node);
    }
    
    @Override 
    public boolean visit(MarkerAnnotation node) {
        return visitAnnotation(node);
    }
    
    @Override 
    public boolean visit(SingleMemberAnnotation node) {
        return visitAnnotation(node);
    }

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
                    addStaticMemberToQuery(declarer, node.getIdentifier());
                }
            }
            
        }
        // No need to visit children of SimpleName/QualifiedName.
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
    
        ITypeBinding originalDeclaringType = findOriginalDeclaringType(typeBinding, false, false, methodBinding);
        
        if (originalDeclaringType != null) {
            QueryElement element = getTypeQueryElement(originalDeclaringType, null);
            if (element != null) {
                String identifier = node.getName().getIdentifier();
                element.addOptionalQuery(identifier, identifier);
                addToQueryElements(element);
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
                QueryElement result = new QueryElement(identifier, identifier, identifier);
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
            addStaticMemberToQuery(typeBinding, identifier);
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
            if (!SKIP_TYPES.contains(name)) {
                QueryElement element = getTypeQueryElement(node.resolveBinding(), name);
                if (element != null) {
                    addToQueryElements(element);
                }
            }
        }
        // No need to visit child nodes for SimpleType.
        return false;
    }
    
    private boolean visitAnnotation(Annotation node) {
        if (nodeOverlaps(node)) {
            IAnnotationBinding annotationBinding = node.resolveAnnotationBinding();
            if (annotationBinding != null) {
                if (!SKIP_TYPES.contains(annotationBinding.getAnnotationType().getQualifiedName())) {
                    QueryElement element = getTypeQueryElement(annotationBinding.getAnnotationType(), null);
                    if (element != null) { 
                        addToQueryElements(element);
                    }
                }
            }
            return true;
        } 
        return false;
    }

    private ITypeBinding findOriginalDeclaringType(ITypeBinding typeBinding, boolean checkThisType, boolean isInterface,
            IMethodBinding methodBinding) {
        if (checkThisType && typeBindingHasMethod(typeBinding, methodBinding)) {
            return typeBinding;
        }
        for (ITypeBinding itfc: typeBinding.getInterfaces()) {
            ITypeBinding found = findOriginalDeclaringType(itfc, true, true, methodBinding);
            if (found != null) {
                return found;
            }
        }
        if (!isInterface) {
            ITypeBinding parentBinding = typeBinding.getSuperclass(); 
            if (parentBinding != null && !parentBinding.equals(objectBinding)) {
                ITypeBinding found = findOriginalDeclaringType(parentBinding, true, false, methodBinding);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
    
    private void addStaticMemberToQuery(ITypeBinding typeBinding, String memberIdentifier) {
        TypeInfo typeInfo = getTypeInfo(typeBinding);
        if (typeInfo == null) {
            return;
        }

        QueryElement typeQueryElement = getTypeQueryElement(typeInfo, true);
        typeQueryElement.addOptionalQuery(memberIdentifier, memberIdentifier);
        addToQueryElements(typeQueryElement);
        
        QueryElement memberReferenceElement = new QueryElement(typeInfo.fullyQualifiedName, 
                typeInfo.className + "." + memberIdentifier, typeInfo.className);
        memberReferenceElement.addDisplayText(memberIdentifier);
        addToQueryElements(memberReferenceElement);
    }
    
    private QueryElement getTypeQueryElement(ITypeBinding typeBinding, String typeIdentifier) {
        if (typeBinding == null) {
            if (typeIdentifier != null && !nameNeedsResolution(typeIdentifier)) {
                return new QueryElement(typeIdentifier, typeIdentifier, typeIdentifier);
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
        
        TypeInfo info = getTypeInfo(typeBinding);
        if (info == null) {
            return null;
        }
        
        return getTypeQueryElement(info, false); 
    }
    
    private QueryElement getTypeQueryElement(TypeInfo typeInfo, boolean forceResolution) {
        if (!forceResolution && !nameNeedsResolution(typeInfo.className)) {
            return new QueryElement(typeInfo.fullyQualifiedName, typeInfo.className, typeInfo.className);
        }
        
        // Note that our tokenizer will remove the trailing ".*" from package imports, so 
        // pkgName alone will match such imports.
        QueryElement result = new QueryElement(typeInfo.fullyQualifiedName, 
                "(" + typeInfo.fullyQualifiedName + " OR " + typeInfo.packageName + ")", typeInfo.className);
        result.addOptionalQuery(typeInfo.className, null);
        return result;
    }
    
    private TypeInfo getTypeInfo(ITypeBinding binding) {
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
        return new TypeInfo(fullyQualifiedName, pkgName);
    }
    
    private boolean nameNeedsResolution(String name) {
        Matcher matcher = SELECTIVE_IDENTIFIER.matcher(name);
        if (matcher.matches()) {
            return false;
        } 
        return true;
    }
    
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
    
    private class TypeInfo {
        public TypeInfo(String fullyQualifiedName, String packageName) {
            this.packageName = packageName;
            this.fullyQualifiedName = fullyQualifiedName;
            if (fullyQualifiedName.length() > packageName.length() + 1) {
                className = fullyQualifiedName.substring(packageName.length()+1);
            }
        }
        
        public String fullyQualifiedName;
        public String packageName;
        public String className;
    }
    
    private class QueryElementsForKey {
        public QueryElementsForKey(String key) {
            this.key = key;
        }
        
        public String key;
        public List<QueryElement> queryElements = new ArrayList<QueryElement>();
    }
    
    private class QueryElement {
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
