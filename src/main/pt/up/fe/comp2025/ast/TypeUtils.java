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


    private final JmmSymbolTable table;

    public TypeUtils(SymbolTable table) {
        this.table = (JmmSymbolTable) table;
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
        if (Objects.equals(operator, "!")) return new Type("boolean", false);
        else throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + unaryExpr + "'");
    }

    private static Type getBinaryExprType(JmmNode binaryExpr) {
        String operator = binaryExpr.get("op");
        switch (operator) {
            case "*", "+", "-", "/": return new Type("int", false);
            case "<", "&&": return new Type("boolean", false);
            default: throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + binaryExpr + "'");
        }
    }

    private static Type getArrayCreation(JmmNode expr) {
        String type = expr.get("type");
        return new Type(type, true);
    }


    public static Type convertType(JmmNode typeNode) {

        // TODO: When you support new types, this must be updated
        var name = typeNode.get("name");
        var isArray = false;

        return new Type(name, isArray);
    }


    /**
     * Gets the {@link Type} of an arbitrary expression.
     *
     * @param expr
     * @return
     */
    public Type getExprType(JmmNode expr) {

        // TODO: Update when there are new types
        if (expr.hasAttribute("type")) {
            return expr.getObject("type", Type.class);
        }
        else {
            Kind kind = Kind.fromString(expr.get("kind"));
            Type type = switch (kind) {
                case PROGRAM -> null;
                case CLASS_DECL -> null;
                case VAR_DECL -> null;
                case TYPE -> null;
                case METHOD_DECL -> null;
                case PARAM -> null;
                case STMT -> null;
                case ASSIGN_STMT -> null;
                case RETURN_STMT -> null;
                case EXPR -> null;
                case BINARY_EXPR -> getBinaryExprType(expr);
                case INTEGER_LITERAL -> newIntType();
                case VAR_REF_EXPR -> null;
                case PARAM_LIST -> null;
                case ELSEIF_STMT -> null;
                case ELSE_STMT -> null;
                case IMPORT_DECL -> null;
                case IF_STMT -> null;
                case WHILE_STMT -> null;
                case FOR_STMT -> null;
                case SIMPLE_EXPR -> null;
                case BRACKETS_STMT -> null;
                case PRIORITY_EXPR -> new Type("int", false);
                case UNARY_EXPR -> getUnaryExprType(expr);
                case ARRAY_ACCESS -> null;
                case ARRAY_LITERAL -> null;
                case LENGTH_EXPR -> null;
                case CLASS_FUNCTION_EXPR -> null;
                case LABEL -> null;
                case ARRAY_CREATION -> getArrayCreation(expr);
                case NEW -> null;
                case BOOL_LITERAL -> newBooleanType();
                case OBJECT_REFERENCE -> null;
                case INCREMENT_BY_ONE -> null;
            };
            return type;
        }
    }



}
