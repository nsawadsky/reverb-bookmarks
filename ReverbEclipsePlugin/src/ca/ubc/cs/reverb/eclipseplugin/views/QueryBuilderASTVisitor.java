package ca.ubc.cs.reverb.eclipseplugin.views;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldAccess;
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

public class QueryBuilderASTVisitor extends ASTVisitor {
    private List<String> queryStrings = new ArrayList<String>();
    private int startPosition;
    private int endPosition;
    private Pattern selectiveIdentifier;
    
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
    
    public List<String> getQueryStrings() {
        return queryStrings;
    }
    
    @Override
    public boolean visit(FieldAccess node) {
        return super.visit(node);
    }
    
    // TODO: Consider what are the cases where child nodes should not be visited.

    @Override
    public boolean visit(MethodDeclaration node) {
        if (nodeOverlaps(node)) {
            addToQueryStrings(node.getName().getIdentifier());
            return true;
        }
        return false;
    }
    
    @Override
    public boolean visit(MethodInvocation node) {
        if (nodeOverlaps(node)) {
            addToQueryStrings(node.getName().getIdentifier());
            return true;
        }
        return false;
    }

    @Override
    public boolean visit(EnumDeclaration node) {
        if (nodeOverlaps(node)) {
            addToQueryStrings(node.getName().getIdentifier());
            return true;
        }
        return false;
    }

    @Override
    public boolean visit(TypeDeclaration node) {
        if (nodeOverlaps(node)) {
            addToQueryStrings(node.getName().getIdentifier());
            return true;
        }
        return false;
    }
    
    @Override
    public boolean visit(SimpleType node) {
        if (nodeOverlaps(node)) {
            addToQueryStrings(getTypeKeywords(node));
            return true;
        }
        return false;
    }

    @Override
    public boolean visit(QualifiedType node) {
        if (nodeOverlaps(node)) {
            addToQueryStrings(getTypeKeywords(node));
            return true;
        }
        return false;
    }

    @Override
    public boolean visit(ArrayType node) {
        if (nodeOverlaps(node)) {
            addToQueryStrings(getTypeKeywords(node));
            return true;
        }
        return false;
    }

    @Override
    public boolean visit(ParameterizedType node) {
        if (nodeOverlaps(node)) {
            addToQueryStrings(getTypeKeywords(node));
            return true;
        }
        return false;
    }

    private List<String> getTypeKeywords(Type node) {
        if (node instanceof QualifiedType) {
            return getTypeKeywords((QualifiedType)node);
        } else if (node instanceof ParameterizedType) {
            return getTypeKeywords((ParameterizedType)node);
        } else if (node instanceof ArrayType) {
            return getTypeKeywords((ArrayType)node); 
        } else if (node instanceof SimpleType) {
            return getTypeKeywords((SimpleType)node);
        } 
        return null;
    }
    
    private List<String> getTypeKeywords(QualifiedType node) {
        Type qualifier = node.getQualifier();
        List<String> qualifierKeywords = getTypeKeywords(qualifier);
        String identifier = node.getName().getIdentifier();
        if (qualifierKeywords != null && qualifierKeywords.size() > 0) {
            return Arrays.asList(qualifierKeywords.get(qualifierKeywords.size()-1), identifier);
        }
        return Arrays.asList(identifier);
    }
    
    private List<String> getTypeKeywords(ParameterizedType node) {
        return getTypeKeywords(node.getType());
    }
    
    private List<String> getTypeKeywords(ArrayType node) {
        return getTypeKeywords(node.getElementType());
    }
    
    private List<String> getTypeKeywords(SimpleType node) {
        SimpleName simpleName = simpleNameFromName(node.getName());
        if (simpleName == null) {
            return null;
        }
        return Arrays.asList(simpleName.getIdentifier());
    }
    
    private SimpleName simpleNameFromName(Name name) {
        if (name.isSimpleName()) {
            return (SimpleName)name; 
        } else if (name.isQualifiedName()) {
            return ((QualifiedName)name).getName();
        } 
        return null;
    }
    
    private void addToQueryStrings(String keyword) {
        if (keyword != null && ! queryStrings.contains(keyword)) {
            Matcher matcher = selectiveIdentifier.matcher(keyword);
            if (matcher.find()) {
                queryStrings.add(keyword);
            }
        }
    }
    
    private void addToQueryStrings(List<String> keywords) {
        for (String keyword: keywords) {
            addToQueryStrings(keyword);
        }
    }
    
    private boolean nodeOverlaps(ASTNode node) {
        int nodeStartPosition = node.getStartPosition();
        int nodeEndPosition = nodeStartPosition + node.getLength();
        return (nodeStartPosition < this.endPosition && nodeEndPosition > this.startPosition);
    }
    
}
