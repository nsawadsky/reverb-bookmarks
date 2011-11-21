package testpackage;

public class MethodInvoc {
    public void testMethod() {
        Methoddecl methoddecl = new Methoddecl();
        methoddecl.methoddeclMethod();
        
        methoddecl.baseclassdeclMethod();
        
        Methoddecl.staticMethod();
        
        MethoddeclNores methoddeclNores = new MethoddeclNores();
        methoddeclNores.methoddeclNoresMethod();
        
        UnknownType a = new UnknownType();
        a.methoddeclNores();
    }
}
