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

import static pt.up.fe.comp2025.ast.TypeUtils.getExprType;

public class MethodDeclaration extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
    }


    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table){
        if(methodDecl.get("name").equals("main"))
            return null;
        JmmNode returnExpr = methodDecl.getChild(methodDecl.getNumChildren()-1);

        Type type = table.getReturnType(methodDecl.get("name"));
        Type returnType = getExprType(returnExpr);

        if(Kind.VAR_REF_EXPR.check(returnExpr)){
            returnType = varType(returnExpr,table);
        }

        if(!type.equals(returnType)){
            var message = String.format("Method of type '%s' can not return %s.",type,returnType.toString());
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    methodDecl.getLine(),
                    methodDecl.getColumn(),
                    message,
                    null)
            );
        }



        return null;
    }


    private Type varType(JmmNode varRefNode, SymbolTable symbolTable){
        Type type = null;
        JmmNode method = varRefNode.getParent();
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
