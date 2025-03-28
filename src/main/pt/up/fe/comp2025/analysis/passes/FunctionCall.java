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

public class FunctionCall extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.CLASS_FUNCTION_EXPR, this::visitFunctionCall);
    }


    private Void visitFunctionCall(JmmNode funcCall, SymbolTable table){

        JmmNode object = funcCall.getChild(0);
        String funcName = funcCall.get("name");
        Type objectType = getExprType(object);



        JmmNode method = funcCall.getParent();

        if(Kind.SIMPLE_EXPR.check(method))
            method = method.getParent();

        if(Kind.VAR_REF_EXPR.check(object)){

            objectType = varType(object,method,table);
        }

        List<String> imports = table.getImports();

        if(imports.contains(object.get("name")))
            return null;


        if(imports.contains(objectType.getName()) || objectType.getName().equals("imported"))
            return null;


        List<Symbol> funcParams = table.getParameters(funcName);

        if(funcParams == null){
            var message = String.format("Call to undeclared function '%s'.", funcName);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    funcCall.getLine(),
                    funcCall.getColumn(),
                    message,
                    null)
            );
            return null;
        }

        if(funcParams.size() > funcCall.getNumChildren()-1){
            var message = String.format("Function '%s' takes '%d' parameters '%d' provided.", funcName,funcParams.size(),funcCall.getNumChildren()-1);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    funcCall.getLine(),
                    funcCall.getColumn(),
                    message,
                    null)
            );
        }

        for(int i=0; i<funcParams.size();i++){
            Type passedParamType = varType(funcCall.getChild(i+1),method,table);
            if(!funcParams.get(i).getType().equals(passedParamType)){
                var message = "At least one parameter does not match function definition.";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        funcCall.getLine(),
                        funcCall.getColumn(),
                        message,
                        null)
                );
            }
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
