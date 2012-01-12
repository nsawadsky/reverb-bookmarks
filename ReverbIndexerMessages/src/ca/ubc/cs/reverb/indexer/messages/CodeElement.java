package ca.ubc.cs.reverb.indexer.messages;

public class CodeElement {
    public CodeElement() { }
    
    public CodeElement(CodeElementType elementType, String packageName,
            String className, String memberName) {
        this.elementType = elementType;
        this.packageName = packageName;
        this.className = className;
        this.memberName = memberName;
    }

    public CodeElementType elementType;
    public String packageName;
    public String className;
    public String memberName;
}
