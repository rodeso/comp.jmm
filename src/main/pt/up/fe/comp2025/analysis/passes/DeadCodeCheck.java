package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;

/**
 * Reports unreachable code following a return statement within a method.
 */
public class DeadCodeCheck extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.RETURN_STMT, this::visitReturn);
    }

    private Void visitReturn(JmmNode returnNode, SymbolTable table) {
        JmmNode parent = returnNode.getParent();
        if (!Kind.METHOD_DECL.check(parent))
            return null;
        boolean seen = false;
        for (JmmNode child : parent.getChildren()) {
            if (child == returnNode) {
                seen = true;
            } else if (seen) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        child.getLine(),
                        child.getColumn(),
                        "Unreachable code after return",
                        null));
            }
        }
        return null;
    }
} 