package pt.up.fe.comp2025.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.List;
import java.util.Objects;

public class TypeUtils {

    private final SymbolTable table;

    public TypeUtils(SymbolTable table) {
        this.table = table;
    }
    public static Type newIntType() {
        return new Type("int", false);
    }

    public static Type newStringType() {
        return new Type("String", false);
    }

    public static Type newBooleanType() {
        return new Type("boolean", false);
    }

    private static Type getUnaryExprType(JmmNode unaryExpr) {
        String operator = unaryExpr.get("op");
        if (Objects.equals(operator, "!")) {
            return new Type("boolean", false);
        }
        throw new RuntimeException("Unknown unary operator '" + operator + "' in expression '" + unaryExpr + "'");
    }

    private static Type getBinaryExprType(JmmNode binaryExpr) {
        String operator = binaryExpr.get("op");

        return switch (operator) {
            case "+", "-", "*", "/" -> newIntType();
            case "<", "&&" -> newBooleanType();
            default ->
                    throw new RuntimeException("Unknown binary operator '" + operator + "' in expression '" + binaryExpr + "'");
        };
    }

    public static Type convertType(JmmNode typeNode) {
        String name = "";
        boolean isArray = false;
        boolean isVarargs = false;

        if (Kind.TYPE.check(typeNode)) {
            JmmNode baseTypeNode = typeNode.getChild(0);
            if (Kind.BASE_TYPE.check(baseTypeNode)) {
                name = baseTypeNode.get("name");
                if (baseTypeNode.getOptional("args").orElse("").equals("...")) {
                    isVarargs = true;
                    isArray = true;
                }
            } else {
                name = typeNode.getOptional("name").orElse("unknown_base");
                System.err.println("Warning: TYPE node child is not BASE_TYPE: " + baseTypeNode.getKind());
            }
            String op1 = typeNode.getOptional("op1").orElse("");
            String op2 = typeNode.getOptional("op2").orElse("");
            if (op1.equals("[") && op2.equals("]")) {
                isArray = true;
            }
        } else if (Kind.BASE_TYPE.check(typeNode)) {
            name = typeNode.get("name");
            if (typeNode.getOptional("args").orElse("").equals("...")) {
                isVarargs = true;
                isArray = true;
            }
        } else {
            throw new IllegalArgumentException("Node is not a valid Type or BaseType node: " + typeNode.getKind());
        }

        // Representar varargs internamente com '...' no nome para distinção se necessário
        if (isVarargs) {
            name += "...";
        }

        return new Type(name, isArray); // isArray será true para varargs também
    }

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
                case ARRAY_CREATION -> getArrayCreationType(expr);
                case ARRAY_ACCESS -> getArrayElementType(expr);
                case PRIORITY_EXPR -> getExprType(expr.getChild(0));
                case ARRAY_LITERAL -> new Type("int", true);
                case LENGTH_EXPR -> newIntType();
                case OBJECT_REFERENCE -> new Type(expr.get("value"), false);
                case VAR_DECL -> getVarDecl(expr);
                case VAR_REF_EXPR, CLASS_FUNCTION_EXPR ->
                        throw new UnsupportedOperationException("Cannot compute static type for '" + kind + "'. Use non-static getExprTypeNotStatic.");
                case RETURN_STMT->
                        throw new IllegalArgumentException("Cannot get expression type for statement kind '" + kind + "'");
                default -> throw new UnsupportedOperationException("Can't compute static type for expression kind '" + kind + "'");
            };
        }
    }

    public Type getExprTypeNotStatic(JmmNode expr, JmmNode method){
        if (expr.hasAttribute("type")) {
            return expr.getObject("type", Type.class);
        }

        Kind kind = Kind.fromString(expr.getKind());

        try {
            return switch (kind){
                case CLASS_FUNCTION_EXPR -> getFunctionCallType(expr, this.table);
                case VAR_REF_EXPR -> varType(expr, method, this.table);
                case ARRAY_ACCESS -> getArrayAccessElementTypeNotStatic(expr, method); // Corrigido aqui
                case OBJECT_REFERENCE -> new Type(this.table.getClassName(), false);
                case PRIORITY_EXPR -> getExprTypeNotStatic(expr.getChild(0), method);
                case LENGTH_EXPR -> newIntType();
                case RETURN_STMT->
                        throw new IllegalArgumentException("Cannot get expression type for statement kind '" + kind + "'");
                default -> getExprType(expr);
            };
        } catch (Exception e) {
            String location = "unknown location";
            if (expr.getLine() > 0 && expr.getColumn() > 0) {
                location = "line " + expr.getLine() + ", col " + expr.getColumn();
            }
            throw new RuntimeException("Error computing type for node '" + expr + "' (" + kind + ") at " + location, e);
        }
    }

    public static Type storeType(Type type, SymbolTable table) {
        if (type == null) return null;

        if (type.getName().equals(table.getClassName())) {
            type.putObject("super", table.getSuper());
        }

        if (table.getImports().contains(type.getName())) {
            type.putObject("imported", true);
        }

        return type;
    }

    private static Type getArrayCreationType(JmmNode arrayCreationNode) {
        JmmNode typeNode = arrayCreationNode.getChild(0);
        Type elementType = TypeUtils.convertType(typeNode);
        return new Type(elementType.getName(), true);
    }

    private static Type getArrayElementType(JmmNode arrayAccess) {
        System.err.println("Warning: Using static getArrayElementType is unreliable.");
        return newIntType();
    }

    // CORRIGIDO getArrayAccessElementTypeNotStatic
    private Type getArrayAccessElementTypeNotStatic(JmmNode arrayAccess, JmmNode method) {
        JmmNode arrayExpr = arrayAccess.getChild(0);
        Type arrayType = getExprTypeNotStatic(arrayExpr, method);

        if (arrayType == null) {
            throw new RuntimeException("Could not determine type for array/varargs expression: " + arrayExpr);
        }

        if (!arrayType.isArray() && !arrayType.getName().endsWith("...")) {
            throw new RuntimeException("Attempting array access on non-array/non-varargs type: " + arrayType.print() + " for expression " + arrayExpr);
        }

        String elementTypeName;
        if (arrayType.getName().endsWith("...")) {
            elementTypeName = arrayType.getName().replace("...", "");
        } else {
            elementTypeName = arrayType.getName();
        }

        return new Type(elementTypeName, false);
    }


    private static Type getFunctionCallType(JmmNode functionCall, SymbolTable table) {
        String methodName = functionCall.get("name");
        Type returnType = table.getReturnType(methodName);
        if (returnType == null) {
            if (functionCall.hasAttribute("type")) {
                return functionCall.getObject("type", Type.class);
            }
            System.err.println("Warning: Return type not found in SymbolTable for method: " + methodName + ". Returning placeholder.");
            return new Type("imported_or_unknown", false);
        }
        return returnType;
    }

    public static boolean isAssignable(Type sourceType, Type destinationType) {
        if (sourceType == null || destinationType == null) {
            System.err.println("Warning: isAssignable called with null type(s).");
            return false;
        }

        if (sourceType.getName().equals(destinationType.getName()) &&
                sourceType.isArray() == destinationType.isArray()) {
            return true;
        }

        if (!destinationType.isArray() && !sourceType.isArray()) {
            if (sourceType.hasAttribute("super")) {
                Object superAttr = sourceType.getObject("super");
                if (superAttr instanceof String && !((String) superAttr).isEmpty() && superAttr.equals(destinationType.getName())) {
                    return true;
                }
            }
        }

        // Permitir atribuir T[] a T... ? Ou T a T...? (Melhor tratar em FunctionCall)

        return false;
    }

    public static boolean isAssignableByImport(Type sourceType, Type destinationType, SymbolTable symbolTable){
        if (sourceType == null || destinationType == null) return false;
        if (symbolTable.getImports().contains(sourceType.getName()) &&
                symbolTable.getImports().contains(destinationType.getName())) {
            return true;
        }
        return false;
    }

    private static Type getVarDecl(JmmNode node){
        JmmNode varType = node.getChild(0);
        String name = varType.getChild(0).get("name");
        boolean isArray = false;
        String op1 = varType.getOptional("op1").orElse("");
        String op2 = varType.getOptional("op2").orElse("");
        if(op1.equals("[") && op2.equals("]")){
            isArray=true;
        }
        return new Type(name, isArray);
    }

    private Type varType(JmmNode varRefNode, JmmNode method, SymbolTable symbolTable){
        String varName = varRefNode.get("name");
        String methodName = (method != null) ? method.get("name") : null;

        if (methodName != null) {
            List<Symbol> locals = symbolTable.getLocalVariables(methodName);
            if (locals != null) {
                for (Symbol symbol : locals) {
                    if (symbol.getName().equals(varName)) {
                        return symbol.getType();
                    }
                }
            }
        }

        if (methodName != null) {
            List<Symbol> params = symbolTable.getParameters(methodName);
            if (params != null) {
                for (Symbol symbol : params) {
                    if (symbol.getName().equals(varName)) {
                        return symbol.getType();
                    }
                }
            }
        }

        List<Symbol> fields = symbolTable.getFields();
        if (fields != null) {
            for (Symbol symbol : fields) {
                if (symbol.getName().equals(varName)) {
                    return symbol.getType();
                }
            }
        }

        if (symbolTable.getImports().contains(varName)) {
            return new Type(varName, false);
        }

        if (symbolTable.getClassName().equals(varName)) {
            return new Type(varName, false);
        }

        System.err.println("Warning: Variable type not found for '" + varName + "' in method '" + methodName + "'. Returning null.");
        return null;
    }

    public static JmmNode getParentMethod(JmmNode node){
        JmmNode current = node;
        while (current != null && !Kind.METHOD_DECL.check(current)) {
            if (Kind.CLASS_DECL.check(current)) return null;
            current = current.getParent();
        }
        return current;
    }

    public boolean isField(JmmNode node){
        if (!Kind.VAR_REF_EXPR.check(node)) {
            return false;
        }

        String varName = node.get("name");
        JmmNode parentMethod = getParentMethod(node);

        boolean existsAsField = table.getFields().stream().anyMatch(f -> f.getName().equals(varName));
        if (!existsAsField) {
            return false;
        }

        if (parentMethod == null) {
            return true;
        }

        String methodName = parentMethod.get("name");
        boolean isLocal = table.getLocalVariables(methodName).stream()
                .anyMatch(l -> l.getName().equals(varName));
        boolean isParam = table.getParameters(methodName).stream()
                .anyMatch(p -> p.getName().equals(varName));

        return !isLocal && !isParam;
    }

    public boolean isInImport(String name, String fullPath){
        String[] parsedPath = fullPath.split("\\.");
        return  name.equals(parsedPath[parsedPath.length-1]);
    }
}
