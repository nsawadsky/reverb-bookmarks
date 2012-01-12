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

    public String getFullyQualifiedName() {
        if (packageName == null || className == null) {
            return null;
        }
        return packageName + "." + className;
    }
    
    public CodeElementType elementType;
    public String packageName;
    public String className;
    public String memberName;

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((className == null) ? 0 : className.hashCode());
        result = prime * result
                + ((elementType == null) ? 0 : elementType.hashCode());
        result = prime * result
                + ((memberName == null) ? 0 : memberName.hashCode());
        result = prime * result
                + ((packageName == null) ? 0 : packageName.hashCode());
        return result;
    }

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

}
