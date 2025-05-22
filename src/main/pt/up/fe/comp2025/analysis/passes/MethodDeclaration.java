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
import java.util.stream.Collectors;

public class MethodDeclaration extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
    }

    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table){
        TypeUtils typeUtils = new TypeUtils(table);
        String methodName = methodDecl.get("name");

        long methodsWithSameName = table.getMethods().stream().filter(name -> name.equals(methodName)).count();
        if(methodsWithSameName > 1){
            addReport(newError(methodDecl, "Method '" + methodName + "' is defined more than once."));
        }

        List<Symbol> parameters = table.getParameters(methodName);
        if (parameters != null) {
            Map<String, Long> parameterCounts = parameters.stream()
                    .collect(Collectors.groupingBy(Symbol::getName, Collectors.counting()));

            parameterCounts.forEach((paramName, count) -> {
                if (count > 1) {
                    addReport(newError(methodDecl, "Parameter '" + paramName + "' is duplicated in method '" + methodName + "'."));
                }
            });

            for(int i = 0; i < parameters.size() - 1; i++){
                if(parameters.get(i).getType().getName().endsWith("...")){
                    addReport(newError(methodDecl, "Varargs parameter must be the last one in method '" + methodName + "'."));
                    break;
                }
            }
        }

        Type expectedReturnType = table.getReturnType(methodName);
        if (expectedReturnType != null && expectedReturnType.getName().endsWith("...")) {
            addReport(newError(methodDecl, "Method '" + methodName + "' cannot return a varargs type."));
        }
        if(methodDecl.hasAttribute("s") && !methodName.equals("main")){
            addReport(newError(methodDecl, "Only main method can be static"));
        }
        if(methodName.equals("main")) {
            String argsType = methodDecl.getOptional("sArgs").orElse("");
            if(!argsType.equals("String") && methodDecl.hasAttribute("s")){
                addReport(newError(methodDecl, "Main method must have String as argument type"));
            }

            return null;
        }

        List<JmmNode> returnStmts = methodDecl.getChildren(Kind.RETURN_STMT);

        if (returnStmts.isEmpty() && (expectedReturnType != null && !expectedReturnType.getName().equals("void"))) {
            addReport(newError(methodDecl, "Missing return statement in non-void method '" + methodName + "'."));
            return null;
        }
        if (!returnStmts.isEmpty() && expectedReturnType != null && expectedReturnType.getName().equals("void")) {
            for (JmmNode returnStmt : returnStmts) {
                if (returnStmt.getNumChildren() > 0) {
                    addReport(newError(returnStmts.get(0), "Return statement with value found in void method '" + methodName + "'."));
                }
            }
        }

        for (JmmNode returnStmt : returnStmts) {
            if (returnStmt.getNumChildren() == 0) {
                if (expectedReturnType != null && !expectedReturnType.getName().equals("void")) {
                    addReport(newError(returnStmt, "Return statement in method '" + methodName + "' must return a value of type '" + expectedReturnType.print() + "'."));
                }
                continue;
            }

            JmmNode returnExpr = returnStmt.getChild(0);
            Type actualReturnType = typeUtils.getExprTypeNotStatic(returnExpr, methodDecl);

            if (actualReturnType == null) {
                addReport(newError(returnExpr, "Could not determine type of return expression in method '" + methodName + "'."));
                continue;
            }

            // <<< CORREÇÃO FINAL: Ignorar verificação se tipo atual for o placeholder >>>
            if (actualReturnType.getName().equals("imported_or_unknown")) {
                System.out.println("Skipping return type check for expression resulting in unknown/imported type in " + methodName);
                continue; // Assume que está correto se não conseguimos determinar o tipo real
            }

            if (expectedReturnType == null) {
                addReport(newError(methodDecl, "Internal error: Could not find expected return type for method '" + methodName + "'."));
                continue;
            }

            if (!TypeUtils.isAssignable(actualReturnType, expectedReturnType)) {
                addReport(newError(returnStmt, "Incompatible return type in method '" + methodName + "'. Expected '" + expectedReturnType.print() + "' but found '" + actualReturnType.print() + "'."));
            }
        }

        return null;
    }
}
