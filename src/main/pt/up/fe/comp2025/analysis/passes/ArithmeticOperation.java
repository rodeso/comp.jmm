package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;


import java.util.List;
import java.util.Objects;

import static pt.up.fe.comp2025.ast.TypeUtils.getExprType;

public class ArithmeticOperation extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.UNARY_EXPR, this::visitUnaryExpr);
        addVisit(Kind.BINARY_EXPR, this::visitBinaryExpr);
    }

    private Void visitBinaryExpr(JmmNode binaryExpr, SymbolTable table) {
        TypeUtils typeUtils = new TypeUtils(table);
        JmmNode expr1 = binaryExpr.getChild(0);
        JmmNode expr2 = binaryExpr.getChild(1);

        Type opType = getExprType(binaryExpr);
        var op = binaryExpr.get("op");
        binaryExpr.putObject("type", opType);


        Type typeExpr1 = typeUtils.getExprTypeNotStatic(expr1,binaryExpr.getParent());
        Type typeExpr2 = typeUtils.getExprTypeNotStatic(expr2,binaryExpr.getParent());

//        if(Kind.VAR_REF_EXPR.check(expr1)){
//            typeExpr1 = varType(expr1, table);
//        }
//
//        if(Kind.VAR_REF_EXPR.check(expr2)){
//            typeExpr2 = varType(expr2, table);
//        }
        if (op.equals("+") || op.equals("-") || op.equals("*") || op.equals("/")) {

            assert typeExpr1 != null;
            if (!typeExpr1.equals(typeExpr2) && !typeExpr1.getName().equals("boolean")) {
                String message = String.format("Operator '%s' not applicable to type '%s'", op, typeExpr1);
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        binaryExpr.getLine(),
                        binaryExpr.getColumn(),
                        message,
                        null)
                );
            }
        }
        if (!typeExpr1.equals(opType) && !(Objects.equals(op, "<") && typeExpr1.getName().equals("int")) &&
                !typeExpr1.getName().equals("imported")) {
            String message = String.format("Operator '%s' not applicable to type '%s'", op, typeExpr1);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    binaryExpr.getLine(),
                    binaryExpr.getColumn(),
                    message,
                    null)
            );
        }
        if (!typeExpr2.equals(opType) && !(Objects.equals(op, "<") && typeExpr2.getName().equals("int")) &&
                !typeExpr2.getName().equals("imported")) {
            String message = String.format("Operator '%s' not applicable to type '%s'", op, typeExpr2);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    binaryExpr.getLine(),
                    binaryExpr.getColumn(),
                    message,
                    null)
            );
        }


        return null;
    }

    private Void visitUnaryExpr(JmmNode unaryExpr, SymbolTable table) {
        JmmNode expr = unaryExpr.getChild(0);

        Type opType = getExprType(unaryExpr);
        Type typeExpr = getExprType(expr);

        if(Kind.VAR_REF_EXPR.check(expr)){
            typeExpr = varType(expr, table);
        }

        unaryExpr.putObject("type", opType);

        if (!typeExpr.equals(opType)) {
            String message = String.format("Operator '%s' not applicable to type '%s'", unaryExpr.get("op"), typeExpr);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    expr.getLine(),
                    expr.getColumn(),
                    message,
                    null)
            );
        }

        return null;
    }

    private Type varType(JmmNode varRefNode, SymbolTable symbolTable){
        Type type = null;
        JmmNode method = varRefNode.getParent().getParent();
        String methodName = method.get("name");

        List<Symbol> varsFromMethod = symbolTable.getLocalVariables(methodName);

        for(Symbol symbol : varsFromMethod){
            if(symbol.getName().equals(varRefNode.get("name"))){
                type = symbol.getType();
            }
        }

        List<Symbol> fields = symbolTable.getFields();

        for(Symbol symbol : fields){
            if(symbol.getName().equals(varRefNode.get("name"))){
                type = symbol.getType();
            }
        }

        List<Symbol> params = symbolTable.getParameters(methodName);

        if(params != null){
            for(Symbol symbol : params){
                if(symbol.getName().equals(varRefNode.get("name"))){
                    type = symbol.getType();
                }
            }
        }

        return type;
    }
}
