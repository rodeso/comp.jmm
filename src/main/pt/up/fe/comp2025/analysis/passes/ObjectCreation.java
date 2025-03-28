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

public class ObjectCreation extends AnalysisVisitor {


    @Override
    public void buildVisitor() {
        addVisit(Kind.NEW, this::visitNewObject);

    }

    private Void visitNewObject(JmmNode object, SymbolTable symbolTable) {
        var className = object.get("name");

        // Var is an imported class, return
        if (symbolTable.getImports().stream().anyMatch(importClass -> importClass.equals(className))) {
            object.putObject("type", storeType(new Type("imported", false), symbolTable));
            return null;
        }


        if (Objects.equals(symbolTable.getClassName(), className)) {
            object.putObject("type", storeType(new Type(className, false), symbolTable));
            return null;
        }


        var message = "New Object creation with class name that does not exist.";
        addReport(Report.newError(
                Stage.SEMANTIC,
                object.getLine(),
                object.getColumn(),
                message,
                null)
        );

        return null;
    }

}
