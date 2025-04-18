package pt.up.fe.comp2025.analysis.passes;

import java_cup.runtime.symbol;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;

public class ObjectReference extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.OBJECT_REFERENCE, this::visitObjectReference);

    }

    private Void visitObjectReference(JmmNode object, SymbolTable symbolTable){
        JmmNode objectRef = object.getChild(1);
        JmmNode method = object.getParent();
        String methodName = method.get("name");

        if(Kind.VAR_REF_EXPR.check(objectRef)){
            for( Symbol symbol : symbolTable.getFields()){
                if(symbol.getName().equals(objectRef.get("name"))){
                    return null;
                }
            }
        }

        var message = "That field does not exist.";
        addReport(Report.newError(
                Stage.SEMANTIC,
                object.getLine(),
                object.getColumn(),
                message,
                null)
        );

        // algo para se for func call expecialmente varargs
        return null;
    }
}
