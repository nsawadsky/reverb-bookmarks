package ca.ubc.cs.reverb.eclipseplugin.views;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    
    private static List<String> STOP_WORDS = Arrays.asList(
            "String", "byte", "short", "int", "long", "float", "double", "boolean", "char");
    
    public QueryBuilderASTVisitor(int startPosition, int endPosition) {
        this.startPosition = startPosition;
        this.endPosition = endPosition;
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
            addToQueryStrings(getTypeKeyword(node));
            return true;
        }
        return false;
    }

    @Override
    public boolean visit(QualifiedType node) {
        if (nodeOverlaps(node)) {
            addToQueryStrings(getTypeKeyword(node));
            return true;
        }
        return false;
    }

    @Override
    public boolean visit(ArrayType node) {
        if (nodeOverlaps(node)) {
            addToQueryStrings(getTypeKeyword(node));
            return true;
        }
        return false;
    }

    @Override
    public boolean visit(ParameterizedType node) {
        if (nodeOverlaps(node)) {
            addToQueryStrings(getTypeKeyword(node));
            return true;
        }
        return false;
    }

    private String getTypeKeyword(Type node) {
        if (node instanceof QualifiedType) {
            return getTypeKeyword((QualifiedType)node);
        } else if (node instanceof ParameterizedType) {
            return getTypeKeyword((ParameterizedType)node);
        } else if (node instanceof ArrayType) {
            return getTypeKeyword((ArrayType)node); 
        } else if (node instanceof SimpleType) {
            return getTypeKeyword((SimpleType)node);
        } 
        return null;
    }
    
    private String getTypeKeyword(QualifiedType node) {
        Type qualifier = node.getQualifier();
        String qualifierKeyword = getTypeKeyword(qualifier);
        String identifier = node.getName().getIdentifier();
        if (qualifierKeyword != null) {
            return qualifierKeyword + " AND " + identifier;
        }
        return identifier;
    }
    
    private String getTypeKeyword(ParameterizedType node) {
        return getTypeKeyword(node.getType());
    }
    
    private String getTypeKeyword(ArrayType node) {
        return getTypeKeyword(node.getElementType());
    }
    
    private String getTypeKeyword(SimpleType node) {
        SimpleName simpleName = simpleNameFromName(node.getName());
        if (simpleName == null) {
            return null;
        }
        return simpleName.getIdentifier();
    }
    
    private SimpleName simpleNameFromName(Name name) {
        if (name.isSimpleName()) {
            return (SimpleName)name; 
        } else if (name.isQualifiedName()) {
            return ((QualifiedName)name).getName();
        } 
        return null;
    }
    
    private void addToQueryStrings(String query) {
        if (query != null && ! queryStrings.contains(query)) {
            queryStrings.add(query);
        }
    }
    
    private boolean nodeOverlaps(ASTNode node) {
        int nodeStartPosition = node.getStartPosition();
        int nodeEndPosition = nodeStartPosition + node.getLength();
        return (nodeStartPosition < this.endPosition && nodeEndPosition > this.startPosition);
    }
}
