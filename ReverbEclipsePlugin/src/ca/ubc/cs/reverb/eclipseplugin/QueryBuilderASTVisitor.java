package ca.ubc.cs.reverb.eclipseplugin;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import ca.ubc.cs.reverb.indexer.messages.IndexerQuery;

public class QueryBuilderASTVisitor extends ASTVisitor {
    private List<QueryElement> queryElements = new ArrayList<QueryElement>();
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
        "(?x) \\b (?: [a-z] \\w*? [A-Z] \\w* | [A-Z] \\w*? [a-z] \\w*? [A-Z] \\w* | [A-Z]{2,} \\w*? [a-z] \\w* |" + 
                "[a-zA-Z] \\w*? _ [a-zA-Z0-9] \\w* )";
    
    private static List<String> PRIMITIVES = Arrays.asList(
            "String", "byte", "short", "int", "long", "float", "double", "boolean", "char");
    
    public QueryBuilderASTVisitor(int startPosition, int endPosition) {
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        selectiveIdentifier = Pattern.compile(IDENTIFIER_PATTERN);
    }
    
    public List<IndexerQuery> getQueries() {
        List<IndexerQuery> queries = new ArrayList<IndexerQuery>();
        for (QueryElement element: queryElements) {
            StringBuilder queryBuilder = new StringBuilder("+");
            queryBuilder.append(element.requiredQuery);
            
            for (String optionalQuery: element.optionalQueries) {
                queryBuilder.append(" ");
                queryBuilder.append(optionalQuery);
            }
            
            StringBuilder queryToDisplay = new StringBuilder(element.requiredQueryDisplayText);
            for (String optionalQuery: element.optionalQueries) {
                queryToDisplay.append(" ");
                queryToDisplay.append(optionalQuery);
            }
            queries.add(new IndexerQuery(queryBuilder.toString(), queryToDisplay.toString()));
        }
        return queries;
    }
    
    // TODO: Add support for annotations.
    
    @Override 
    public boolean visit(QualifiedName node) {
        if (!nodeOverlaps(node)) {
            return false;
        }
        
        IBinding binding = node.resolveBinding();
        if (binding instanceof IVariableBinding) {
            IVariableBinding varBinding = (IVariableBinding)binding;

            int modifiers = varBinding.getModifiers();
            if (varBinding.isField() && Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers)) {
                String qualifierName = simpleIdentifierFromName(node.getQualifier());
                QueryElement element = getBindingQueryElement(qualifierName, varBinding.getDeclaringClass());
                if (element != null) {
                    element.optionalQueries.add(node.getName().getIdentifier());
                    addToQueryElements(element);
                }
            }
            
        }
        // Do not need to visit children of top-level QualifiedName.
        return false;
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
                QueryElement element = getBindingQueryElement(null, varBinding.getDeclaringClass());
                if (element != null) {
                    element.optionalQueries.add(node.getIdentifier());
                    addToQueryElements(element);
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
            QueryElement element = getBindingQueryElement(null, superClass);
            if (element != null) {
                element.optionalQueries.add(node.getName().getIdentifier());
                addToQueryElements(element);
            }
        } else {
            for (ITypeBinding itfc: typeBinding.getInterfaces()) {
                if (typeBindingHasMethod(itfc, methodBinding)) {
                    QueryElement element = getBindingQueryElement(null, itfc);
                    if (element != null) {
                        element.optionalQueries.add(node.getName().getIdentifier());
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
                addToQueryElements(new QueryElement(identifier, identifier));
            }
            return true;
        }
        ITypeBinding typeBinding = methodBinding.getDeclaringClass();
        if (typeBinding == null) {
            return true;
        }
        QueryElement typeElement = getBindingQueryElement(null, typeBinding);
        if (typeElement != null) {
            typeElement.optionalQueries.add(node.getName().getIdentifier());
            addToQueryElements(typeElement);
        }
        return true;
    }

    @Override
    public boolean visit(EnumDeclaration node) {
        if (nodeOverlaps(node)) {
            String identifier = node.getName().getIdentifier();
            addToQueryElements(getBindingQueryElement(identifier, node.resolveBinding()));
            return true;
        }
        return false;
    }

    @Override
    public boolean visit(TypeDeclaration node) {
        if (nodeOverlaps(node)) {
            String identifier = node.getName().getIdentifier();
            addToQueryElements(getBindingQueryElement(identifier, node.resolveBinding()));
            return true;
        }
        return false;
    }
    
    @Override
    public boolean visit(SimpleType node) {
        if (nodeOverlaps(node)) {
            String identifier = simpleIdentifierFromName(node.getName());
            if (!PRIMITIVES.contains(identifier)) {
                addToQueryElements(getBindingQueryElement(identifier, node.resolveBinding()));
            }
        }
        // No need to visit child nodes for SimpleType.
        return false;
    }

    private String simpleIdentifierFromName(Name name) {
        if (name.isSimpleName()) {
            return ((SimpleName)name).getIdentifier(); 
        } else if (name.isQualifiedName()) {
            return ((QualifiedName)name).getName().getIdentifier();
        } 
        // Should never reach this line.
        return name.getFullyQualifiedName();
    }
    
    private QueryElement getBindingQueryElement(String identifier, ITypeBinding binding) {
        if (identifier != null && !nameNeedsResolution(identifier)) {
            return new QueryElement(identifier, identifier);
        }
        if (binding == null) {
            return null;
        }
        
        if (binding.isParameterizedType() || binding.isRawType()) {
            binding = binding.getTypeDeclaration();
            if (binding == null) {
                return null;
            }
        }

        // TODO: Add support for inner (member) classes.
        String name = binding.getName();
        
        if (!nameNeedsResolution(name)) {
            return new QueryElement(name, name);
        }
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
        return new QueryElement("((" + fullyQualifiedName + " OR " + pkgName + ".\\*) AND " + name + ")", 
                name);
    }
    
    private boolean nameNeedsResolution(String name) {
        Matcher matcher = selectiveIdentifier.matcher(name);
        if (matcher.find()) {
            return false;
        } 
        return true;
    }
    
    private void addToQueryElements(QueryElement element) {
        if (element != null) {
            QueryElement existing = null;
            for (QueryElement curr: queryElements) {
                if (curr.requiredQuery.equals(element.requiredQuery)) {
                    existing = curr;
                    break;
                }
            }
            if (existing == null) {
                queryElements.add(element);
            } else {
                for (String optionalQuery: element.optionalQueries) {
                    if (!existing.optionalQueries.contains(optionalQuery)) {
                        existing.optionalQueries.add(optionalQuery);
                    }
                }
            }
        }
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
    
    private class QueryElement {
        public String requiredQuery;
        public String requiredQueryDisplayText;
        public List<String> optionalQueries = new ArrayList<String>();
        
        public QueryElement(String requiredQuery, String requiredQueryDisplayText) {
            this.requiredQuery = requiredQuery;
            this.requiredQueryDisplayText = requiredQueryDisplayText;
        }
    }
}
