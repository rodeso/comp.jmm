package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.Objects;

public class ConditionCheck extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.WHILE_STMT, this::visitWhile);
        addVisit(Kind.IF_STMT, this::visitIf);
    }

    private Void visitWhile(JmmNode whileStmt, SymbolTable symbolTable) {
        JmmNode whileCondition = whileStmt.getChild(0);
        TypeUtils typeUtils = new TypeUtils(symbolTable);
        JmmNode method = TypeUtils.getParentMethod(whileStmt);

        Type conditionType = typeUtils.getExprTypeNotStatic(whileCondition, method);

        if (conditionType == null) {
            addReport(newError(whileCondition, "Could not determine type for while condition."));
            return null;
        }

        if (!conditionType.getName().equals("boolean") || conditionType.isArray()) {
            addReport(newError(whileCondition, "While condition must be a non-array boolean expression. Found type: " + conditionType.print()));
        }
        return null;
    }

    private Void visitIf(JmmNode ifStmt, SymbolTable symbolTable) {
        JmmNode ifCondition = ifStmt.getChild(0);
        TypeUtils typeUtils = new TypeUtils(symbolTable);
        JmmNode method = TypeUtils.getParentMethod(ifStmt);

        Type conditionType = typeUtils.getExprTypeNotStatic(ifCondition, method);

        if (conditionType == null) {
            addReport(newError(ifCondition, "Could not determine type for if condition."));
            return null;
        }

        if (!conditionType.getName().equals("boolean") || conditionType.isArray()) {
            addReport(newError(ifCondition, "If condition must be a non-array boolean expression. Found type: " + conditionType.print()));
        }
        return null;
    }
}
