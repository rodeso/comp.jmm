package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;


import java.util.List;
import java.util.Objects;

import static pt.up.fe.comp2025.ast.Kind.*;
import static pt.up.fe.comp2025.ast.TypeUtils.*;

public class Assignment extends AnalysisVisitor {


    @Override
    public void buildVisitor() {
        addVisit(Kind.ASSIGN_STMT, this::visitAssign);

    }

    private Void visitAssign(JmmNode assignStmt, SymbolTable symbolTable) {
        JmmNode expr1 = assignStmt.getChild(0);
        JmmNode expr2 = assignStmt.getChild(1);

        Type typeExpr1 = getExprType(expr1);
        Type typeExpr2 = getExprType(expr2);

        //Assignments lhs may only be of type id[expr] or id
        if(!(ARRAY_ACCESS.check(expr1) && VAR_REF_EXPR.check(expr1.getChild(0)))&& !VAR_REF_EXPR.check(expr1) && !OBJECT_REFERENCE.check(expr1)){
            // Create error report
            var message = String.format("Assignment left hand operand must be a valid ID.");
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    assignStmt.getLine(),
                    assignStmt.getColumn(),
                    message,
                    null)
            );
        }
        else if (!isAssignable(typeExpr2, typeExpr1)) {
            String array1 = typeExpr1.isArray() ? " array" : "";
            String array2 = typeExpr2.isArray() ? " array" : "";

            if(Objects.equals(typeExpr2.getName(), "imported")){
                expr2.putObject("type", typeExpr1);
                return null;
            }

            // Create error report
            var message = String.format("Assignment of '%s' of type %s to %s of type %s is not allowed.", expr1.toString(), typeExpr1.getName() + array1, expr2.toString(), typeExpr2.getName() + array2);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    assignStmt.getLine(),
                    assignStmt.getColumn(),
                    message,
                    null)
            );
        }

        assignStmt.putObject("type", storeType(typeExpr1, symbolTable));

        return null;
    }

}
