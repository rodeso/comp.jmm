package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.ArrayList;
import java.util.List;

import static pt.up.fe.comp2025.ast.Kind.*;

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

//        setDefaultVisit(this::defaultVisit);
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
