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

    private final SymbolTable table;
    private final TypeUtils types;
    private final OptUtils ollirTypes;
    private final OllirExprGeneratorVisitor exprVisitor;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = new OptUtils(types);
        // Pass the *same* SymbolTable to the expression visitor
        this.exprVisitor = new OllirExprGeneratorVisitor(table);
        buildVisitor(); // Call buildVisitor in the constructor
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
        // ELSE_STMT and ELSEIF_STMT are handled within visitIfStmt logic usually
        addVisit(SIMPLE_EXPR, this::visitSimpleExpr);
        addVisit(WHILE_STMT, this::visitWhileStmt);

        setDefaultVisit(this::defaultVisit);
    }

    private String visitVarDecl(JmmNode node, Void unused) {
        JmmNode parent = node.getParent();
        StringBuilder code = new StringBuilder();
        // Only generate .field for class-level declarations
        if (CLASS_DECL.check(parent)) {
            code.append(".field public "); // Assuming public fields for simplicity
            // Note: Access modifiers (private, protected) might need specific handling
            // based on language spec if they differ from Java.

            // Type conversion needs the Type node (child 0)
            Type type = TypeUtils.convertType(node.getChild(0));
            String ollirType = ollirTypes.toOllirType(type);
            code.append(node.get("name")).append(ollirType).append(END_STMT);
        }
        // Local variable declarations don't generate direct OLLIR code here,
        // they are implicitly handled by usage and the symbol table.
        return code.toString();
    }

    private String visitAssignStmt(JmmNode node, Void unused) {
        JmmNode lhsNode = node.getChild(0);
        JmmNode rhsNode = node.getChild(1);
        JmmNode methodNode = TypeUtils.getParentMethod(node);

        var rhsResult = exprVisitor.visit(rhsNode);
        StringBuilder code = new StringBuilder();
        code.append(rhsResult.getComputation()); // Computation for the right side

        // Determine LHS type and reference string
        Type lhsType = types.getExprTypeNotStatic(lhsNode, methodNode);
        String ollirLhsType = ollirTypes.toOllirType(lhsType);
        String lhsRef;

        // Handle Array Assignment: a[i] = ...
        if (lhsNode.isInstance(ARRAY_ACCESS)) {
            JmmNode arrayVarNode = lhsNode.getChild(0);
            JmmNode indexNode = lhsNode.getChild(1);

            // Evaluate array and index - needed for the assignment target
            var arrayResult = exprVisitor.visit(arrayVarNode);
            var indexResult = exprVisitor.visit(indexNode);
            code.append(arrayResult.getComputation());
            code.append(indexResult.getComputation());

            // Build the LHS reference string for array access
            lhsRef =
                    arrayResult.getRef() +
                            "[" +
                            indexResult.getRef() +
                            "]" +
                            ollirLhsType; // Type of the element being assigned

            // Assignment instruction
            code
                    .append(lhsRef)
                    .append(SPACE)
                    .append(ASSIGN)
                    .append(ollirLhsType)
                    .append(SPACE)
                    .append(rhsResult.getRef())
                    .append(END_STMT);

        }
        // Handle Field Assignment: this.a = ... or obj.a = ... (if supported)
        else if (types.isField(lhsNode)) {
            // Assuming assignment to 'this.field'
            String fieldName = lhsNode.get("name");
            code
                    .append(PUT_FIELD)
                    .append(L_PARENTHESIS)
                    .append(THIS)
                    .append(".")
                    .append(table.getClassName()) // Type of 'this'
                    .append(", ")
                    .append(fieldName)
                    .append(ollirLhsType) // Field name and type
                    .append(", ")
                    .append(rhsResult.getRef()) // Value to assign
                    .append(R_PARENTHESIS)
                    .append(".V") // putfield returns void
                    .append(END_STMT);
        }
        // Handle Local Variable/Parameter Assignment: x = ...
        else {
            lhsRef = lhsNode.get("name") + ollirLhsType; // Simple variable name + type
            code
                    .append(lhsRef)
                    .append(SPACE)
                    .append(ASSIGN)
                    .append(ollirLhsType)
                    .append(SPACE)
                    .append(rhsResult.getRef())
                    .append(END_STMT);
        }

        return code.toString();
    }

    private String visitWhileStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        String loopLabel = ollirTypes.nextWhileBranch("Loop");
        String condLabel = ollirTypes.nextWhileBranch("Cond");
        String endLabel = ollirTypes.nextWhileBranch("EndLoop");

        code.append(GOT_TO).append(SPACE).append(condLabel).append(END_STMT); // Jump to condition check first

        code.append(loopLabel).append(COLON); // Label for loop body
        // Visit the loop body statement (assuming it's the second child)
        code.append(visit(node.getChild(1)));

        code.append(condLabel).append(COLON); // Label for condition check
        // Visit the condition expression (first child)
        var conditionResult = exprVisitor.visit(node.getChild(0));
        code.append(conditionResult.getComputation());
        // If condition is true, jump back to loop body
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
        // If condition is false, fall through to endLabel (or add explicit goto EndLoop;)

        code.append(endLabel).append(COLON); // Label after the loop
        return code.toString();
    }

    private String visitIfStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        JmmNode conditionNode = node.getChild(0);
        JmmNode thenNode = node.getChild(1);
        // Check if there's an else part
        JmmNode elseNode = node.getNumChildren() > 2 ? node.getChild(2) : null;

        String thenLabel = ollirTypes.nextIfBranch("if_then");
        String endLabel = ollirTypes.nextIfBranch("if_end");
        String elseLabel = elseNode != null ? ollirTypes.nextIfBranch("if_else") : endLabel;

        // Evaluate condition
        var conditionResult = exprVisitor.visit(conditionNode);
        code.append(conditionResult.getComputation());

        // if (condition) goto thenLabel;
        code
                .append(IF)
                .append(L_PARENTHESIS)
                .append(conditionResult.getRef())
                .append(R_PARENTHESIS)
                .append(SPACE)
                .append(GOT_TO)
                .append(SPACE)
                .append(thenLabel)
                .append(END_STMT);

        // Code for the 'else' block (if it exists) or jump to end
        if (elseNode != null) {
            code.append(visit(elseNode)); // Generate else block code
            code.append(GOT_TO).append(SPACE).append(endLabel).append(END_STMT); // Jump past 'then' block
        } else {
            // No else block, fall through or jump directly to end if needed
            // code.append(GOTO + SPACE + endLabel + END_STMT); // Usually not needed if then block is next
        }

        // Code for the 'then' block
        code.append(thenLabel).append(COLON);
        code.append(visit(thenNode));
        // Fall through to endLabel after 'then' block

        code.append(endLabel).append(COLON); // Final label

        return code.toString();
    }

    // visitElseStmt is usually not needed as its content is generated within visitIfStmt
    // private String visitElseStmt(JmmNode node, Void unused){ ... }

    private String visitBracketsStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        // Visit all statements within the brackets
        for (JmmNode child : node.getChildren()) {
            code.append(visit(child));
        }
        return code.toString();
    }

    private String visitSimpleExpr(JmmNode node, Void unused) {
        // Visit the expression, its computation contains the necessary OLLIR code
        var code = exprVisitor.visit(node.getChild(0));
        return code.getComputation();
    }

    private String visitReturn(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        String methodSig = TypeUtils.getParentMethod(node).get("name");
        Type retType = table.getReturnType(methodSig);
        String ollirRetType = ollirTypes.toOllirType(retType);

        // Handle void return
        if (ollirRetType.equals(".V")) {
            code.append(RETURN).append(ollirRetType).append(END_STMT);
        } else {
            // Handle return with value
            JmmNode retExprNode = node.getChild(0);
            var exprResult = exprVisitor.visit(retExprNode);
            code.append(exprResult.getComputation()); // Compute the return value
            code
                    .append(RETURN)
                    .append(ollirRetType)
                    .append(SPACE)
                    .append(exprResult.getRef()) // Reference to the return value
                    .append(END_STMT);
        }
        return code.toString();
    }

    private String visitParam(JmmNode node, Void unused) {
        // Child 0 is Type node
        var typeCode = ollirTypes.toOllirType(TypeUtils.convertType(node.getChild(0)));
        var id = node.get("name");
        return id + typeCode;
    }

    private String visitParamList(JmmNode node, Void unused) {
        return node
                .getChildren()
                .stream()
                .map(paramNode -> visit(paramNode, null)) // Pass null for Void context
                .collect(Collectors.joining(", "));
    }

    private String visitMethodDecl(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder(".method ");
        boolean isPublic = node.getBoolean("isPublic", false);
        var name = node.get("name");
        boolean isStatic = name.equals("main"); // Simple check for main, needs refinement for others
        // TODO: Check for 'static' keyword in method declaration if grammar supports it

        if (isPublic) {
            code.append("public ");
        }
        if (isStatic) {
            code.append("static ");
        }

        if (node.hasAttribute("varargs") && node.getObject("varargs", Boolean.class)) {
            code.append("varargs ");
        }


        code.append(name);

        // Parameters
        code.append("(");
        String paramsString = "";
        if (name.equals("main")) {
            // Main method has specific signature
            paramsString = "args.array.String";
        } else {
            // Find ParamList node
            node
                    .getChildren(PARAM_LIST)
                    .stream()
                    .findFirst()
                    .ifPresent(paramList -> code.append(visit(paramList)));
        }
        code.append(paramsString);
        code.append(")");

        // Return Type
        var retType = ollirTypes.toOllirType(table.getReturnType(name));
        code.append(retType);
        code.append(L_BRACKET);

        // Body (VarDeclarations are handled by usage, process Statements)
        var locals = table.getLocalVariables(name); // Needed for stack allocation later?
        var stmtsCode = node
                .getChildren()
                .stream()
                // Filter out VarDecl, ParamList, Type nodes, process only statements
                .filter(child ->
                        !Kind.VAR_DECL.check(child) &&
                                !Kind.PARAM_LIST.check(child) &&
                                !Kind.TYPE.check(child)
                )
                .map(this::visit)
                .collect(Collectors.joining()); // Collect statements sequentially

        code.append(stmtsCode);

        // Ensure return for void methods if not explicitly present
        if (retType.equals(".V") && !stmtsCode.contains(RETURN + ".V")) {
            if (!stmtsCode.endsWith(END_STMT) && !stmtsCode.isEmpty()) {
                code.append(NL); // Add newline if last statement didn't have one
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

        // Try to get the path attribute as a String (might be like "[comp, io]")
        String pathString = node.getOptional("path").orElse("");

        // Clean the path string (remove brackets and spaces) and join with dots
        if (!pathString.isEmpty()) {
            String cleanedPath = pathString.replace("[", "").replace("]", "").replace(" ", "");
            if (!cleanedPath.isEmpty()) {
                String[] pathParts = cleanedPath.split(",");
                code.append(String.join(".", pathParts)).append(".");
            }
        }

        // Append the final class name
        code.append(node.get("name")).append(END_STMT);
        return code.toString();
    }


    private String visitClass(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        // Imports first
        // Imports are handled at the Program level usually

        code.append(table.getClassName());
        boolean isSub = node.getBoolean("isSub", false);
        if (isSub) {
            String superClass = node.get("parent");
            code.append(SPACE).append(EXTENDS).append(SPACE).append(superClass);
        }
        code.append(L_BRACKET);
        code.append(NL);

        // Fields
        for (var child : node.getChildren(VAR_DECL)) {
            code.append(visit(child));
        }
        code.append(NL);

        // Constructor
        code.append(buildConstructor());
        code.append(NL);

        // Methods
        for (var child : node.getChildren(METHOD_DECL)) {
            code.append(visit(child));
        }

        code.append(R_BRACKET);
        return code.toString();
    }

    private String buildConstructor() {
        // Basic constructor, assumes no fields needing initialization here
        // If fields have initializers, they might need to be added.
        return (
                ".construct " +
                        table.getClassName() +
                        "().V" +
                        L_BRACKET +
                        "invokespecial(" +
                        THIS +
                        ", \"<init>\").V" +
                        END_STMT +
                        R_BRACKET +
                        NL
        );
    }

    private String visitProgram(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        // Process Imports first
        node
                .getChildren(IMPORT_DECL)
                .stream()
                .map(this::visit)
                .forEach(code::append);
        // Process Class Declaration
        node
                .getChildren(CLASS_DECL)
                .stream()
                .map(this::visit)
                .forEach(code::append);
        return code.toString();
    }

    /**
     * Default visitor. Visits every child node and returns an empty string.
     */
    private String defaultVisit(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        for (var child : node.getChildren()) {
            code.append(visit(child, unused)); // Pass unused context
        }
        return code.toString(); // Return collected code from children
    }
}
