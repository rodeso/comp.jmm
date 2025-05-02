package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

public class LengthProperty extends AnalysisVisitor {
    public void buildVisitor() {
        addVisit(Kind.LENGTH_EXPR, this::visitLength);
    }

    private Void visitLength(JmmNode lengthExpr, SymbolTable symbolTable){
        JmmNode caller = lengthExpr.getChild(0);
        TypeUtils typeUtils = new TypeUtils(symbolTable);
        JmmNode parentMethod = TypeUtils.getParentMethod(caller);
        Type type = typeUtils.getExprTypeNotStatic(caller,parentMethod);

        if(!type.isArray()){
            addReport(newError(lengthExpr, "Length property can only be called for arrays"));
        }
        return null;
    }
}
