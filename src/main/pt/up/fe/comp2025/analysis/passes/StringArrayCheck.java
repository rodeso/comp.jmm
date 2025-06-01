package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;

/**
 * Reports an error when a variable or field is declared as String[].
 */
public class StringArrayCheck extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.VAR_DECL, this::visitVarDecl);
    }

    private Void visitVarDecl(JmmNode varDecl, SymbolTable table) {
        // First child is TYPE node
        JmmNode typeNode = varDecl.getChildren().get(0);
        if (Kind.TYPE.check(typeNode)) {
            String baseType = typeNode.getChild(0).getOptional("name").orElse("");
            String op1 = typeNode.getOptional("op1").orElse("");
            String op2 = typeNode.getOptional("op2").orElse("");
            if (baseType.equals("String") && op1.equals("[") && op2.equals("]")) {
                addReport(Report.newError(
                    Stage.SEMANTIC,
                    varDecl.getLine(),
                    varDecl.getColumn(),
                    "String arrays are not supported",
                    null
                ));
            }
        }
        return null;
    }
} 