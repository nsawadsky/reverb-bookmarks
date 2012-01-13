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

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CodeElement other = (CodeElement) obj;
        if (className == null) {
            if (other.className != null)
                return false;
        } else if (!className.equals(other.className))
            return false;
        if (elementType != other.elementType)
            return false;
        if (memberName == null) {
            if (other.memberName != null)
                return false;
        } else if (!memberName.equals(other.memberName))
            return false;
        if (packageName == null) {
            if (other.packageName != null)
                return false;
        } else if (!packageName.equals(other.packageName))
            return false;
        return true;
    }
    
    @Override
    public String toString() {
        return "CodeElement: type=" + elementType + ", package=" + packageName +
                ", class=" + className +", member=" + memberName;
    }

}
