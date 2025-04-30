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

public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";
    private final String IMPORT = "import";
    private final String EXTENDS = "extends";
    private final String IF = "if";
    private final String L_PARENTHESIS = "(";
    private final String R_PARENTHESIS = ")";
    private final String GOT_TO = "goto";
    private final String COLON = ":\n";
    private final String PUT_FIELD = "putfield";
    private final String THIS = "this";
    private final String RETURN = "ret";
    private final String NEW = "new"; // Added constant
    private final String INVOKE = "invoke"; // Added constant
    private final String SPECIAL = "special"; // Added constant


    private final SymbolTable table;
    private final TypeUtils types;
    private final OptUtils ollirTypes;
    private final OllirExprGeneratorVisitor exprVisitor;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = new OptUtils(types);
        this.exprVisitor = new OllirExprGeneratorVisitor(table);
        buildVisitor();
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
        addVisit(SIMPLE_EXPR, this::visitSimpleExpr);
        addVisit(WHILE_STMT, this::visitWhileStmt);
        setDefaultVisit(this::defaultVisit);
    }

    private String visitVarDecl(JmmNode node, Void unused) {
        JmmNode parent = node.getParent();
        StringBuilder code = new StringBuilder();
        if (CLASS_DECL.check(parent)) {
            code.append(".field public ");
            Type type = TypeUtils.convertType(node.getChild(0));
            String ollirType = ollirTypes.toOllirType(type);
            code.append(node.get("name")).append(ollirType).append(END_STMT);
        }
        return code.toString();
    }

    private String visitAssignStmt(JmmNode node, Void unused) {
        JmmNode lhsNode = node.getChild(0);
        JmmNode rhsNode = node.getChild(1);
        JmmNode methodNode = TypeUtils.getParentMethod(node);

        StringBuilder code = new StringBuilder();

        Type lhsType = types.getExprTypeNotStatic(lhsNode, methodNode);
        if (lhsType == null) {
            System.err.println("Error: Could not determine type for LHS of assignment: " + lhsNode);
            return "// ERROR: Could not determine type for LHS\n";
        }
        String ollirLhsType = ollirTypes.toOllirType(lhsType);
        String lhsRef;

        if (lhsNode.isInstance(Kind.ARRAY_ACCESS)) {
            // Atribuição a array - será tratada depois de avaliar RHS
            // Precisamos da referência completa a[i].type
            JmmNode arrayVarNode = lhsNode.getChild(0);
            JmmNode indexNode = lhsNode.getChild(1);
            var arrayResult = exprVisitor.visit(arrayVarNode);
            var indexResult = exprVisitor.visit(indexNode);
            code.append(arrayResult.getComputation());
            code.append(indexResult.getComputation());
            Type elementType = new Type(lhsType.getName(), false);
            String ollirElementType = ollirTypes.toOllirType(elementType);
            lhsRef = arrayResult.getRef() + "[" + indexResult.getRef() + "]" + ollirElementType;
            ollirLhsType = ollirElementType;

        } else if (types.isField(lhsNode)) {
            var rhsResult = exprVisitor.visit(rhsNode);
            code.append(rhsResult.getComputation());
            String fieldName = lhsNode.get("name");
            code.append(PUT_FIELD).append(L_PARENTHESIS)
                    .append(THIS).append(".").append(table.getClassName())
                    .append(", ").append(fieldName).append(ollirLhsType)
                    .append(", ").append(rhsResult.getRef())
                    .append(R_PARENTHESIS).append(".V").append(END_STMT);
            return code.toString(); // Termina aqui para PUTFIELD

        } else {
            lhsRef = lhsNode.get("name") + ollirLhsType;
        }



        if (rhsNode.isInstance(Kind.NEW)) {
            String className = rhsNode.get("name");
            String ollirClassType = ollirTypes.toOllirType(new Type(className, false));

            // Gerar código como nos exemplos OLLIR:
            code.append(lhsRef)
                    .append(SPACE).append(ASSIGN).append(ollirLhsType)
                    .append(SPACE).append(NEW).append(L_PARENTHESIS).append(className).append(R_PARENTHESIS).append(ollirClassType)
                    .append(END_STMT);
            code.append(INVOKE).append(SPECIAL).append(L_PARENTHESIS)
                    .append(lhsRef).append(", ").append("\"<init>\"").append(R_PARENTHESIS).append(".V")
                    .append(END_STMT);

        } else if (rhsNode.isInstance(Kind.ARRAY_CREATION)) {
            var rhsResult = exprVisitor.visit(rhsNode);
            code.append(rhsResult.getComputation()); // Contém o tmpN.array := new(...)

            code.append(lhsRef)
                    .append(SPACE).append(ASSIGN).append(ollirLhsType)
                    .append(SPACE).append(rhsResult.getRef())
                    .append(END_STMT);

        } else {
            // Outros tipos de RHS
            var rhsResult = exprVisitor.visit(rhsNode);
            code.append(rhsResult.getComputation()); // Computação do RHS

            code.append(lhsRef)
                    .append(SPACE).append(ASSIGN).append(ollirLhsType)
                    .append(SPACE).append(rhsResult.getRef())
                    .append(END_STMT);
        }

        return code.toString();
    }


    private String visitWhileStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        String loopLabel = ollirTypes.nextWhileBranch("Loop");
        String condLabel = ollirTypes.nextWhileBranch("Cond");
        String endLabel = ollirTypes.nextWhileBranch("EndLoop");

        code.append(GOT_TO).append(SPACE).append(condLabel).append(END_STMT);

        code.append(loopLabel).append(COLON);
        code.append(visit(node.getChild(1)));

        code.append(condLabel).append(COLON);
        var conditionResult = exprVisitor.visit(node.getChild(0));
        code.append(conditionResult.getComputation());
        code
                .append(IF)
                .append(L_PARENTHESIS)
                .append(conditionResult.getRef())
                .append(R_PARENTHESIS)
                .append(SPACE)
                .append(GOT_TO)
                .append(SPACE)
                .append(loopLabel)
                .append(END_STMT);

        code.append(endLabel).append(COLON);
        return code.toString();
    }

    private String visitIfStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        JmmNode conditionNode = node.getChild(0);
        JmmNode thenNode = node.getChild(1);
        JmmNode elseNode = node.getNumChildren() > 2 ? node.getChild(2) : null;

        String elseLabel = ollirTypes.nextIfBranch("if_else");
        String endLabel = ollirTypes.nextIfBranch("if_end");
        String finalElseLabel = elseNode != null ? elseLabel : endLabel;

        // Avaliar condição
        var conditionResult = exprVisitor.visit(conditionNode);
        code.append(conditionResult.getComputation());

        // Negar condição para saltar para else/end se for falsa
        String negatedCondTemp = ollirTypes.nextTemp() + ".bool";
        code.append(negatedCondTemp).append(SPACE).append(ASSIGN).append(".bool").append(SPACE)
                .append("!.bool").append(SPACE).append(conditionResult.getRef()).append(END_STMT);
        code.append(IF).append(L_PARENTHESIS).append(negatedCondTemp).append(R_PARENTHESIS)
                .append(SPACE).append(GOT_TO).append(SPACE).append(finalElseLabel).append(END_STMT);

        code.append(visit(thenNode));
        // Se havia um bloco else, saltar por cima dele para o end
        if (elseNode != null) {
            code.append(GOT_TO).append(SPACE).append(endLabel).append(END_STMT);
        }

        // Bloco Else (se existir)
        if (elseNode != null) {
            code.append(elseLabel).append(COLON);
            code.append(visit(elseNode));
            // Cai naturalmente no endLabel
        }

        // Label final
        code.append(endLabel).append(COLON);

        return code.toString();
    }

    private String visitBracketsStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        for (JmmNode child : node.getChildren()) {
            code.append(visit(child));
        }
        return code.toString();
    }

    private String visitSimpleExpr(JmmNode node, Void unused) {
        var code = exprVisitor.visit(node.getChild(0));
        return code.getComputation();
    }

    private String visitReturn(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        JmmNode methodNode = TypeUtils.getParentMethod(node);
        // Handle case where return is not inside a method (should not happen in valid code)
        if (methodNode == null) return "// ERROR: Return statement outside method\n";

        String methodSig = methodNode.get("name");
        Type retType = table.getReturnType(methodSig);
        String ollirRetType = ollirTypes.toOllirType(retType);

        if (ollirRetType.equals(".V")) {
            code.append(RETURN).append(ollirRetType).append(END_STMT);
        } else {
            if (node.getNumChildren() == 0) {
                return "// ERROR: Non-void method missing return value\n";
            }
            JmmNode retExprNode = node.getChild(0);
            var exprResult = exprVisitor.visit(retExprNode);
            code.append(exprResult.getComputation());
            code
                    .append(RETURN)
                    .append(ollirRetType)
                    .append(SPACE)
                    .append(exprResult.getRef())
                    .append(END_STMT);
        }
        return code.toString();
    }

    private String visitParam(JmmNode node, Void unused) {
        var typeCode = ollirTypes.toOllirType(TypeUtils.convertType(node.getChild(0)));
        var id = node.get("name");
        return id + typeCode;
    }

    private String visitParamList(JmmNode node, Void unused) {
        return node
                .getChildren()
                .stream()
                .map(paramNode -> visit(paramNode, null))
                .collect(Collectors.joining(", "));
    }

    private String visitMethodDecl(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder(".method ");
        boolean isPublic = node.getBoolean("isPublic", false);
        var name = node.get("name");
        boolean isStatic = name.equals("main");

        if (isPublic) code.append("public ");
        if (isStatic) code.append("static ");

        if (node.hasAttribute("varargs") && node.getObject("varargs", Boolean.class)) {
            code.append("varargs ");
        }

        code.append(name);
        code.append("(");
        if (name.equals("main")) {
            code.append("args.array.String");
        } else {
            node.getChildren(PARAM_LIST).stream().findFirst().ifPresent(paramList -> code.append(visit(paramList)));
        }
        code.append(")");

        // Obter o tipo de retorno da SymbolTable
        Type methodReturnType = table.getReturnType(name);
        if (methodReturnType == null) {
            // Fallback ou erro se tipo não encontrado
            System.err.println("Error: Return type not found for method " + name);
            methodReturnType = new Type("void", false); // Assumir void em caso de erro?
        }
        var ollirRetType = ollirTypes.toOllirType(methodReturnType);
        code.append(ollirRetType);
        code.append(L_BRACKET);

        // Gerar código para o corpo do método
        var stmtsCode = node
                .getChildren()
                .stream()
                .filter(child -> !Kind.VAR_DECL.check(child) && !Kind.PARAM_LIST.check(child) && !Kind.TYPE.check(child))
                .map(this::visit)
                .collect(Collectors.joining());

        code.append(stmtsCode);

        // Garante que há uma instrução antes do '}'
        if (ollirRetType.equals(".V")) {
            // Adicionar newline se necessário antes do ret
            if (!stmtsCode.endsWith(NL) && !stmtsCode.isEmpty()) {
                code.append(NL);
            }
            code.append(RETURN).append(".V").append(END_STMT);
        }

        code.append(R_BRACKET);
        code.append(NL);
        return code.toString();
    }

    private String visitImportDecl(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        code.append(IMPORT).append(SPACE);
        String pathString = node.getOptional("path").orElse("");
        if (!pathString.isEmpty()) {
            String cleanedPath = pathString.replace("[", "").replace("]", "").replace(" ", "");
            if (!cleanedPath.isEmpty()) {
                String[] pathParts = cleanedPath.split(",");
                code.append(String.join(".", pathParts)).append(".");
            }
        }
        code.append(node.get("name")).append(END_STMT);
        return code.toString();
    }

    private String visitClass(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        code.append(table.getClassName());
        boolean isSub = node.getBoolean("isSub", false);
        if (isSub) {
            String superClass = node.get("parent");
            code.append(SPACE).append(EXTENDS).append(SPACE).append(superClass);
        }
        code.append(L_BRACKET);
        code.append(NL);

        for (var child : node.getChildren(VAR_DECL)) {
            code.append(visit(child));
        }
        code.append(NL);
        code.append(buildConstructor());
        code.append(NL);
        for (var child : node.getChildren(METHOD_DECL)) {
            code.append(visit(child));
        }
        code.append(R_BRACKET);
        return code.toString();
    }

    private String buildConstructor() {
        return (
                ".construct " + table.getClassName() + "().V" + L_BRACKET +
                        INVOKE + SPECIAL + L_PARENTHESIS + THIS + ", \"<init>\").V" + END_STMT +
                        R_BRACKET + NL
        );
    }

    private String visitProgram(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        node.getChildren(IMPORT_DECL).stream().map(this::visit).forEach(code::append);
        node.getChildren(CLASS_DECL).stream().map(this::visit).forEach(code::append);
        return code.toString();
    }

    private String defaultVisit(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        for (var child : node.getChildren()) {
            code.append(visit(child, unused));
        }
        return code.toString();
    }
}
