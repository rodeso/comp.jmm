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

        return switch (operator) {
            case "+", "-", "*", "/" -> new Type("int", false);
            case "<", "&&" -> new Type("boolean", false);
            default ->
                    throw new RuntimeException("Unknown binary operator '" + operator + "' in expression '" + binaryExpr + "'");
        };
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
        else {
            Kind kind = Kind.fromString(expr.getKind());
            return switch (kind) {
                case BINARY_EXPR -> getBinaryExprType(expr);
                case UNARY_EXPR -> getUnaryExprType(expr);
                case INTEGER_LITERAL -> newIntType();
                case BOOLEAN_LITERAL -> newBooleanType();
                case ARRAY_CREATION -> getArrayCreation(expr);
                case ARRAY_ACCESS -> getArrayElementType(expr);
                case CLASS_FUNCTION_EXPR -> getFunctionCallType(expr);
                case PRIORITY_EXPR -> getExprType(expr.getChild(0));
                case VAR_REF_EXPR -> getVarExprType(expr);
                case ARRAY_LITERAL -> getArrayLiteral(expr);
                default -> throw new UnsupportedOperationException("Can't compute type for expression kind '" + kind + "'");
            };
        }
    }

    private static Type getVarExprType(JmmNode expr) {
        // this is so hard im just going to skip for now
        return new Type(expr.get("name"), false);
    }

    public static Type storeType(Type type, SymbolTable table) {
        if (type.getName().equals(table.getClassName())) {
            type.putObject("super", table.getSuper());
        }

        if (table.getImports().contains(type.getName())) {
            type.putObject("imported", true);
        }

        return type;
    }


    /**
     * Handles the type for array creation expressions.
     * Assumes the node has an attribute "type" with the base type.
     */
    private static Type getArrayCreation(JmmNode expr) {
        // in array creation the first child is always type and type only has one child that is the baseType
        String type = expr.getChild(0).getChild(0).get("name");
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

    private static Type getArrayLiteral(JmmNode arrayLiteral){
        return new Type("int",true);
    }

    public static boolean isAssignable(Type sourceType, Type destinationType) {
        if (sourceType.equals(destinationType)) {
            return true;
        } else if (sourceType.hasAttribute("super") && (sourceType.getObject("super").equals(destinationType.getName())))
            return true;
        return sourceType.hasAttribute("imported") && destinationType.hasAttribute("imported");
    }

    public  static boolean isAssignableByImport(Type sourceType, Type destinationType, SymbolTable symbolTable){
        if(symbolTable.getImports().contains(sourceType.getName()) && symbolTable.getImports().contains(destinationType.getName())){
            return true;
        }

        return false;
    }
}
