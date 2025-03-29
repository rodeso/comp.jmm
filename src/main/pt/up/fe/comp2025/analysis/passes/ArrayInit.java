package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
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

public class ArrayInit extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.ARRAY_ACCESS, this::visitArrayAccess);
        addVisit(Kind.ARRAY_LITERAL, this::visitArrayLiteral);
        addVisit(Kind.ARRAY_CREATION, this::visitArrayCreation);
    }

    private Void visitArrayCreation(JmmNode array, SymbolTable symbolTable) {
        JmmNode arraySize = array.getChild(1);
        Type arraySizeType = getExprType(arraySize);

        JmmNode method = array.getParent().getParent();//Array creation is inside assignment
        if(Kind.VAR_REF_EXPR.check(arraySize))
            arraySizeType = varType(arraySize,method,symbolTable);

        if(!arraySizeType.equals(new Type("int",false))){
            var message = "Array size must be int";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    array.getLine(),
                    array.getColumn(),
                    message,
                    null)
            );
        }


        return null;
    }

    private Void visitArrayLiteral(JmmNode array, SymbolTable symbolTable) {
        List<JmmNode> arrayElements = array.getChildren();
        if (!arrayElements.isEmpty()){
            for (JmmNode arrayElement : arrayElements)
                if (!Objects.equals(arrayElement.getKind(), "IntegerLiteral")) {
                    // Create error report
                    var message = "All array elements must have 'int' type";
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            array.getLine(),
                            array.getColumn(),
                            message,
                            null)
                    );
                }
        }


        return null;
    }

    private Void visitArrayAccess(JmmNode array, SymbolTable table) {
        JmmNode expr = array.getChild(0);
        JmmNode index = array.getChild(1);
        Type exprType = getExprType(expr);

        JmmNode method = array.getParent();

        if(Kind.ASSIGN_STMT.check(method))
            method = method.getParent();

        if(Kind.VAR_REF_EXPR.check(expr))
            exprType = varType(expr,method,table);



        if (exprType == null) {
        
            String message = String.format("ArrayAccess Error: Null type");
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    array.getLine(),
                    array.getColumn(),
                    message,
                    null)
            );
        }

        if (!exprType.isArray() && !exprType.getName().equals("int...")) {
            String message = String.format("ArrayAccess Error: Not an array");
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    array.getLine(),
                    array.getColumn(),
                    message,
                    null)
            );
        }

        Type indexType = getExprType(index);

        if(Kind.VAR_REF_EXPR.check(index))
            indexType = varType(index,method,table);
        if (indexType == null) {
            String message = String.format("ArrayAccess Error: Null index type");
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    array.getLine(),
                    array.getColumn(),
                    message,
                    null)
            );
        }

        if (!indexType.getName().equals("int")) {
            String message = String.format("ArrayAccess Error: Index is not an integer");
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    array.getLine(),
                    array.getColumn(),
                    message,
                    null)
            );
        }


        return null;
    }


    private Type varType(JmmNode varRefNode,JmmNode method, SymbolTable symbolTable){
        Type type = null;

        String methodName = method.get("name");

        List<Symbol> varsFromMethod = symbolTable.getLocalVariables(methodName);
        if(varsFromMethod != null){
            for(Symbol symbol : varsFromMethod){
                if(symbol.getName().equals(varRefNode.get("name"))){
                    type = symbol.getType();
                }
            }
        }


        List<Symbol> fields = symbolTable.getFields();

        if(fields != null){
            for(Symbol symbol : fields){
                if(symbol.getName().equals(varRefNode.get("name"))){
                    type = symbol.getType();
                }
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
