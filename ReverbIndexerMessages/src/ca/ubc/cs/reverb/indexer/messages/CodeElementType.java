package ca.ubc.cs.reverb.indexer.messages;

public enum CodeElementType {
    /**
     * Type declaration.
     */
    TYPE_DECL,
    
    /**
     * Type reference (e.g. declaring a variable of a given type).
     */
    TYPE_REF,
    
    /**
     * Method declaration.  We are only interested in method declarations if they override a method 
     * from a parent class or interface.  In this case, the CodeElement will contain the package and 
     * class name for the parent class or interface.
     */
    METHOD_DECL,
    
    /**
     * Method invocation.
     */
    METHOD_CALL,
    
    /**
     * Reference to a constant static field.
     */
    STATIC_FIELD_REF,
    
    /**
     * Static method invocation.
     */
    STATIC_METHOD_CALL
}
