package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;

/**
 * Reports an error for variable or field declarations of type void.
 */
public class VoidTypeCheck extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.VAR_DECL, this::visitVarDecl);
    }

    private Void visitVarDecl(JmmNode varDecl, SymbolTable table) {
        // The first child is the TYPE node
        JmmNode typeNode = varDecl.getChildren().get(0);
        String typeName = typeNode.getOptional("name").orElse("");
        if (typeName.equals("void")) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    varDecl.getLine(),
                    varDecl.getColumn(),
                    "Variable or field cannot be declared with void type", null
            ));
        }
        return null;
    }
} 