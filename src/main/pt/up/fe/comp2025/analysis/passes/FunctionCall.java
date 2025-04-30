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
import java.util.ArrayList;

public class FunctionCall extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.CLASS_FUNCTION_EXPR, this::visitFunctionCall);
    }

    private Void visitFunctionCall(JmmNode funcCall, SymbolTable table){
        TypeUtils typeUtils = new TypeUtils(table);

        JmmNode object = funcCall.getChild(0);
        String funcName = funcCall.get("name");
        JmmNode methodContext = TypeUtils.getParentMethod(funcCall);

        Type callerType = typeUtils.getExprTypeNotStatic(object, methodContext);

        if (callerType == null) {
            if (object.getKind().equals("VarRefExpr") && table.getImports().contains(object.get("name"))) {
                System.out.println("Assuming call to imported static method '" + funcName + "' is correct.");
                return null;
            } else {
                addReport(newError(object, "Could not determine type of caller for method '" + funcName + "'."));
                return null;
            }
        }

        boolean isImportedCall = table.getImports().contains(callerType.getName());

        if (isImportedCall) {
            System.out.println("Assuming call to imported method '" + funcName + "' on type '" + callerType.getName() + "' is correct.");
            return null;
        }

        boolean methodExists = table.getMethods().contains(funcName);
        boolean isSuperCall = false;
        if (!methodExists && table.getSuper() != null && !table.getSuper().isEmpty()) {
            System.out.println("Assuming method '" + funcName + "' exists in superclass '" + table.getSuper() + "'.");
            isSuperCall = true;
        } else if (!methodExists) {
            addReport(newError(funcCall, "Method '" + funcName + "' not declared in class '" + table.getClassName() + "' or its known superclasses."));
            return null;
        }

        if (isSuperCall) {
            return null;
        }

        List<Symbol> expectedParams = table.getParameters(funcName);
        if (expectedParams == null) {
            addReport(newError(funcCall, "Internal error: Parameters not found for existing method '" + funcName + "'."));
            return null;
        }

        List<JmmNode> passedArgs = new ArrayList<>();
        for (int i = 1; i < funcCall.getNumChildren(); i++) {
            passedArgs.add(funcCall.getChild(i));
        }

        boolean isVarArgs = !expectedParams.isEmpty() &&
                expectedParams.getLast().getType().getName().endsWith("...");

        if (isVarArgs) {
            int numFixedParams = expectedParams.size() - 1;
            if (passedArgs.size() < numFixedParams) {
                addReport(newError(funcCall, "Method '" + funcName + "' expects at least " + numFixedParams + " arguments, but got " + passedArgs.size() + "."));
                return null;
            }

            for (int i = 0; i < numFixedParams; i++) {
                Type expectedType = expectedParams.get(i).getType();
                Type passedType = typeUtils.getExprTypeNotStatic(passedArgs.get(i), methodContext);
                if (passedType == null) {
                    addReport(newError(passedArgs.get(i), "Could not determine type for argument " + (i+1) + "."));
                    continue;
                }
                if (!TypeUtils.isAssignable(passedType, expectedType)) {
                    addReport(newError(passedArgs.get(i), "Argument " + (i+1) + " type mismatch. Expected '" + expectedType.print() + "' but got '" + passedType.print() + "'."));
                }
            }

            Type varArgsBaseType = new Type(expectedParams.getLast().getType().getName().replace("...", ""), false);
            for (int i = numFixedParams; i < passedArgs.size(); i++) {
                Type passedType = typeUtils.getExprTypeNotStatic(passedArgs.get(i), methodContext);
                if (passedType == null) {
                    addReport(newError(passedArgs.get(i), "Could not determine type for varargs argument " + (i+1) + "."));
                    continue;
                }

                boolean passedArrayMatches = passedType.isArray() && passedType.getName().equals(varArgsBaseType.getName());

                if (!TypeUtils.isAssignable(passedType, varArgsBaseType) && !passedArrayMatches) {
                    addReport(newError(passedArgs.get(i), "Varargs argument " + (i+1) + " type mismatch. Expected assignable to '" + varArgsBaseType.print() + "' or '" + varArgsBaseType.getName() + "[]' but got '" + passedType.print() + "'."));
                }
            }

        } else {
            if (expectedParams.size() != passedArgs.size()) {
                addReport(newError(funcCall, "Method '" + funcName + "' expects " + expectedParams.size() + " arguments, but got " + passedArgs.size() + "."));
                return null;
            }

            for (int i = 0; i < expectedParams.size(); i++) {
                Type expectedType = expectedParams.get(i).getType();
                Type passedType = typeUtils.getExprTypeNotStatic(passedArgs.get(i), methodContext);
                if (passedType == null) {
                    addReport(newError(passedArgs.get(i), "Could not determine type for argument " + (i+1) + "."));
                    continue;
                }
                if (!TypeUtils.isAssignable(passedType, expectedType)) {
                    addReport(newError(passedArgs.get(i), "Argument " + (i+1) + " type mismatch. Expected '" + expectedType.print() + "' but got '" + passedType.print() + "'."));
                }
            }
        }

        return null;
    }
}
