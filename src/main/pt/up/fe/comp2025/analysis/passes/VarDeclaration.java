package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class VarDeclaration extends AnalysisVisitor {
    @Override
    public void buildVisitor() {
        addVisit(Kind.VAR_DECL, this::visitVarDecl);
    }

    private Void visitVarDecl(JmmNode varDecl, SymbolTable table){
        JmmNode parent = varDecl.getParent();

        if(Kind.METHOD_DECL.check(parent)){
            List<Symbol> locals = table.getLocalVariables(parent.get("name"));

            Map<String, Long> localsCount = locals.stream()
                    .collect(Collectors.groupingBy(Symbol::getName, Collectors.counting()));
            for(Map.Entry<String,Long> local : localsCount.entrySet()){
                if(local.getValue() > 1){
                    var message = String.format("Local variable '%s' is duplicated",local.getKey());
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
            Map<String, Long> fieldsCount = fields.stream()
                    .collect(Collectors.groupingBy(Symbol::getName, Collectors.counting()));
            for(Map.Entry<String,Long> field : fieldsCount.entrySet()){
                if(field.getValue() > 1){
                    var message = String.format("Field '%s' is duplicated",field.getKey());
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
