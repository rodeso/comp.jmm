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

public class ArithmeticOperation extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.UNARY_EXPR, this::visitUnaryExpr);
        addVisit(Kind.BINARY_EXPR, this::visitBinaryExpr);
    }

    private Void visitBinaryExpr(JmmNode binaryExpr, SymbolTable table) {
        TypeUtils typeUtils = new TypeUtils(table); // Instanciar
        JmmNode expr1 = binaryExpr.getChild(0);
        JmmNode expr2 = binaryExpr.getChild(1);
        JmmNode method = TypeUtils.getParentMethod(binaryExpr); // Obter contexto

        var op = binaryExpr.get("op");

        // Usar método não estático
        Type typeExpr1 = typeUtils.getExprTypeNotStatic(expr1, method);
        Type typeExpr2 = typeUtils.getExprTypeNotStatic(expr2, method);

        Type expectedOperandType;
        Type resultType;
        boolean isArithmetic = op.equals("+") || op.equals("-") || op.equals("*") || op.equals("/");
        boolean isComparison = op.equals("<"); // Adicionar ==, != etc. se necessário
        boolean isLogical = op.equals("&&"); // Adicionar || se necessário

        if (isArithmetic) {
            expectedOperandType = TypeUtils.newIntType();
            resultType = TypeUtils.newIntType();
        } else if (isComparison) {
            expectedOperandType = TypeUtils.newIntType();
            resultType = TypeUtils.newBooleanType();
        } else if (isLogical) {
            expectedOperandType = TypeUtils.newBooleanType();
            resultType = TypeUtils.newBooleanType();
        } else {
            addReport(newError(binaryExpr, "Unsupported binary operator '" + op + "'."));
            return null;
        }

        // Verificar tipo do operando esquerdo
        if (typeExpr1 == null) {
            addReport(newError(expr1, "Could not determine type for left operand."));
        } else if (typeExpr1.isArray() || !typeExpr1.getName().equals(expectedOperandType.getName())) {
            // Permitir tipos importados? Pode ser perigoso sem mais verificações.
            // Ignorar verificação para tipos importados por agora.
            boolean isImported = typeExpr1.hasAttribute("imported") && typeExpr1.getObject("imported", Boolean.class);
            if (!isImported) {
                addReport(newError(expr1, "Operator '" + op + "' cannot be applied to type '" + typeExpr1.print() + "'. Expected '" + expectedOperandType.getName() + "'."));
            }
        }

        // Verificar tipo do operando direito
        if (typeExpr2 == null) {
            addReport(newError(expr2, "Could not determine type for right operand."));
        } else if (typeExpr2.isArray() || !typeExpr2.getName().equals(expectedOperandType.getName())) {
            boolean isImported = typeExpr2.hasAttribute("imported") && typeExpr2.getObject("imported", Boolean.class);
            if (!isImported) {
                addReport(newError(expr2, "Operator '" + op + "' cannot be applied to type '" + typeExpr2.print() + "'. Expected '" + expectedOperandType.getName() + "'."));
            }
        }

        // Anotar o nó com o tipo de resultado (se os operandos foram minimamente válidos)
        if (typeExpr1 != null && typeExpr2 != null) {
            binaryExpr.putObject("type", resultType);
        }

        return null;
    }

    private Void visitUnaryExpr(JmmNode unaryExpr, SymbolTable table) {
        TypeUtils typeUtils = new TypeUtils(table); // Instanciar
        JmmNode expr = unaryExpr.getChild(0);
        JmmNode method = TypeUtils.getParentMethod(unaryExpr); // Obter contexto
        String op = unaryExpr.get("op");

        // Usar método não estático
        Type typeExpr = typeUtils.getExprTypeNotStatic(expr, method);

        Type expectedOperandType;
        Type resultType;

        if (op.equals("!")) {
            expectedOperandType = TypeUtils.newBooleanType();
            resultType = TypeUtils.newBooleanType();
        } else {
            addReport(newError(unaryExpr, "Unsupported unary operator '" + op + "'."));
            return null;
        }

        if (typeExpr == null) {
            addReport(newError(expr, "Could not determine type for operand."));
        } else if (typeExpr.isArray() || !typeExpr.getName().equals(expectedOperandType.getName())) {
            addReport(newError(expr, "Operator '" + op + "' cannot be applied to type '" + typeExpr.print() + "'. Expected '" + expectedOperandType.getName() + "'."));
        }

        // Anotar o nó com o tipo de resultado (se o operando foi minimamente válido)
        if (typeExpr != null) {
            unaryExpr.putObject("type", resultType);
        }

        return null;
    }

}
