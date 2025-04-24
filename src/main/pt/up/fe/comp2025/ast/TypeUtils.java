package pt.up.fe.comp2025.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.symboltable.JmmSymbolTable;

import java.util.List;
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

    /**
     * Returns the type for a binary expression by checking the operator and the types of the operands.
     */
    private static Type getBinaryExprType(JmmNode binaryExpr) {

        if (!Kind.BINARY_EXPR.check(binaryExpr)) {
            binaryExpr = binaryExpr.getChild(0);
        }


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
        if(Kind.TYPE.check(typeNode)){
            name = typeNode.getChild(0).get("name");
        }
        boolean isArray = false;
        String op1 = typeNode.getOptional("op1").orElse("");
        String op2 = typeNode.getOptional("op2").orElse("");
        if(op1.equals("[") && op2.equals("]")){
            isArray=true;
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
                case PRIORITY_EXPR -> getExprType(expr.getChild(0));
                case VAR_REF_EXPR -> getVarExprType(expr);
                case ARRAY_LITERAL -> getArrayLiteral(expr);
                case RETURN_STMT -> getReturnType(expr);
                case VAR_DECL -> getVarDecl(expr);
                default -> throw new UnsupportedOperationException("Can't compute type for expression kind '" + kind + "'");
            };
        }
    }
    // fazer funções para ver tipos com a symbol table
    // parent method might not be necessary in that case give null
    public Type getExprTypeNotStatic(JmmNode expr,JmmNode method){
        Kind kind = Kind.fromString(expr.getKind());

        return switch (kind){
            case CLASS_FUNCTION_EXPR -> getFunctionCallType(expr,this.table);
            case VAR_REF_EXPR -> varType(expr,method,this.table);
            case ARRAY_ACCESS -> getArrayAcessType(expr,method,this.table);
            default -> getExprType(expr);
        };
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
//        if (!arrayType.isArray()) { this is now checked int the semantics
//            throw new RuntimeException("Attempting to access a non-array type: " + arrayType.getName());
//        }
        return new Type(arrayType.getName(), false);
    }

    /**
     * Resolves the return type function call by querying the symbol table.
     */
    private static Type getFunctionCallType(JmmNode functionCall, SymbolTable table) {
        String methodName = functionCall.get("name");
        return table.getReturnType(methodName);
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

    private static Type varType(JmmNode varRefNode,JmmNode method, SymbolTable symbolTable){
        Type type = null;
        String methodName = method.get("name");

        List<Symbol> varsFromMethod = symbolTable.getLocalVariables(methodName);
        if(varsFromMethod != null){
            for(Symbol symbol : varsFromMethod){
                if(symbol.getName().equals(varRefNode.get("name"))){
                    type = symbol.getType();
                }
            }
        }


        List<Symbol> fields = symbolTable.getFields();

        if(fields != null){
            for(Symbol symbol : fields){
                if(symbol.getName().equals(varRefNode.get("name"))){
                    type = symbol.getType();
                }
            }
        }

        List<Symbol> params = symbolTable.getParameters(methodName);

        if(params != null){
            for(Symbol symbol : params){
                if(symbol.getName().equals(varRefNode.get("name"))){
                    type = symbol.getType();
                }
            }
        }


        return type;
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

    private static Type getArrayAcessType(JmmNode expr, JmmNode method, SymbolTable table){
        Type returnType = varType((expr.getChild(0)),method,table);
        returnType = new Type(returnType.getName(),false);
        return returnType;
    }

    public  static JmmNode getParentMethod(JmmNode node){
        JmmNode parentMethod = node.getParent();
        while(!Kind.METHOD_DECL.check(parentMethod)){
            parentMethod = parentMethod.getParent();
        }

        return parentMethod;
    }

    public static Type getReturnType(JmmNode expr){
        if(expr.getNumChildren() == 0){
            return new Type("void",false);
        }
        if(expr.getNumChildren() > 1){
            throw new RuntimeException("Return statement has more than one child");
        }
        Kind kind = Kind.fromString(expr.getChild(0).getKind());

        return switch(kind){
            case INTEGER_LITERAL -> newIntType();
            case BOOLEAN_LITERAL -> newBooleanType();
            case BINARY_EXPR -> getBinaryExprType(expr);
            case UNARY_EXPR -> getUnaryExprType(expr);
            case VAR_REF_EXPR -> new Type(expr.getChild(0).get("name"),false);
            default -> throw new RuntimeException("Unknown return type");
        };
    }
}
