package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static pt.up.fe.comp2025.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor
        extends PreorderJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String L_PARENTHESIS = "(";
    private final String R_PARENTHESIS = ")";
    private final String GET_FIELD = "getfield";
    private final String INVOKE = "invoke";
    private final String STATIC = "static";
    private final String VIRTUAL = "virtual";
    private final String SPECIAL = "special";
    private final String THIS = "this";
    private final String NEW = "new";
    private final String ARRAY_LENGTH = "arraylength";
    private final String IF = "if";
    private final String GOT_TO = "goto";
    private final String COLON = ":\n";

    private final SymbolTable table;
    private final TypeUtils types;
    private final OptUtils ollirTypes;

    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = new OptUtils(types); // Initialize OptUtils here
        buildVisitor(); // Call buildVisitor in the constructor
    }

    @Override
    protected void buildVisitor() {
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(BOOLEAN_LITERAL, this::visitBool);
        addVisit(UNARY_EXPR, this::visitUnaryExpr);
        addVisit(NEW, this::visitNewObject);
        addVisit(PRIORITY_EXPR, this::visitPriorityExpr);
        addVisit(CLASS_FUNCTION_EXPR, this::visitFuncCall);
        addVisit(ARRAY_CREATION, this::visitArrayCreation);
        addVisit(ARRAY_ACCESS, this::visitArrayAccess);
        addVisit(OBJECT_REFERENCE, this::visitThis);
        addVisit(LENGTH_EXPR, this::visitLengthExpr);
        addVisit(ARRAY_LITERAL, this::visitArrayLiteral);

        setDefaultVisit(this::defaultVisit);
    }

    private OllirExprResult visitArrayLiteral(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();
        Type intArrayType = new Type("int", true);
        String ollirArrayType = ollirTypes.toOllirType(intArrayType);
        String arraySize = String.valueOf(node.getNumChildren());

        // 1. Create the array
        String arrayRef = ollirTypes.nextTemp() + ollirArrayType;
        computation
                .append(arrayRef)
                .append(SPACE)
                .append(ASSIGN)
                .append(ollirArrayType)
                .append(SPACE)
                .append(NEW)
                .append("(array, ")
                .append(arraySize)
                .append(".i32)")
                .append(ollirArrayType)
                .append(END_STMT);

        // 2. Initialize each element
        for (int i = 0; i < node.getNumChildren(); i++) {
            JmmNode elementNode = node.getChild(i);
            OllirExprResult elementResult = visit(elementNode);
            computation.append(elementResult.getComputation());
            computation
                    .append(arrayRef)
                    .append("[")
                    .append(i)
                    .append(".i32].i32")
                    .append(SPACE)
                    .append(ASSIGN)
                    .append(".i32")
                    .append(SPACE)
                    .append(elementResult.getRef())
                    .append(END_STMT);
        }

        return new OllirExprResult(arrayRef, computation);
    }

    private OllirExprResult visitLengthExpr(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();
        OllirExprResult arrayExprResult = visit(node.getChild(0));
        computation.append(arrayExprResult.getComputation());

        String resultTemp = ollirTypes.nextTemp() + ".i32";

        computation
                .append(resultTemp)
                .append(SPACE)
                .append(ASSIGN)
                .append(".i32")
                .append(SPACE)
                .append(ARRAY_LENGTH)
                .append(L_PARENTHESIS)
                .append(arrayExprResult.getRef())
                .append(R_PARENTHESIS)
                .append(".i32")
                .append(END_STMT);

        return new OllirExprResult(resultTemp, computation);
    }

    public OllirExprResult visitArrayCreation(JmmNode node, Void unused) {
        var code = new StringBuilder();
        // Child 0 is Type, Child 1 is size expression
        JmmNode typeNode = node.getChild(0);
        Type arrayElementType = TypeUtils.convertType(typeNode);
        Type arrayType = new Type(arrayElementType.getName(), true);
        String ollirArrayType = ollirTypes.toOllirType(arrayType);

        var lengthExprResult = visit(node.getChild(1));
        code.append(lengthExprResult.getComputation());

        var tmp = ollirTypes.nextTemp();
        code
                .append(tmp)
                .append(ollirArrayType)
                .append(SPACE)
                .append(ASSIGN)
                .append(ollirArrayType)
                .append(SPACE);
        code
                .append(NEW)
                .append("(array,")
                .append(SPACE)
                .append(lengthExprResult.getRef())
                .append(")")
                .append(ollirArrayType)
                .append(END_STMT);

        return new OllirExprResult(tmp + ollirArrayType, code.toString());
    }

    private OllirExprResult visitArrayAccess(JmmNode node, Void unused) {
        JmmNode arrayNode = node.getChild(0);
        JmmNode indexNode = node.getChild(1);
        JmmNode methodNode = TypeUtils.getParentMethod(node);

        OllirExprResult arrayResult = visit(arrayNode);
        OllirExprResult indexResult = visit(indexNode);

        var computation = new StringBuilder();
        computation.append(arrayResult.getComputation());
        computation.append(indexResult.getComputation());

        // Determine element type
        Type arrayType = types.getExprTypeNotStatic(arrayNode, methodNode);
        Type elementType = new Type(arrayType.getName(), false); // Element type
        String ollirElementType = ollirTypes.toOllirType(elementType);

        // Temporary variable to store the result of the access
        String tempVar = ollirTypes.nextTemp() + ollirElementType;

        computation
                .append(tempVar)
                .append(SPACE)
                .append(ASSIGN)
                .append(ollirElementType)
                .append(SPACE)
                .append(arrayResult.getRef()) // Array reference
                .append("[")
                .append(indexResult.getRef()) // Index reference
                .append("]")
                .append(ollirElementType) // Element type
                .append(END_STMT);

        return new OllirExprResult(tempVar, computation.toString());
    }

    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = TypeUtils.newIntType();
        String ollirIntType = ollirTypes.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitBool(JmmNode node, Void unused) {
        var boolType = TypeUtils.newBooleanType();
        String ollirBoolType = ollirTypes.toOllirType(boolType);
        // OLLIR uses 1 for true, 0 for false
        String value = node.get("value").equals("true") ? "1" : "0";
        String code = value + ollirBoolType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitUnaryExpr(JmmNode node, Void unused) {
        var operandResult = visit(node.getChild(0));
        StringBuilder computation = new StringBuilder();
        computation.append(operandResult.getComputation());

        Type exprType = types.getExprType(node);
        String ollirType = ollirTypes.toOllirType(exprType);
        String ref = ollirTypes.nextTemp() + ollirType;
        String operator = node.get("op");

        computation
                .append(ref)
                .append(SPACE)
                .append(ASSIGN)
                .append(ollirType)
                .append(SPACE)
                .append(operator)
                .append(ollirType)
                .append(SPACE)
                .append(operandResult.getRef())
                .append(END_STMT);

        return new OllirExprResult(ref, computation);
    }

    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {
        String operator = node.get("op");

        if (operator.equals("&&")) {
            return visitAndExpr(node, unused);
        }

        var lhs = visit(node.getChild(0));
        var rhs = visit(node.getChild(1));

        StringBuilder computation = new StringBuilder();
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        Type resType = types.getExprType(node);
        String resOllirType = ollirTypes.toOllirType(resType);
        String code = ollirTypes.nextTemp() + resOllirType;

        Type operandType = types.getExprTypeNotStatic(
                node.getChild(0),
                TypeUtils.getParentMethod(node)
        );
        String opOllirType;

        // Define operator type based on the operator
        if (List.of("+", "-", "*", "/").contains(operator)) {
            opOllirType = ollirTypes.toOllirType(TypeUtils.newIntType()); // Arithmetic ops use .i32
        } else if (List.of("<").contains(operator)) {
            opOllirType = ollirTypes.toOllirType(TypeUtils.newIntType()); // Comparison operands are .i32
        }
        else {
            // Fallback or throw error for unsupported operators
            opOllirType = ollirTypes.toOllirType(operandType); // Default guess
            System.err.println("Warning: Unhandled binary operator type determination for: " + operator);
        }


        computation
                .append(code)
                .append(SPACE)
                .append(ASSIGN)
                .append(resOllirType) // Result type assignment
                .append(SPACE)
                .append(lhs.getRef())
                .append(SPACE);

        computation
                .append(operator)
                .append(opOllirType)
                .append(SPACE)
                .append(rhs.getRef())
                .append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitAndExpr(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();
        String trueLabel = ollirTypes.nextIfBranch("trueL");
        String endLabel = ollirTypes.nextIfBranch("endL");
        String resultTemp = ollirTypes.nextTemp() + ".bool"; // Result is boolean

        // Evaluate LHS
        var lhsResult = visit(node.getChild(0));
        computation.append(lhsResult.getComputation());

        computation
                .append(IF)
                .append(L_PARENTHESIS)
                .append(lhsResult.getRef())
                .append(R_PARENTHESIS)
                .append(SPACE)
                .append(GOT_TO)
                .append(SPACE)
                .append(trueLabel)
                .append(END_STMT);

        computation
                .append(resultTemp)
                .append(SPACE)
                .append(ASSIGN)
                .append(".bool")
                .append(SPACE)
                .append("0.bool")
                .append(END_STMT);
        computation.append(GOT_TO).append(SPACE).append(endLabel).append(END_STMT);

        // trueLabel: Evaluate RHS
        computation.append(trueLabel).append(COLON);
        var rhsResult = visit(node.getChild(1));
        computation.append(rhsResult.getComputation());


        computation
                .append(resultTemp)
                .append(SPACE)
                .append(ASSIGN)
                .append(".bool")
                .append(SPACE)
                .append(rhsResult.getRef())
                .append(END_STMT);
        // Fall through to endLabel

        // endLabel:
        computation.append(endLabel).append(COLON);

        return new OllirExprResult(resultTemp, computation.toString());
    }


    private OllirExprResult visitVarRef(JmmNode node, Void unused) {
        var id = node.get("name");
        JmmNode methodNode = TypeUtils.getParentMethod(node);

        Type type = types.getExprTypeNotStatic(node, methodNode);
        if (type == null) {

            return new OllirExprResult(id); // Simplistic fallback
        }

        String ollirType = ollirTypes.toOllirType(type);
        String code = id + ollirType;

        if (types.isField(node)) {
            StringBuilder computation = new StringBuilder();
            var tmp = ollirTypes.nextTemp();
            computation
                    .append(tmp)
                    .append(ollirType)
                    .append(SPACE)
                    .append(ASSIGN)
                    .append(ollirType)
                    .append(SPACE)
                    .append(GET_FIELD)
                    .append(L_PARENTHESIS)
                    .append(THIS)
                    .append(".")
                    .append(table.getClassName())
                    .append(", ")
                    .append(id)
                    .append(ollirType)
                    .append(R_PARENTHESIS)
                    .append(ollirType)
                    .append(END_STMT);
            return new OllirExprResult(tmp + ollirType, computation);
        }

        return new OllirExprResult(code);
    }

    private OllirExprResult visitNewObject(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();
        String className = node.get("name");
        String ollirType = "." + className;
        String reg = ollirTypes.nextTemp() + ollirType;

        // 1. Create new object instance
        computation
                .append(reg)
                .append(SPACE)
                .append(ASSIGN)
                .append(ollirType)
                .append(SPACE)
                .append(NEW)
                .append(L_PARENTHESIS)
                .append(className)
                .append(R_PARENTHESIS)
                .append(ollirType)
                .append(END_STMT);

        // 2. Call constructor invokespecial
        computation
                .append(INVOKE)
                .append(SPECIAL)
                .append(L_PARENTHESIS)
                .append(reg) // The newly created object
                .append(", ")
                .append("\"<init>\"") // Constructor method name
                .append(R_PARENTHESIS)
                .append(".V") // Constructor returns void
                .append(END_STMT);

        return new OllirExprResult(reg, computation);
    }

    public OllirExprResult visitThis(JmmNode node, Void unused) {
        return new OllirExprResult(THIS + "." + table.getClassName());
    }

    private OllirExprResult visitPriorityExpr(JmmNode node, Void unused) {
        return visit(node.getChild(0));
    }

    private OllirExprResult visitFuncCall(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();
        String funcName = node.get("name");
        JmmNode callerNode = node.getChild(0);
        JmmNode methodNode = TypeUtils.getParentMethod(node);

        // 1. Evaluate Arguments
        List<String> argRefs = new ArrayList<>();
        for (int i = 1; i < node.getNumChildren(); i++) {
            JmmNode argNode = node.getChild(i);
            OllirExprResult argResult = visit(argNode);
            computation.append(argResult.getComputation());
            argRefs.add(argResult.getRef());
        }

        // 2. Determine Return Type
        Type returnType = table.getReturnType(funcName);
        if (returnType == null) {
            if (node.hasAttribute("type")) {
                returnType = node.getObject("type", Type.class);
            } else {
                System.err.println(
                        "Warning: Return type for method '" +
                                funcName +
                                "' not found. Assuming void or placeholder."
                );
                returnType = new Type("void", false);
            }
        }
        String ollirReturnType = ollirTypes.toOllirType(returnType);

        String invokeType;
        OllirExprResult callerResult;
        String callerRef;

        boolean isStaticCall = false;
        if (callerNode.isInstance(VAR_REF_EXPR)) {
            String callerName = callerNode.get("name");
            if (
                    table.getImports().contains(callerName) ||
                            table.getClassName().equals(callerName)
            ) {
                if (table.getImports().contains(callerName)) {
                    isStaticCall = true;
                } else {
                    isStaticCall = false;
                }
            }
        }

        if (isStaticCall) {
            invokeType = STATIC;
            callerRef = callerNode.get("name"); // Class name is the "caller"
        } else {
            invokeType = VIRTUAL;
            callerResult = visit(callerNode); // Evaluate the object expression
            computation.append(callerResult.getComputation());
            callerRef = callerResult.getRef();
        }

        String resultTemp = "";
        if (!ollirReturnType.equals(".V")) {
            resultTemp = ollirTypes.nextTemp() + ollirReturnType;
            computation
                    .append(resultTemp)
                    .append(SPACE)
                    .append(ASSIGN)
                    .append(ollirReturnType)
                    .append(SPACE);
        }

        computation.append(INVOKE).append(invokeType).append(L_PARENTHESIS);
        computation.append(callerRef); // Object ref or Class name
        computation.append(",").append(SPACE).append("\"").append(funcName).append("\"");

        for (String argRef : argRefs) {
            computation.append(",").append(SPACE).append(argRef);
        }

        computation.append(R_PARENTHESIS).append(ollirReturnType).append(END_STMT);

        return new OllirExprResult(resultTemp, computation.toString());
    }

    /**
     * Default visitor. Visits every child node and return an empty result.
     */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {
        for (var child : node.getChildren()) {
            visit(child, unused); // Pass unused along
        }
        return OllirExprResult.EMPTY; // Return empty result
    }
}
