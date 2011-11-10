package testpackage;

import static testpackage.Fielddecls.*;
import static testpackage.FielddeclsNores.*;
import static testpackage.Enumvaluedecls.*;

public class FieldRefs {
	public void testMethod() {
		// Testing:
		//   - simple name, qualified name
		//   - type requires resolution, type does not requires resolution
		//   - field final and static, field not both final and static
		//   - fields, enums
		String temp = FIELDDECLS_CONSTANT_1;
		temp = FIELDDECLS_NORES_CONSTANT_1;
		temp = FIELDDECLS_NOT_FINAL;
		
		temp = Fielddecls.FIELDDECLS_CONSTANT_2;
		temp = FielddeclsNores.FIELDDECLS_NORES_CONSTANT_2;
		temp = Fielddecls.FIELDDECLS_NOT_FINAL;
		
		Enumvaluedecls tempEnum = ENUM_VALUE_1;
		tempEnum = Enumvaluedecls.ENUM_VALUE_2;
	}
}
