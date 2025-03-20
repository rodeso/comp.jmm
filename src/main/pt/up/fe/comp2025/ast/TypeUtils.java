package pt.up.fe.comp2025.ast;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.symboltable.JmmSymbolTable;

import java.util.Objects;

/**
 * Utility methods regarding types.
 */
public class TypeUtils {

    private final SymbolTable table;

    public TypeUtils(SymbolTable table) {
        this.table = table;
    }
    public static Type newIntType() {
        return new Type("int", false);
    }

    public static Type newFloatType() {
        return new Type("float", false);
    }

    public static Type newStringType() {
        return new Type("String", false);
    }

    public static Type newBooleanType() {
        return new Type("boolean", false);
    }

    public static Type newDoubleType() {
        return new Type("double", false);
    }

    private static Type getUnaryExprType(JmmNode unaryExpr) {
        String operator = unaryExpr.get("op");
        if (Objects.equals(operator, "!")) {
            return new Type("boolean", false);
        }
        throw new RuntimeException("Unknown unary operator '" + operator + "' in expression '" + unaryExpr + "'");
    }

    /**
     * Returns the type for a binary expression by checking the operator and the types of the operands.
     */
    private static Type getBinaryExprType(JmmNode binaryExpr) {
        String operator = binaryExpr.get("op");

        switch (operator) {
            case "+":
            case "-":
            case "*":
            case "/":
                return new Type("int", false);
            case "<":
            case "&&":
                return new Type("boolean", false);
            default:
                throw new RuntimeException("Unknown binary operator '" + operator + "' in expression '" + binaryExpr + "'");
        }
    }

    /**
     * Converts a type node from the AST to a {@link Type}.
     * Assumes the type node has a "name" attribute for the base type and an optional "array"
     * attribute (as a boolean string) indicating whether it is an array.
     */
    public static Type convertType(JmmNode typeNode) {
        String name = typeNode.get("name");
        boolean isArray = false;
        if (typeNode.hasAttribute("array")) {
            isArray = Boolean.parseBoolean(typeNode.get("array"));
        }
        return new Type(name, isArray);
    }

    /**
     * Determines the type of an arbitrary expression.
     */
    public static Type getExprType(JmmNode expr) {
        if (expr.hasAttribute("type")) {
            return expr.getObject("type", Type.class);
        }

        String kind = expr.get("kind");
        switch (kind) {
            case "BINARY_EXPR":
                return getBinaryExprType(expr);
            case "UNARY_EXPR":
                return getUnaryExprType(expr);
            case "INTEGER_LITERAL":
                return newIntType();
            case "BOOL_LITERAL":
                return newBooleanType();
            case "ARRAY_CREATION":
                return getArrayCreation(expr);
            case "ARRAY_ACCESS":
                return getArrayElementType(expr);
            case "CLASS_FUNCTION_EXPR":
                return getFunctionCallType(expr);
            case "PRIORITY_EXPR":
                // Return the type of the enclosed expression
                return getExprType(expr.getChild(0));
            default:
                return null;
        }
    }

    /**
     * Handles the type for array creation expressions.
     * Assumes the node has an attribute "type" with the base type.
     */
    private static Type getArrayCreation(JmmNode expr) {
        String type = expr.get("type");
        return new Type(type, true);
    }

    /**
     * Handles array access expressions.
     * Returns the element type by removing the array dimension.
     */
    private static Type getArrayElementType(JmmNode arrayAccess) {
        JmmNode arrayExpr = arrayAccess.getChild(0);
        Type arrayType = getExprType(arrayExpr);
        if (!arrayType.isArray()) {
            throw new RuntimeException("Attempting to access a non-array type: " + arrayType.getName());
        }
        return new Type(arrayType.getName(), false);
    }

    /**
     * Resolves the return type function call by querying the symbol table.
     */
    private static Type getFunctionCallType(JmmNode functionCall) {
        String methodName = functionCall.get("name");
        return new Type("int", false);
    }
}
