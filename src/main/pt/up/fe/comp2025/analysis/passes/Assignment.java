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
import java.util.Map;
import java.util.Objects;

import static pt.up.fe.comp2025.ast.Kind.*;
import static pt.up.fe.comp2025.ast.TypeUtils.*;

public class Assignment extends AnalysisVisitor {


    @Override
    public void buildVisitor() {
        addVisit(Kind.ASSIGN_STMT, this::visitAssign);

    }

    private Void visitAssign(JmmNode assignStmt, SymbolTable symbolTable) {
        TypeUtils typeUtils = new TypeUtils(symbolTable);

        JmmNode expr1 = assignStmt.getChild(0);
        JmmNode expr2 = assignStmt.getChild(1);

        //JmmNode method = assignStmt.getParent();
        JmmNode method = TypeUtils.getParentMethod(assignStmt);

        Type typeExpr1 = typeUtils.getExprTypeNotStatic(expr1,method);
        Type typeExpr2 = typeUtils.getExprTypeNotStatic(expr2,method);

//        Type typeExpr1 = getExprType(expr1);
//        Type typeExpr2 = getExprType(expr2);
//
//        if(VAR_REF_EXPR.check(expr1)){
//            typeExpr1 = varType(expr1,method,symbolTable);
//        }
//
//        if(VAR_REF_EXPR.check(expr2)){
//            typeExpr2 = varType(expr2,method,symbolTable);
//        }
//
//        if(ARRAY_ACCESS.check(expr1)){
//            typeExpr1 = varType(expr1.getChild(0),method,symbolTable);
//            typeExpr1 = new Type(typeExpr1.getName(),false);
//        }
//
//        if(ARRAY_ACCESS.check(expr2)){
//            typeExpr2 = varType(expr2.getChild(0),method,symbolTable);
//            typeExpr2 = new Type(typeExpr2.getName(),false);
//        }
//
//
//        if(CLASS_FUNCTION_EXPR.check(expr2)){
//            typeExpr2 = symbolTable.getReturnType(expr2.get("name"));
//        }



        if (isAssignableByImport(typeExpr1,typeExpr2,symbolTable)) {
            assignStmt.putObject("type",storeType(typeExpr1,symbolTable));
            assignStmt.putObject("type",storeType(typeExpr2,symbolTable));
            return null;
        }

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

    private Type varType(JmmNode varRefNode,JmmNode method, SymbolTable symbolTable){
        Type type = null;

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


