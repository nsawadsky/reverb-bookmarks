package ca.ubc.cs.reverb.indexer.messages;

public class BlockCodeElementRequest implements IndexerMessage {
    public BlockCodeElementRequest() { }
    
    public BlockCodeElementRequest(CodeElement codeElement) {
        this.codeElement = codeElement;
    }
    
    public CodeElement codeElement;
}
