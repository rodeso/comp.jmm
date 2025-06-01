package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

public class ReturnTypeCheck extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.RETURN_STMT, this::visitReturnStmt);
    }

    private Void visitReturnStmt(JmmNode returnNode, SymbolTable table) {
        // Find enclosing method
        JmmNode methodDecl = TypeUtils.getParentMethod(returnNode);
        String methodName = methodDecl.get("name");
        Type expected = table.getReturnType(methodName);
        boolean isVoid = expected != null && expected.getName().equals("void");

        // Has expression?
        boolean hasExpr = returnNode.getNumChildren() > 0;

        if (isVoid) {
            if (hasExpr) {
                // return with value in void method
                addReport(Report.newError(Stage.SEMANTIC,
                        returnNode.getLine(), returnNode.getColumn(),
                        "Return with value found in void method '" + methodName + "'.", null));
            }
            return null;
        }

        // Non-void: must have expression
        if (!hasExpr) {
            addReport(Report.newError(Stage.SEMANTIC,
                    returnNode.getLine(), returnNode.getColumn(),
                    "Missing return value in non-void method '" + methodName + "'.", null));
            return null;
        }

        // Check expression type
        JmmNode expr = returnNode.getChild(0);
        // Compute expression type using TypeUtils instance
        TypeUtils utils = new TypeUtils(table);
        Type actual = utils.getExprTypeNotStatic(expr, methodDecl);
        if (actual == null) {
            addReport(Report.newError(Stage.SEMANTIC,
                    expr.getLine(), expr.getColumn(),
                    "Could not determine return expression type in method '" + methodName + "'.", null));
            return null;
        }

        // Compare types
        if (!TypeUtils.isAssignable(actual, expected)) {
            addReport(Report.newError(Stage.SEMANTIC,
                    returnNode.getLine(), returnNode.getColumn(),
                    "Incompatible return type in method '" + methodName + "'. Expected '" + expected.print() +
                            "' but found '" + actual.print() + "'.", null));
        }

        return null;
    }
} 