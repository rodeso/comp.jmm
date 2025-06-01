package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;

/**
 * Reports an error when a variable is declared with varargs syntax outside of parameter declarations.
 */
public class VarargsVarDeclCheck extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.VAR_DECL, this::visitVarDecl);
    }

    private Void visitVarDecl(JmmNode varDecl, SymbolTable table) {
        // The first child is TYPE node
        JmmNode typeNode = varDecl.getChildren().get(0);
        // In varargs type, the TYPE node child has BASE_TYPE with args attribute
        if (Kind.TYPE.check(typeNode) || Kind.BASE_TYPE.check(typeNode)) {
            String args = typeNode.getOptional("args").orElse("");
            if (args.equals("...")) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        varDecl.getLine(),
                        varDecl.getColumn(),
                        "Varargs can only be used in method parameter declarations", null
                ));
            }
        }
        return null;
    }
} 