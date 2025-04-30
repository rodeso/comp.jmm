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

    private final SymbolTable table;

    private final TypeUtils types;
    private final OptUtils ollirTypes;


    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = new OptUtils(types);
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

//        setDefaultVisit(this::defaultVisit);
    }

    public OllirExprResult visitArrayCreation(JmmNode node, Void unused) {
        var code = new StringBuilder();
        var lengthExpr = visit(node.getChild(0));
        code.append("new(array,").append(SPACE).append(lengthExpr.getRef()).append(")").append(ollirTypes.toOllirType(node));

        return new OllirExprResult(code.toString(), lengthExpr.getComputation());
    }

    private OllirExprResult visitArrayAccess(JmmNode node, Void unused) {

        JmmNode arrayNode = node.getChild(0);
        JmmNode indexNode = node.getChild(1);

        OllirExprResult array = visit(arrayNode);
        OllirExprResult index;
        Type indexType = getExprType(indexNode);

        var code = new StringBuilder();
        var computation = new StringBuilder();

        if (!(indexNode.isInstance(INTEGER_LITERAL) || indexNode.isInstance(BOOLEAN_LITERAL))) {
            index = visitForceTemp(indexNode, ollirTypes.toOllirType(indexType));
        } else index = visit(indexNode);

        String varName;

        if (arrayNode.isInstance(ARRAY_LITERAL)) {
            varName = array.getRef();
            varName = varName.substring(0, varName.indexOf('.'));
        } else {
            varName = arrayNode.get("name"); // Node BinaryExpr does not contain attribute 'name'
        }
        code.append(varName).append("[").append(index.getRef()).append("]").append(".i32");

        return new OllirExprResult(code.toString(), array.getComputation() + index.getComputation());


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

        // code to compute the children
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        // code to compute self
        Type resType = types.getExprType(node);
        String resOllirType = ollirTypes.toOllirType(resType);
        String code = ollirTypes.nextTemp() + resOllirType;

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

    private OllirExprResult visitFuncCall(JmmNode node, Void unused){
        String funcName = node.get("name");
        JmmNode parent = node.getParent();
        JmmNode method = TypeUtils.getParentMethod(node);
        JmmNode caller = node.getChild(0);

        // ver se é this
        Type type = types.getExprTypeNotStatic(caller,method);
        String callerName = caller.get("name");

        StringBuilder computation = new StringBuilder();
        String ref = "";
        List<String> args = new ArrayList<>();

        for(int i = 1 ; i<node.getNumChildren(); i++){
            var childComp = visit(node.getChild(1));
            computation.append(childComp.getComputation());
            args.add(childComp.getRef());
        }

        if(table.getImports().contains(callerName)){
            //static method from the import
            computation.append(INVOKE).append(STATIC).append(L_PARENTHESIS).append(caller.get("name"))
                    .append(",").append("\""+funcName+"\"");

            for(int j = 0; j< args.size(); j++){
                if(j != args.size() -1){
                    computation.append(",").append(SPACE);
                }
                computation.append(args.get(j));
            }
            computation.append(R_PARENTHESIS)
                    .append(".").append("v").append(END_STMT);
        }

        // falta para se obj for de tipo importado, para extends e para quando for um tipo importado mas estiver a dar assign


        if(VAR_REF_EXPR.check(caller)){

            Type returnType = table.getReturnType(funcName);
            var ollirType = ollirTypes.toOllirType(type);
            var ollirReturnType = ollirTypes.toOllirType(returnType);
            computation.append(INVOKE).append(VIRTUAL).append(L_PARENTHESIS).append(caller.get("name")+"."+ollirType)
                    .append(",").append("\""+funcName+"\"");

            for(int j = 0; j< args.size(); j++){
                if(j != args.size() -1){
                    computation.append(",").append(SPACE);
                }
                computation.append(args.get(j));
            }
            computation.append(R_PARENTHESIS)
                    .append(".").append(ollirReturnType).append(END_STMT);
        }


        return new OllirExprResult(ref,computation);
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
