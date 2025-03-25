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

import static pt.up.fe.comp2025.ast.TypeUtils.getExprType;
import static pt.up.fe.comp2025.ast.TypeUtils.storeType;

public class ConditionCheck extends AnalysisVisitor {


    @Override
    public void buildVisitor() {
        addVisit(Kind.WHILE_STMT, this::visitWhile);
        addVisit(Kind.IF_STMT, this::visitIf);

    }

    private Void visitWhile(JmmNode whileStmt, SymbolTable symbolTable) {
        JmmNode whileCondition = whileStmt.getChild(0);
        Type conditionType = getExprType(whileCondition);
        if (Objects.equals(conditionType.getName(), "boolean"))
            return null;

        // Create error report
        var message = "Expressions in While must return a boolean";
        addReport(Report.newError(
                Stage.SEMANTIC,
                whileStmt.getLine(),
                whileStmt.getColumn(),
                message,
                null)
        );

        return null;
    }

    private Void visitIf(JmmNode ifStmt, SymbolTable symbolTable) {
        JmmNode ifCondition = ifStmt.getChild(0);
        Type conditionType = getExprType(ifCondition);
        if (Objects.equals(conditionType.getName(), "boolean"))
            return null;

        // Create error report
        var message = "Expressions in If must return a boolean";
        addReport(Report.newError(
                Stage.SEMANTIC,
                ifStmt.getLine(),
                ifStmt.getColumn(),
                message,
                null)
        );

        return null;
    }

}
