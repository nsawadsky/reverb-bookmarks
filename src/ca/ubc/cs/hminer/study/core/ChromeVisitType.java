package ca.ubc.cs.hminer.study.core;

public class ChromeVisitType {
    public final static int LINK = 0;
    public final static int TYPED = 1;
    public final static int AUTO_BOOKMARK = 2;
    public final static int AUTO_SUBFRAME = 3;
    public final static int MANUAL_SUBFRAME = 4;
    public final static int GENERATED = 5;
    public final static int START_PAGE = 6;
    public final static int FORM_SUBMIT = 7;
    public final static int RELOAD = 8;
    public final static int KEYWORD = 9;
    public final static int KEYWORD_GENERATED = 10;
    
    // Redirect flags.
    public final static int CHAIN_START =     0x10000000;
    public final static int CHAIN_END =       0x20000000;
    public final static int CLIENT_REDIRECT = 0x40000000;
    public final static int SERVER_REDIRECT = 0x80000000;
}
