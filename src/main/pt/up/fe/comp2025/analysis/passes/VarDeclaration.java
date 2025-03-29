package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;

import java.util.List;

public class VarDeclaration extends AnalysisVisitor {
    @Override
    public void buildVisitor() {
        addVisit(Kind.VAR_DECL, this::visitVarDecl);
    }

    private Void visitVarDecl(JmmNode varDecl, SymbolTable table){
        JmmNode parent = varDecl.getParent();

        if(Kind.METHOD_DECL.check(parent)){
            List<Symbol> locals = table.getLocalVariables(parent.get("name"));
            for(Symbol symbol : locals){
                if(symbol.getName().equals(varDecl.get("name"))){
                    var message = "Expressions in If must return a boolean";
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            varDecl.getLine(),
                            varDecl.getColumn(),
                            message,
                            null)
                    );
                }
            }
        }

        if(Kind.CLASS_DECL.check(parent)){
            List<Symbol> fields = table.getFields();
            for(Symbol symbol: fields){
                if(symbol.getName().equals(varDecl.get("name"))){
                    var message = "Duplicated field";
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            varDecl.getLine(),
                            varDecl.getColumn(),
                            message,
                            null)
                    );
                }
            }
        }
        return null;
    }
}
