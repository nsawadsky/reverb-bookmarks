package ca.ubc.cs.hminer.eclipseplugin.views;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.TypeDeclaration;

public class QueryBuilderASTVisitor extends ASTVisitor {
    private List<String> queryStrings = new ArrayList<String>();
    private int startPosition;
    private int endPosition;
    
    public QueryBuilderASTVisitor(int startPosition, int endPosition) {
        
    }
    
    public List<String> getQueryStrings() {
        return queryStrings;
    }
    
    @Override
    public boolean visit(FieldAccess node) {
        return super.visit(node);
    }

    @Override
    public boolean visit(MethodDeclaration node) {
        if (nodeOverlaps(node)) {
            if (! queryStrings.contains(node.getName().getIdentifier())) {
                queryStrings.add(node.getName().getIdentifier());
            }
            return true;
        }
        return false;
    }
    
    // TODO: Add support for enum declaration and other kinds of types.

    @Override
    public boolean visit(MethodInvocation node) {
        if (nodeOverlaps(node)) {
            if (! queryStrings.contains(node.getName().getIdentifier())) {
                queryStrings.add(node.getName().getIdentifier());
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean visit(SimpleType node) {
        if (nodeOverlaps(node)) {
            Name name = node.getName();
            SimpleName simpleName = null;
            if (name.isSimpleName()) {
                simpleName = (SimpleName)name; 
            } else if (name.isQualifiedName()) {
                QualifiedName qualifiedName = (QualifiedName)name;
                simpleName = qualifiedName.getName();
            }
            if (simpleName != null) {
                if (! queryStrings.contains(simpleName.getIdentifier())) {
                    queryStrings.add(simpleName.getIdentifier());
                }
            } 
            return true;
        }
        return false;
    }

    @Override
    public boolean visit(TypeDeclaration node) {
        if (nodeOverlaps(node)) {
            if (! queryStrings.contains(node.getName().getIdentifier())) {
                queryStrings.add(node.getName().getIdentifier());
            }
            return true;
        }
        return false;
    }

    private boolean nodeOverlaps(ASTNode node) {
        int nodeStartPosition = node.getStartPosition();
        int nodeEndPosition = nodeStartPosition + node.getLength();
        return (nodeStartPosition < this.endPosition && nodeEndPosition > this.startPosition);
    }
}
