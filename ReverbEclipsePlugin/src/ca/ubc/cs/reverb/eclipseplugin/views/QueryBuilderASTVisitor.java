package ca.ubc.cs.reverb.eclipseplugin.views;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Type;
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
            queryBuilder.append(element.typeQuery);
            
            for (String memberQuery: element.memberQueries) {
                queryBuilder.append(" ");
                queryBuilder.append(memberQuery);
            }
            
            StringBuilder queryToDisplay = new StringBuilder(element.simpleTypeName);
            for (String memberQuery: element.memberQueries) {
                queryToDisplay.append(" ");
                queryToDisplay.append(memberQuery);
            }
            queries.add(new IndexerQuery(queryBuilder.toString(), queryToDisplay.toString()));
        }
        return queries;
    }
    
    // TODO: Verify assumption that we will get array element type through a separate visit() call.
    @Override
    public boolean visit(ArrayType node) {
        return super.visit(node);
    }

    @Override
    public boolean visit(FieldAccess node) {
        if (nodeOverlaps(node)) {
            ITypeBinding typeBinding = node.resolveTypeBinding();
            if (typeBinding != null) {
                IVariableBinding varBinding = node.resolveFieldBinding();
                if (varBinding != null) {
                    int modifiers = varBinding.getModifiers();
                    // TODO: Verify this picks up enum constants.
                    if (Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers)) {
                        QueryElement element = getBindingQueryElement(typeBinding);
                        if (element != null) {
                            element.memberQueries.add(node.getName().getIdentifier());
                            addToQueryElements(element);
                        }
                    }
                }
            }
        }
        return super.visit(node);
    }
    
    @Override
    public boolean visit(MethodDeclaration node) {
        if (nodeOverlaps(node)) {
            if (! node.isConstructor()) {
                IMethodBinding methodBinding = node.resolveBinding();
                if (methodBinding != null) {
                    ITypeBinding typeBinding = methodBinding.getDeclaringClass();
                    if (typeBinding != null) {
                        ITypeBinding superClass = typeBinding.getSuperclass();
                        if (superClass != null && ! superClass.equals(getWellKnownObjectBinding(node.getAST())) &&
                                bindingHasMethod(superClass, methodBinding)) {
                            QueryElement element = getBindingQueryElement(superClass);
                            if (element != null) {
                                element.memberQueries.add(node.getName().getIdentifier());
                                addToQueryElements(element);
                            }
                        } else {
                            for (ITypeBinding itfc: typeBinding.getInterfaces()) {
                                if (bindingHasMethod(itfc, methodBinding)) {
                                    QueryElement element = getBindingQueryElement(itfc);
                                    if (element != null) {
                                        element.memberQueries.add(node.getName().getIdentifier());
                                        addToQueryElements(element);
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            return true;
        }
        return false;
    }
    
    @Override
    public boolean visit(MethodInvocation node) {
        if (nodeOverlaps(node)) {
            QueryElement typeElement = getBindingQueryElement(node.resolveTypeBinding());
            if (typeElement == null) {
                String identifier = node.getName().getIdentifier();
                if (!nameNeedsResolution(identifier)) {
                    addToQueryElements(new QueryElement(identifier, identifier));
                }
            } else {
                typeElement.memberQueries.add(node.getName().getIdentifier());
                addToQueryElements(typeElement);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean visit(EnumDeclaration node) {
        if (nodeOverlaps(node)) {
            String identifier = node.getName().getIdentifier();
            if (nameNeedsResolution(identifier)) {
                addToQueryElements(getBindingQueryElement(node.resolveBinding()));
            } else {
                addToQueryElements(new QueryElement(identifier, identifier));
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean visit(TypeDeclaration node) {
        if (nodeOverlaps(node)) {
            String identifier = node.getName().getIdentifier();
            if (nameNeedsResolution(identifier)) {
                addToQueryElements(getBindingQueryElement(node.resolveBinding()));
            } else {
                addToQueryElements(new QueryElement(identifier, identifier));
            }
            return true;
        }
        return false;
    }
    
    @Override
    public boolean visit(SimpleType node) {
        if (nodeOverlaps(node)) {
            addToQueryElements(getTypeQueryElement(node));
            return true;
        }
        return false;
    }

    @Override
    public boolean visit(QualifiedType node) {
        if (nodeOverlaps(node)) {
            addToQueryElements(getTypeQueryElement(node));
            return true;
        }
        return false;
    }

    // TODO: Verify assumption that we will get the parameter types through separate visit() calls.
    @Override
    public boolean visit(ParameterizedType node) {
        if (nodeOverlaps(node)) {
            addToQueryElements(getTypeQueryElement(node));
            return true;
        }
        return false;
    }

    private QueryElement getTypeQueryElement(Type node) {
        if (node instanceof QualifiedType) {
            return getTypeQueryElement((QualifiedType)node);
        } else if (node instanceof ParameterizedType) {
            return getTypeQueryElement((ParameterizedType)node);
        } else if (node instanceof SimpleType) {
            return getTypeQueryElement((SimpleType)node);
        } 
        return null;
    }
    
    private QueryElement getTypeQueryElement(ParameterizedType node) {
        return getTypeQueryElement(node.getType());
    }
    
    private QueryElement getTypeQueryElement(QualifiedType node) {
        Type qualifier = node.getQualifier();
        if (! (qualifier instanceof SimpleType)) {
            // The other possibility here is ParameterizedType, which we do not support.
            return null;
        }
        SimpleType simpleQualifier = (SimpleType)qualifier;
        SimpleName qualifierName = simpleNameFromName(simpleQualifier.getName());
        if (qualifierName == null) {
            return null;
        }
        String name = qualifierName.getIdentifier() + "." + node.getName().getIdentifier();
        if (nameNeedsResolution(name)) {
            return getBindingQueryElement(node.resolveBinding());
        }
        return new QueryElement(name, name);
    }
    
    private QueryElement getTypeQueryElement(SimpleType node) {
        SimpleName simpleName = simpleNameFromName(node.getName());
        if (simpleName == null) {
            return null;
        }
        String identifier = simpleName.getIdentifier();
        if (PRIMITIVES.contains(identifier)) {
            return null;
        }
        if (nameNeedsResolution(identifier)) {
            return getBindingQueryElement(node.resolveBinding());
        }
        return new QueryElement(identifier, identifier);
    }
    
    private SimpleName simpleNameFromName(Name name) {
        if (name.isSimpleName()) {
            return (SimpleName)name; 
        } else if (name.isQualifiedName()) {
            return ((QualifiedName)name).getName();
        } 
        return null;
    }
    
    private QueryElement getBindingQueryElement(ITypeBinding binding) {
        if (binding == null) {
            return null;
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
        if (fullyQualifiedName.length() <= pkgName.length() + 1) {
            return null;
        }
        // This provides better handling for inner classes, as opposed to calling ITypeBinding.getName(),
        // which just returns the simple name of the inner class.
        String name = fullyQualifiedName.substring(pkgName.length() + 1);
        return new QueryElement("((" + fullyQualifiedName + " OR " + pkgName + ") AND " + name + ")", name);
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
                if (curr.typeQuery.equals(element.typeQuery)) {
                    existing = curr;
                    break;
                }
            }
            if (existing == null) {
                queryElements.add(element);
            } else {
                for (String memberQuery: element.memberQueries) {
                    if (!existing.memberQueries.contains(memberQuery)) {
                        existing.memberQueries.add(memberQuery);
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
 
    private boolean bindingHasMethod(ITypeBinding typeBinding, IMethodBinding methodBinding) {
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
        public String typeQuery;
        public String simpleTypeName;
        public List<String> memberQueries = new ArrayList<String>();
        
        public QueryElement(String typeQuery, String simpleTypeName) {
            this.typeQuery = typeQuery;
            this.simpleTypeName = simpleTypeName;
        }
    }
}
