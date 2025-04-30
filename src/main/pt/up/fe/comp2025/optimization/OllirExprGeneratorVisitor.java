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
        this.ollirTypes = new OptUtils(types);
        buildVisitor();
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
        JmmNode typeNode = node.getChild(0);
        JmmNode sizeExprNode = node.getChild(1);

        Type arrayType = TypeUtils.convertType(typeNode);
        String ollirArrayType = ollirTypes.toOllirType(arrayType);

        var lengthExprResult = visit(sizeExprNode);
        code.append(lengthExprResult.getComputation());

        var tmp = ollirTypes.nextTemp();
        String tempRef = tmp + ollirArrayType;

        code
                .append(tempRef)
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

        return new OllirExprResult(tempRef, code.toString());
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

        Type arrayType = types.getExprTypeNotStatic(arrayNode, methodNode);
        if (arrayType == null || !arrayType.isArray()) {
            return new OllirExprResult("ERROR_ACCESS_NON_ARRAY", computation);
        }
        Type elementType = new Type(arrayType.getName(), false);
        String ollirElementType = ollirTypes.toOllirType(elementType);

        String tempVar = ollirTypes.nextTemp() + ollirElementType;

        computation
                .append(tempVar)
                .append(SPACE)
                .append(ASSIGN)
                .append(ollirElementType)
                .append(SPACE)
                .append(arrayResult.getRef())
                .append("[")
                .append(indexResult.getRef())
                .append("]")
                .append(ollirElementType)
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
        JmmNode lhsNode = node.getChild(0);
        JmmNode rhsNode = node.getChild(1);
        // --- Lógica Condicional para && ---
        if (operator.equals("&&")) {
            boolean lhsIsLiteral = lhsNode.isInstance(Kind.BOOLEAN_LITERAL);
            boolean rhsIsLiteral = rhsNode.isInstance(Kind.BOOLEAN_LITERAL);

            if (lhsIsLiteral && rhsIsLiteral) {
                var lhs = visit(lhsNode);
                var rhs = visit(rhsNode);
                StringBuilder computation = new StringBuilder();
                Type resType = TypeUtils.newBooleanType();
                String resOllirType = ollirTypes.toOllirType(resType);
                String code = ollirTypes.nextTemp() + resOllirType;
                String opOllirType = ollirTypes.toOllirType(TypeUtils.newBooleanType());

                computation
                        .append(code)
                        .append(SPACE).append(ASSIGN).append(resOllirType)
                        .append(SPACE).append(lhs.getRef())
                        .append(SPACE).append(operator).append(opOllirType) // &&.bool
                        .append(SPACE).append(rhs.getRef())
                        .append(END_STMT);
                return new OllirExprResult(code, computation);

            } else {
                return visitAndExpr(node, unused);
            }
        }

        var lhs = visit(lhsNode);
        var rhs = visit(rhsNode);

        StringBuilder computation = new StringBuilder();
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        Type resType = types.getExprType(node);
        String resOllirType = ollirTypes.toOllirType(resType);
        String code = ollirTypes.nextTemp() + resOllirType;

        Type operandType = types.getExprTypeNotStatic(lhsNode, TypeUtils.getParentMethod(node));
        String opOllirType;

        if (List.of("+", "-", "*", "/").contains(operator)) {
            opOllirType = ollirTypes.toOllirType(TypeUtils.newIntType());
        } else if (List.of("<").contains(operator)) {
            opOllirType = ollirTypes.toOllirType(TypeUtils.newIntType());
        } else {
            opOllirType = ollirTypes.toOllirType(operandType);
            System.err.println("Warning: Unhandled binary operator type determination for: " + operator);
        }

        computation
                .append(code)
                .append(SPACE).append(ASSIGN).append(resOllirType)
                .append(SPACE).append(lhs.getRef())
                .append(SPACE);

        computation
                .append(operator).append(opOllirType)
                .append(SPACE).append(rhs.getRef())
                .append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitAndExpr(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();
        String trueLabel = ollirTypes.nextIfBranch("trueL");
        String endLabel = ollirTypes.nextIfBranch("endL");
        String resultTemp = ollirTypes.nextTemp() + ".bool";

        var lhsResult = visit(node.getChild(0));
        computation.append(lhsResult.getComputation());

        computation
                .append(IF).append(L_PARENTHESIS).append(lhsResult.getRef()).append(R_PARENTHESIS)
                .append(SPACE).append(GOT_TO).append(SPACE).append(trueLabel).append(END_STMT);

        computation
                .append(resultTemp).append(SPACE).append(ASSIGN).append(".bool")
                .append(SPACE).append("0.bool").append(END_STMT);
        computation.append(GOT_TO).append(SPACE).append(endLabel).append(END_STMT);

        computation.append(trueLabel).append(COLON);
        var rhsResult = visit(node.getChild(1));
        computation.append(rhsResult.getComputation());
        computation
                .append(resultTemp).append(SPACE).append(ASSIGN).append(".bool")
                .append(SPACE).append(rhsResult.getRef()).append(END_STMT);

        computation.append(endLabel).append(COLON);

        return new OllirExprResult(resultTemp, computation.toString());
    }

    private OllirExprResult visitVarRef(JmmNode node, Void unused) {
        var id = node.get("name");
        JmmNode methodNode = TypeUtils.getParentMethod(node);

        Type type = types.getExprTypeNotStatic(node, methodNode);
        if (type == null) {
            return new OllirExprResult(id);
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
        String ollirType = ollirTypes.toOllirType(new Type(className, false));
        String reg = ollirTypes.nextTemp() + ollirType;

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

        computation
                .append(INVOKE)
                .append(SPECIAL)
                .append(L_PARENTHESIS)
                .append(reg)
                .append(", ")
                .append("\"<init>\"")
                .append(R_PARENTHESIS)
                .append(".V")
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

        List<String> argRefs = new ArrayList<>();
        for (int i = 1; i < node.getNumChildren(); i++) {
            JmmNode argNode = node.getChild(i);
            OllirExprResult argResult = visit(argNode);
            computation.append(argResult.getComputation());
            argRefs.add(argResult.getRef());
        }

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
            if (table.getImports().contains(callerName)) {
                isStaticCall = true;
            } else if (table.getClassName().equals(callerName)) {
                isStaticCall = false;
            }
        }

        if (isStaticCall) {
            invokeType = STATIC;
            callerRef = callerNode.get("name");
        } else {
            invokeType = VIRTUAL;
            callerResult = visit(callerNode);
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
        computation.append(callerRef);
        computation.append(",").append(SPACE).append("\"").append(funcName).append("\"");

        for (String argRef : argRefs) {
            computation.append(",").append(SPACE).append(argRef);
        }

        computation.append(R_PARENTHESIS).append(ollirReturnType).append(END_STMT);

        return new OllirExprResult(resultTemp, computation.toString());
    }

    private OllirExprResult defaultVisit(JmmNode node, Void unused) {
        for (var child : node.getChildren()) {
            visit(child, unused);
        }
        return OllirExprResult.EMPTY;
    }
}
