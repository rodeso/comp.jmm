package pt.up.fe.comp2025.optimization;

import java.util.List;
import java.util.stream.Collectors;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

import static pt.up.fe.comp2025.ast.Kind.*;
import static pt.up.fe.comp2025.ast.TypeUtils.getExprType;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";
    private final String IMPORT ="import";
    private final String EXTENDS ="extends";
    private final String IF ="if";
    private final String L_PARENTHESIS ="(";
    private final String R_PARENTHESIS =")";
    private final String GOT_TO ="goto";
    private final String COLON =":\n";
    private final String PUT_FIELD ="putfield";



    private final SymbolTable table;

    private final TypeUtils types;
    private final OptUtils ollirTypes;


    private final OllirExprGeneratorVisitor exprVisitor;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = new OptUtils(types);
        exprVisitor = new OllirExprGeneratorVisitor(table,this.ollirTypes);
    }


    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(PARAM, this::visitParam);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(IMPORT_DECL, this::visitImportDecl);
        addVisit(PARAM_LIST, this::visitParamList);
        addVisit(VAR_DECL, this::visitVarDecl);
        addVisit(IF_STMT, this::visitIfStmt);
        addVisit(BRACKETS_STMT, this::visitBracketsStmt);
        addVisit(ELSE_STMT, this::visitElseStmt);
        addVisit(SIMPLE_EXPR, this::visitSimpleExpr);
        addVisit(WHILE_STMT, this::visitWhileStmt);
