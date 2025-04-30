package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils; // Import normal

import java.util.List;
import java.util.Objects;

// REMOVIDA importação estática

public class ArrayInit extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.ARRAY_ACCESS, this::visitArrayAccess);
        addVisit(Kind.ARRAY_LITERAL, this::visitArrayLiteral);
        addVisit(Kind.ARRAY_CREATION, this::visitArrayCreation);
    }

    private Void visitArrayCreation(JmmNode array, SymbolTable symbolTable) {
        JmmNode arraySize = array.getChild(1);
        TypeUtils typeUtils = new TypeUtils(symbolTable); // Instanciar
        JmmNode method = TypeUtils.getParentMethod(array); // Obter contexto

        // Usar método não estático
        Type arraySizeType = typeUtils.getExprTypeNotStatic(arraySize, method);

        if (arraySizeType == null) {
            addReport(newError(arraySize, "Could not determine type for array size expression."));
            return null;
        }

        // Verificar se o tipo é int e não array
        if (!arraySizeType.getName().equals("int") || arraySizeType.isArray()) {
            var message = "Array size must be a non-array integer expression. Found: " + arraySizeType.print();
            addReport(newError(arraySize, message));
        }
        return null;
    }

    private Void visitArrayLiteral(JmmNode array, SymbolTable symbolTable) {
        List<JmmNode> arrayElements = array.getChildren();
        TypeUtils typeUtils = new TypeUtils(symbolTable); // Instanciar
        JmmNode method = TypeUtils.getParentMethod(array); // Obter contexto

        if (!arrayElements.isEmpty()){
            for (JmmNode arrayElement : arrayElements) {
                // Usar método não estático
                Type currentElementType = typeUtils.getExprTypeNotStatic(arrayElement, method);

                if (currentElementType == null) {
                    addReport(newError(arrayElement, "Could not determine type for array element."));
                    continue;
                }
                // Verificar se o elemento é int e não array
                if (!currentElementType.getName().equals("int") || currentElementType.isArray()) {
                    var message = "All array literal elements must be of type 'int'. Found: " + currentElementType.print();
                    addReport(newError(arrayElement, message));
                }
            }
        }
        return null;
    }

    private Void visitArrayAccess(JmmNode array, SymbolTable table) {
        JmmNode expr = array.getChild(0); // Expressão do array
        JmmNode index = array.getChild(1); // Expressão do índice
        TypeUtils typeUtils = new TypeUtils(table); // Instanciar
        JmmNode method = TypeUtils.getParentMethod(array); // Obter contexto

        // Usar método não estático para obter tipo do array
        Type exprType = typeUtils.getExprTypeNotStatic(expr, method);

        if (exprType == null) {
            addReport(newError(expr, "Could not determine type for array variable/expression."));
        } else if (!exprType.isArray()) {
            // Permitir acesso a varargs (int...) como se fosse array
            if (!exprType.getName().equals("int...")) {
                String message = String.format("Attempting array access '[]' on non-array type '%s'.", exprType.print());
                addReport(newError(expr, message));
            }
        }

        // Usar método não estático para obter tipo do índice
        Type indexType = typeUtils.getExprTypeNotStatic(index, method);

        if (indexType == null) {
            addReport(newError(index, "Could not determine type for index expression."));
        } else if (!indexType.getName().equals("int") || indexType.isArray()) {
            String message = String.format("Array index must be a non-array integer expression. Found type: '%s'.", indexType.print());
            addReport(newError(index, message));
        }

        return null;
    }

}
