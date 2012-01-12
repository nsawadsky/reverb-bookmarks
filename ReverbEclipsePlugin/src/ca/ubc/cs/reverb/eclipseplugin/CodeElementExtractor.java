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
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
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

import ca.ubc.cs.reverb.indexer.messages.CodeElement;
import ca.ubc.cs.reverb.indexer.messages.CodeElementType;

public class CodeElementExtractor extends ASTVisitor {
    private List<CodeElement> codeElements = new ArrayList<CodeElement>();
    private int startPosition;
    private int endPosition;
    
    /**
     * The type binding for java.lang.Object.
     */
    private ITypeBinding objectBinding;
    
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
    
    /**
     * Types which are never included in the query.
     */
    private static List<String> SKIP_TYPES = Arrays.asList(
            "java.lang.String", "String", "java.lang.Override", "java.lang.Deprecated", 
            "byte", "short", "int", "long", "float", "double", "boolean", "char");
    
    public CodeElementExtractor(AST ast, int startPosition, int endPosition) {
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        objectBinding = ast.resolveWellKnownType("java.lang.Object");
    }
    
    public List<CodeElement> getCodeElements() {
        return this.codeElements;
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

    /** 
     * Catch qualified references to static final fields.
     */
    @Override 
    public boolean visit(QualifiedName node) {
        return visit(node.getName());
    }
    
    /** 
     * Catch references to static final fields.
     */
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
                    CodeElement element = getCodeElement(CodeElementType.STATIC_FIELD_REF, declarer, null);
                    if (element != null) {
                        element.memberName = node.getIdentifier();
                        addToCodeElements(element);
                    }
                }
            }
            
        }
        // No need to visit children of SimpleName/QualifiedName.
        return false;
    }

    /**
     * Catch method declarations which override or implement a method in a parent class or interface.
     */
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
            CodeElement element = getCodeElement(CodeElementType.METHOD_DECL, originalDeclaringType, null);
            if (element != null) {
                element.memberName = node.getName().getIdentifier();
                addToCodeElements(element);
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
            // If the method binding cannot be resolved, but the method name itself is selective enough,
            // then include just the method name in the query.
            if (!nameNeedsResolution(identifier)) {
                addToCodeElements(new CodeElement(CodeElementType.METHOD_CALL, null, null, identifier));
            }
            return true;
        }
        ITypeBinding typeBinding = methodBinding.getDeclaringClass();
        if (typeBinding == null) {
            return true;
        }
        
        String identifier = node.getName().getIdentifier();
        CodeElementType elementType = (Modifier.isStatic(methodBinding.getModifiers()) ? 
                CodeElementType.STATIC_METHOD_CALL : CodeElementType.METHOD_CALL);
        
        CodeElement element = getCodeElement(elementType, typeBinding, null);
        if (element != null) {
            element.memberName = identifier;
            addToCodeElements(element);
        }
        
        return true;
    }

    @Override
    public boolean visit(EnumDeclaration node) {
        return visitTypeDeclaration(node);
    }

    @Override
    public boolean visit(TypeDeclaration node) {
        return visitTypeDeclaration(node);
    }

    @Override
    public boolean visit(AnnotationTypeDeclaration node) {
        return visitTypeDeclaration(node);
    }

    /**
     * Catch references to types (e.g. variable declarations, method parameters, field declarations).
     * Array type declarations will result in a call to this method for the type of the elements.
     * Generic type instantiations result in a call to this method for the generic type, as well as the
     * parameter types.  
     */
    @Override
    public boolean visit(SimpleType node) {
        if (nodeOverlaps(node)) {
            String name = node.getName().getFullyQualifiedName();
            if (!SKIP_TYPES.contains(name)) {
                CodeElement element = getCodeElement(CodeElementType.TYPE_REF, node.resolveBinding(), name);
                if (element != null) {
                    addToCodeElements(element);
                }
            }
        }
        // No need to visit child nodes for SimpleType.
        return false;
    }
    
    /**
     * Catch type declarations (there may actually be web pages that discuss the code the developer
     * is developing -- creating queries for type declarations goes a little way towards including such
     * pages in the result list).
     */
    private boolean visitTypeDeclaration(AbstractTypeDeclaration node) {
        if (nodeOverlaps(node)) {
            String identifier = node.getName().getFullyQualifiedName();
            CodeElement element = getCodeElement(CodeElementType.TYPE_DECL, node.resolveBinding(), identifier);
            if (element != null) {
                addToCodeElements(element);
            }
            return true;
        }
        return false;
    }
    
    private boolean visitAnnotation(Annotation node) {
        // TODO: If annotation type name is selective enough on its own, do not need type binding? 
        if (nodeOverlaps(node)) {
            IAnnotationBinding annotationBinding = node.resolveAnnotationBinding();
            if (annotationBinding != null) {
                CodeElement element = getCodeElement(CodeElementType.TYPE_REF, annotationBinding.getAnnotationType(), null);
                if (element != null) { 
                    addToCodeElements(element);
                }
            }
            return true;
        } 
        return false;
    }

    /**
     * Find the parent class or interface which declares a given method (if one exists).
     * Currently, for performance and simplicity, this method matches the method name only, and ignores
     * the parameter lists.
     */
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
    
    private CodeElement getCodeElement(CodeElementType elementType, ITypeBinding typeBinding, String typeIdentifier) {
        if (typeBinding == null) {
            // If no type binding is available, but the type name alone is quite selective,
            // add the type name on its own to the query.  
            if (typeIdentifier != null && !SKIP_TYPES.contains(typeIdentifier) && !nameNeedsResolution(typeIdentifier)) {
                return new CodeElement(elementType, null, typeIdentifier, null);
            } else {
                return null;
            }
        }
        
        // Handle instances of generic types.
        if (typeBinding.isParameterizedType() || typeBinding.isRawType()) {
            typeBinding = typeBinding.getTypeDeclaration();
            if (typeBinding == null) {
                return null;
            }
        }
        
        CodeElement element = typeBindingToCodeElement(elementType, typeBinding);
        if (element == null) {
            return null;
        }
        
        if (SKIP_TYPES.contains(element.getFullyQualifiedName())) {
            return null;
        }
        
        return element;
    }
    
    private CodeElement typeBindingToCodeElement(CodeElementType elementType, ITypeBinding binding) {
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
        if (fullyQualifiedName.length() < pkgName.length() + 2) {
            return null;
        }
        String className = fullyQualifiedName.substring(pkgName.length()+1);
        return new CodeElement(elementType, pkgName, className, null);
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
     * Does the node overlap the source file range we are interested in?
     */
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
    
    private void addToCodeElements(CodeElement element) {
        if (element != null && !codeElements.contains(element)) {
            codeElements.add(element);
        }
    }
}
