package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;

import java.util.HashSet;
import java.util.Set;

/**
 * Reports an error if the same import is declared more than once.
 */
public class ImportCheck extends AnalysisVisitor {

    private final Set<String> seenImports = new HashSet<>();

    @Override
    public void buildVisitor() {
        addVisit(Kind.IMPORT_DECL, this::visitImport);
    }

    private Void visitImport(JmmNode importNode, SymbolTable table) {
        String name = importNode.get("name");
        if (seenImports.contains(name)) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    importNode.getLine(),
                    importNode.getColumn(),
                    "Duplicate import '" + name + "'.",
                    null
            ));
        } else {
            seenImports.add(name);
        }
        return null;
    }
} 