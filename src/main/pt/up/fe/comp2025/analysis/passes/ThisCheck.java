package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

public class ThisCheck extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        // Visit any 'this' reference (represented by OBJECT_REFERENCE nodes)
        addVisit(Kind.OBJECT_REFERENCE, this::visitThisReference);
    }

    /**
     * Reports an error if 'this' is used inside a static method.
     */
    private Void visitThisReference(JmmNode thisNode, SymbolTable table) {
        // Find the enclosing method declaration
        JmmNode methodDecl = TypeUtils.getParentMethod(thisNode);
        if (methodDecl.hasAttribute("s")) {
            // Cannot use 'this' in a static context
            addReport(newError(thisNode, "'this' cannot be used in static context"));
        }
        return null;
    }

}