//        setDefaultVisit(this::defaultVisit);
    }


    private String visitVarDecl(JmmNode node, Void unused){
        JmmNode parent = node.getParent();
        StringBuilder code = new StringBuilder();
        if(CLASS_DECL.check(parent)){
            code.append(".field public");
        }
        Type type = types.getExprTypeNotStatic(node,parent);
        String ollirType = ollirTypes.toOllirType(type);

        code.append(SPACE).append(node.get("name")).append(ollirType).append(END_STMT);

        return code.toString();

    }


    private String visitAssignStmt(JmmNode node, Void unused) {

        var rhs = exprVisitor.visit(node.getChild(1));

        StringBuilder code = new StringBuilder();

        // code to compute the children
        code.append(rhs.getComputation());

        // code to compute self
        // statement has type of lhs
        var left = node.getChild(0);




        JmmNode method = TypeUtils.getParentMethod(node);
        Type thisType = types.getExprTypeNotStatic(left,method);
        String typeString = ollirTypes.toOllirType(thisType);

        String varCode;

        boolean isArrayAcess = false;

        if (Kind.check(left, Kind.ARRAY_ACCESS)) {
            isArrayAcess = true;

            var index = left.getChild(1);
            left = left.getChild(0);

            Type indexType = types.getExprTypeNotStatic(index,method);
            String indexString = ollirTypes.toOllirType(indexType);
            var indexComp = exprVisitor.visit(index);
            code.append(indexComp.getComputation());
            varCode = left.get("name")+"[" +indexComp.getRef() +indexString+ "]" +typeString;
            if(types.isField(left)){
                var tmpArray = ollirTypes.nextTemp();
                // code for getfield
                code.append(tmpArray).append(typeString).append(SPACE)
                        .append(ASSIGN).append(typeString).append(SPACE)
                        .append("getfield(this,").append(left.get("name"))
                        .append(typeString).append(")").append(typeString).append(END_STMT);

                // assign in right index
                code.append(tmpArray).append(typeString)
                        .append("[" +indexComp.getRef() +indexString+ "]")
                        .append(".i32").append(SPACE).append(ASSIGN).append(".i32")
                        .append(SPACE).append(rhs.getRef()).append(END_STMT);
            }
        }
        else {
            varCode = left.get("name") + typeString;
        }


        if(types.isField(left) && !isArrayAcess){
            code.append(PUT_FIELD).append(L_PARENTHESIS).append("this,").append(varCode)
                    .append(",").append(rhs.getRef()).append(R_PARENTHESIS).append(".V").append(END_STMT);
            return code.toString();
        }


        code.append(varCode);
        code.append(SPACE);

        code.append(ASSIGN);
        code.append(typeString);
        code.append(SPACE);

        code.append(rhs.getRef());

        code.append(END_STMT);

        return code.toString();
    }

    private String visitWhileStmt(JmmNode node, Void unused){
        StringBuilder code = new StringBuilder();

        var while_ = ollirTypes.nextWhileBranch();
        var if_ = ollirTypes.nextIfBranch();
        var conditionComputation = exprVisitor.visit(node.getChild(0));
        var endif = "endif"+if_.substring(4);

        code.append(while_).append(COLON).append(conditionComputation.getComputation()).
                append(IF).append(L_PARENTHESIS).append("!.bool ").append(conditionComputation.getRef())
                .append(R_PARENTHESIS).append(SPACE).append(GOT_TO).append(SPACE).append(endif).append(END_STMT);

        List<JmmNode> whileContent = node.getChildren(BRACKETS_STMT);

        for(JmmNode child : whileContent){
            var comp = visit(child);
            code.append(comp);
        }

        code.append(GOT_TO).append(SPACE).append(while_).append(END_STMT);
        code.append(endif).append(COLON);
        return code.toString();
    }

    private String visitIfStmt(JmmNode node, Void unused){
        StringBuilder code = new StringBuilder();

        var condition = exprVisitor.visit(node.getChild(0));

        code.append(condition.getComputation());

        var then = ollirTypes.nextIfBranch();

        code.append(IF).append(SPACE).append(L_PARENTHESIS).append(condition.getRef()).append(R_PARENTHESIS)
                .append(SPACE).append(GOT_TO).append(SPACE).append(then).append(END_STMT);


        List<JmmNode> elseStmt = node.getChildren(ELSE_STMT);
        List<JmmNode> ifContent = node.getChildren(BRACKETS_STMT);

        for(JmmNode els : elseStmt){
            code.append(visit(els));
        }
        String enfIfNum = "endif"+then.substring(4);
        code.append(GOT_TO).append(SPACE).append(enfIfNum).append(END_STMT);

        code.append(then).append(COLON);

        for(JmmNode ifExpr : ifContent){
            code.append(visit(ifExpr));
        }

        code.append(enfIfNum).append(COLON);


        return code.toString();
    }

    private String visitElseStmt(JmmNode node, Void unused){
        StringBuilder code = new StringBuilder();

        for(JmmNode child : node.getChildren()){
            code.append(visit(child));
        }

        return code.toString();
    }

    private String visitBracketsStmt(JmmNode node, Void unused){
        StringBuilder code = new StringBuilder();

        for(JmmNode child : node.getChildren()){
            code.append(visit(child));
        }

        return code.toString();
    }

    private String visitSimpleExpr(JmmNode node, Void unused){
        var code = exprVisitor.visit(node.getChild(0));

        return code.getComputation();
    }


    private String visitReturn(JmmNode node, Void unused) {

        JmmNode retExpr = node.getChild(0);

        Type retType = types.getExprTypeNotStatic(retExpr,TypeUtils.getParentMethod(node));


        StringBuilder code = new StringBuilder();






        var expr = node.getNumChildren() > 0 ? exprVisitor.visit(node.getChild(0)) : OllirExprResult.EMPTY;




        code.append(expr.getComputation());
        code.append("ret");
        code.append(ollirTypes.toOllirType(retType));
        code.append(SPACE);

        code.append(expr.getRef());

        code.append(END_STMT);

        return code.toString();
    }



    private String visitParam(JmmNode node, Void unused) {

        var typeCode = ollirTypes.toOllirType(node.getChild(0));
        var id = node.get("name");

        String code = id + typeCode;

        return code;
    }

    private String visitParamList(JmmNode node, Void unused){
        StringBuilder code = new StringBuilder();

        String methodName = node.getParent().get("name");

        for(int i = 0; i < table.getParameters(methodName).size();i++){
            var param = visit(node.getChild(i));
            code.append(param);
            if(i != table.getParameters(methodName).size()-1){
                code.append(",");
            }


        }

        return code.toString();
    }


    private String visitMethodDecl(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = node.getBoolean("isPublic", false);

        if (isPublic) {
            code.append("public ");
        }

        // name
        var name = node.get("name");

        if(name.equals("main")){
            code.append("static ");
        }
        code.append(name);

        // params

        code.append("(");
        if(!table.getParameters(name).isEmpty()){
            code.append(visit(node.getChild(1)));
        }
        if(name.equals("main")){
            code.append("args.array.String");
        }
        code.append(")");


        // type

        var retType = ollirTypes.toOllirType(table.getReturnType(name));
        code.append(retType);
        code.append(L_BRACKET);


        // rest of its children stmts
        var stmtsCode = node.getChildren(STMT).stream()
                .map(this::visit)
                .collect(Collectors.joining("\n   ", "   ", ""));

        code.append(stmtsCode);


        // Garante que há uma instrução antes do '}'
        if (retType.equals(".V")) {
            // Adicionar newline se necessário antes do ret
            if (!stmtsCode.endsWith(NL) && !stmtsCode.isEmpty()) {
                code.append(NL);
            }
            code.append("ret").append(".V").append(END_STMT);
        }
        code.append(R_BRACKET);
        code.append(NL);

        return code.toString();
    }

    private String visitImportDecl(JmmNode node, Void unused){
        StringBuilder code = new StringBuilder();
        var path = node.getOptional("path").orElse("");
        path=path.substring(1,path.length()-1);
        StringBuilder realPath = new StringBuilder();
        realPath.append(IMPORT).append(SPACE);

        if(!path.isEmpty()){

            path = path.replaceAll("\\s+","");
            String[] dir = path.trim().split(",");

            for(String d : dir){
                realPath.append(d).append(".");
            }


        }
        var file = node.get("name");
        code.append(realPath).append(file).append(END_STMT);
        return code.toString();

    }
    private String visitClass(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        code.append(NL);
        code.append(table.getClassName());
        boolean isSub = node.getBoolean("isSub",false);
        if(isSub){
            String superClass = node.get("parent");
            code.append(SPACE).append(EXTENDS).append(SPACE).append(superClass);
        }
        code.append(L_BRACKET);
        code.append(NL);
        code.append(NL);

        for (var child : node.getChildren(VAR_DECL)) {
            var result = visit(child);
            code.append(result);
        }

        code.append(buildConstructor());
        code.append(NL);

        for (var child : node.getChildren(METHOD_DECL)) {
            var result = visit(child);
            code.append(result);
        }

        code.append(R_BRACKET);

        return code.toString();
    }

    private String buildConstructor() {

        return """
                .construct %s().V {
                    invokespecial(this, "<init>").V;
                }
                """.formatted(table.getClassName());
    }


    private String visitProgram(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        node.getChildren().stream()
                .map(this::visit)
                .forEach(code::append);

        return code.toString();
    }

    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return "";
    }
}
