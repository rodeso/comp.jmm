package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static pt.up.fe.comp2025.ast.Kind.*;
import static pt.up.fe.comp2025.ast.TypeUtils.getExprType;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends PreorderJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String L_PARENTHESIS ="(";
    private final String R_PARENTHESIS =")";
    private final String GET_FIELD ="getfield";
    private final String INVOKE ="invoke";
    private final String STATIC ="static";
    private final String VIRTUAL ="virtual";
    private final String ARRAY_LENGTH = "arraylength";

    private final SymbolTable table;

    private final TypeUtils types;
    private final OptUtils ollirTypes;


    public OllirExprGeneratorVisitor(SymbolTable table, OptUtils optUtils) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = optUtils;
    }


    @Override
    protected void buildVisitor() {
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(BOOLEAN_LITERAL, this::visitBool);
        addVisit(UNARY_EXPR, this::visitUnaryExpr);
        addVisit(NEW,this::visitNewObject);
        addVisit(PRIORITY_EXPR,this::visitPriorityExpr);
        addVisit(CLASS_FUNCTION_EXPR,this::visitFuncCall);
        addVisit(ARRAY_CREATION, this::visitArrayCreation);
        addVisit(ARRAY_ACCESS, this::visitArrayAccess);
        addVisit(OBJECT_REFERENCE, this::visitThis);
        addVisit(TYPE, this::defaultVisit);
        addVisit(BASE_TYPE, this::defaultVisit);
        addVisit(ARRAY_LITERAL, this::visitArrayLiteral);
        addVisit(LENGTH_EXPR, this::visitLengthExpr);

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


    public OllirExprResult visitArrayCreation(JmmNode node, Void unused) {
        var code = new StringBuilder();
        JmmNode typeNode = node.getChild(0);
        JmmNode sizeExprNode = node.getChild(1);

        Type arrayType = TypeUtils.convertType(typeNode);
        arrayType = new Type(arrayType.getName(),true);
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
                .append("new")
                .append("(array,")
                .append(SPACE)
                .append(lengthExprResult.getRef())
                .append(")")
                .append(ollirArrayType)
                .append(END_STMT);

        return new OllirExprResult(tempRef, code.toString());
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



    public OllirExprResult visitForceTemp(JmmNode node, String type) {
        var computation = new StringBuilder();
        String register = ollirTypes.nextTemp();
        var nodeComp = visit(node);

        computation.append(nodeComp.getComputation());

        final Pattern pattern = Pattern.compile("(.*)(\\.[a-z0-9A-Z]*)");
        var matcher = pattern.matcher(nodeComp.getRef());
        if (!matcher.find()) {
            return nodeComp;
        }
        var returnedRegister = matcher.group(1);

        computation.append(String.format("%s%s :=%s %s%s;", register, type, type, returnedRegister, type)).append("\n");

        return new OllirExprResult(register + type, computation);

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
        String code = node.get("value") + ollirBoolType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitUnaryExpr(JmmNode node, Void unused){
        var lhs = visit(node.getChild(0));
        StringBuilder computation = new StringBuilder();

        //computation.append(lhs.getComputation());
        Type exprType = types.getExprTypeNotStatic(node,null);
        String ollirType = ollirTypes.toOllirType(exprType);
        String ref = ollirTypes.nextTemp() + ollirType;

        computation.append(ref).append(SPACE).append(ASSIGN).append(ollirType).append(SPACE)
                .append(node.get("op")).append(ollirType).append(SPACE).append(lhs.getRef()).append(END_STMT);



        return new OllirExprResult(ref,computation);
    }




    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {

        var lhs = visit(node.getChild(0));
        var rhs = visit(node.getChild(1));

        StringBuilder computation = new StringBuilder();

        if(node.get("op").equals("&&")){
            // short circuit
            var firstIf = ollirTypes.nextIfBranch();
            var firstEnd = "endif"+firstIf.substring(4);
            var andTmp = ollirTypes.nextTemp("andTmp");
            // verify if 1st condition is true
            computation.append(lhs.getComputation());
            computation.append("if(").append(lhs.getRef()).append(")").append(SPACE)
                    .append("goto").append(SPACE).append(firstIf).append(END_STMT);

            // if it is not
            computation.append(andTmp).append(".bool").append(SPACE)
                    .append(ASSIGN).append(".bool").append(SPACE).append("false.bool")
                    .append(END_STMT);
            computation.append("goto ").append(firstEnd).append(END_STMT);

            // if it is verify the second part
            computation.append(firstIf).append(":\n");
            computation.append(rhs.getComputation());
            computation.append(andTmp).append(".bool").append(SPACE)
                    .append(ASSIGN).append(".bool").append(SPACE)
                    .append(rhs.getRef()).append(END_STMT);
            // in case 1st is false continue to rest of the code
            computation.append(firstEnd).append(":\n");



            return new OllirExprResult(andTmp+".bool",computation);
        }


        // code to compute the children
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        // code to compute self
        Type resType = types.getExprType(node);
        String resOllirType = ollirTypes.toOllirType(resType);
        String code="";
        if(node.getParent().isInstance(ASSIGN_STMT)){
            if(node.getChild(0).isInstance(VAR_REF_EXPR) && node.getChild(1).isInstance(INTEGER_LITERAL) && !types.isField(node.getChild(0))){
                code = lhs.getRef() +" "+ node.get("op")+resOllirType+" "+rhs.getRef();
                return new OllirExprResult(code);
            }
            if(node.getChild(1).isInstance(VAR_REF_EXPR) && node.getChild(0).isInstance(INTEGER_LITERAL)&& !types.isField(node.getChild(1))){
                code = lhs.getRef() +" "+ node.get("op")+resOllirType+" "+rhs.getRef();
                return new OllirExprResult(code);
            }

        }


        code = ollirTypes.nextTemp() + resOllirType;


        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(lhs.getRef()).append(SPACE);

        Type type = types.getExprType(node);
        computation.append(node.get("op")).append(ollirTypes.toOllirType(type)).append(SPACE)
                .append(rhs.getRef()).append(END_STMT);

        return new OllirExprResult(code, computation);
    }


    private OllirExprResult visitVarRef(JmmNode node, Void unused) {

        var id = node.get("name");
        JmmNode parent = TypeUtils.getParentMethod(node);

        //Type type = types.getExprType(node);
        Type type = types.getExprTypeNotStatic(node,parent);

        if (type == null) {
            return new OllirExprResult(id);
        }
        String ollirType = ollirTypes.toOllirType(type);

        String code = id + ollirType;

        if(types.isField(node)){
            StringBuilder computation = new StringBuilder();

            var tmp = ollirTypes.nextTemp();

            computation.append(tmp).append(ollirType).append(SPACE).append(ASSIGN).append(ollirType)
                    .append(SPACE).append(GET_FIELD).append(L_PARENTHESIS).append("this,").append(code)
                    .append(R_PARENTHESIS).append(ollirType).append(END_STMT);

            return new OllirExprResult(tmp+ollirType,computation);
        }

        return new OllirExprResult(code);
    }

    private OllirExprResult visitNewObject(JmmNode node, Void unused){
        StringBuilder computation = new StringBuilder();

        String objName = node.get("name");
        String reg = ollirTypes.nextTemp()+"."+objName;


        List<String> argRefs = new ArrayList<>();

        for(JmmNode child : node.getChildren()){
            var res = visit(child);
            computation.append(res.getComputation());
            argRefs.add(res.getRef());
        }
        computation.append(reg).append(SPACE).append(ASSIGN).append("."+objName).
                append(SPACE).append("new").append(L_PARENTHESIS).
                append(objName).append(R_PARENTHESIS).append("."+objName).append(END_STMT);

        computation.append("invokespecial(").append(reg).append(", \"<init>\"");

        for(String arg : argRefs){
            computation.append(", ").append(arg);
        }

        computation.append(").V").append(END_STMT);


        return new OllirExprResult(reg,computation);
    }

    public OllirExprResult visitThis(JmmNode node, Void unused) {
        return new OllirExprResult("this." + table.getClassName());
    }


    private OllirExprResult visitPriorityExpr(JmmNode node, Void unused){
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

    /**
     * Default visitor. Visits every child node and return an empty result.
     *
     * @param node
     * @param unused
     * @return
     */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
    }



}
