package ca.ubc.cs.reverb.indexer.messages;

public class CodeElementError {
    public CodeElementError() { }
    public CodeElementError(CodeElement element, String errorMessage) {
        this.codeElement = element;
        this.errorMessage = errorMessage;
    }
    
    public CodeElement codeElement;
    public String errorMessage;
}
