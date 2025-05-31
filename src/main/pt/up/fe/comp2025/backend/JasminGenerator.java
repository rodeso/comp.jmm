package pt.up.fe.comp2025.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;
import org.specs.comp.ollir.tree.TreeNode;
import org.specs.comp.ollir.type.BuiltinType;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";

    private final OllirResult ollirResult;

    List<Report> reports;

    String code;

    Method currentMethod;

    private final JasminUtils types;

    private final FunctionClassMap<TreeNode, String> generators;

    private int stackLimit = 0;

    private int regLimit = 0;

    private int maxStackLimit =0;

    private int maxRegLimit=0;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;

        types = new JasminUtils(ollirResult);

        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(PutFieldInstruction.class, this::generatePutInst);
        generators.put(GetFieldInstruction.class,this::generateGetInst);
        generators.put(NewInstruction.class, this::generateNewInstruction);
        generators.put(OpCondInstruction.class,this::generateOpCondInst);
        generators.put(SingleOpCondInstruction.class, this::generateSingleOpCondInst);
        generators.put(GotoInstruction.class,this::generateGoto);
        generators.put(UnaryOpInstruction.class,this::generateUnaryInst);
        generators.put(InvokeStaticInstruction.class,this::generateInvokeStatic);

    }

    private void stackLimitIncrement(int value){
        this.stackLimit=this.stackLimit+value;
        this.maxStackLimit = Math.max(this.maxStackLimit,this.stackLimit);
    }

    private void regLimitIncrement(int value){
        this.regLimit=value+1;
        this.maxRegLimit = Math.max(this.maxRegLimit,this.regLimit);
    }

    private String generateStores(String type, int regNum){
        this.regLimitIncrement(regNum);
        this.stackLimitIncrement(-1);
        if(regNum>=0 && regNum <=3){
            return type+"store_"+regNum;
        }
        return  type+"store "+regNum;
    }
    private String apply(TreeNode node) {
        var code = new StringBuilder();

        // Print the corresponding OLLIR code as a comment
        //code.append("; ").append(node).append(NL);

        code.append(generators.apply(node));

        return code.toString();
    }


    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = apply(ollirResult.getOllirClass());
        }

        return code;
    }

    private String generatePutInst(PutFieldInstruction putFieldInstruction){
        StringBuilder code = new StringBuilder();
        this.stackLimitIncrement(1);
        var className = ollirResult.getOllirClass().getClassName();
        code.append("aload_0").append(NL);
        code.append(generators.apply(putFieldInstruction.getValue()));

        var field = putFieldInstruction.getField();
        code.append("putfield ").append(className).append("/").append(field.getName()).append(" ")
                .append(types.getJasminType(field.getType())).append(NL);

        this.stackLimitIncrement(-2);
        return code.toString();
    }

    private String generateGetInst(GetFieldInstruction getFieldInstruction){
        StringBuilder code = new StringBuilder();

        var className = ollirResult.getOllirClass().getClassName();
        code.append("aload_0").append(NL);

        var field = getFieldInstruction.getField();
        code.append("getfield ").append(className).append("/").append(field.getName()).append(" ")
                .append(types.getJasminType(field.getType())).append(NL);
        //code.append(store()); create func to store
        return code.toString();
    }

    private String generateUnaryInst(UnaryOpInstruction unaryOpInstruction){
        StringBuilder code = new StringBuilder();

        code.append(apply(unaryOpInstruction.getOperand()))
                .append("iconst_1").append(NL).append("ixor").append(NL);

        this.stackLimitIncrement(-1);

        return code.toString();
    }


    private String generateOpCondInst(OpCondInstruction opCondInstruction){
        StringBuilder code = new StringBuilder();

        code.append(apply(opCondInstruction.getCondition()));

        code.append("ifne ").append(opCondInstruction.getLabel());

        this.stackLimitIncrement(-1);

        return  code.toString();
    }


    private String generateClassUnit(ClassUnit classUnit) {

        var code = new StringBuilder();

        // generate class name
        var className = ollirResult.getOllirClass().getClassName();
        code.append(".class ").append(className).append(NL).append(NL);


        var imports = ollirResult.getOllirClass().getImports();
        var superClass = ollirResult.getOllirClass().getSuperClass();
        var fullSuperClass = "java/lang/Object";
        if(superClass!=null){

            for(String i : imports){
                if( types.isInImport(superClass,i)){
                    fullSuperClass = i;
                }
            }

        }
        code.append(".super ").append(fullSuperClass).append(NL);

        var fields = ollirResult.getOllirClass().getFields();

        for(var field : fields){
            code.append(".field public '").append(field.getFieldName()).append("' ").append(types.getJasminType(field.getFieldType())).append(NL);

        }
        // generate a single constructor method
        var defaultConstructor = """
                ;default constructor
                .method public <init>()V
                    aload_0
                    invokespecial %s/<init>()V
                    return
                .end method
                """.formatted(fullSuperClass);
        code.append(defaultConstructor);

        // generate code for all other methods
        for (var method : ollirResult.getOllirClass().getMethods()) {

            // Ignore constructor, since there is always one constructor
            // that receives no arguments, and has been already added
            // previously
            if (method.isConstructMethod()) {
                continue;
            }

            code.append(apply(method));
        }

        return code.toString();
    }


    private String generateMethod(Method method) {
        //System.out.println("STARTING METHOD " + method.getMethodName());
        // set method
        currentMethod = method;
        this.regLimit=0;
        this.stackLimit=0;
        this.maxStackLimit=0;
        this.maxRegLimit=0;

        var code = new StringBuilder();

        // calculate modifier
        var modifier = types.getModifier(method.getMethodAccessModifier());

        var staticMethod = method.isStaticMethod();

        var methodName = method.getMethodName();


        var params = new StringBuilder();


        for(Element param : currentMethod.getParams()){
            params.append(types.getJasminType(param.getType()));


        }

        this.regLimitIncrement(currentMethod.getParams().size()-1);


        var returnType = types.getJasminType(currentMethod.getReturnType());

        code.append("\n.method ").append(modifier);

        if(staticMethod){
            code.append("static ");
        }

        code.append(methodName)
                .append("(" + params + ")" + returnType).append(NL);

        if(!staticMethod){
            this.regLimitIncrement(0);
        }


        StringBuilder methodBody = new StringBuilder();
        for (var inst : method.getInstructions()) {
            var labels = method.getLabels(inst);

            for(var label : labels){
                methodBody.append(label).append(":\n");
            }
            var instCode = StringLines.getLines(apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            methodBody.append(instCode);
        }

        // Add limits
        code.append(TAB).append(".limit stack ").append(this.maxStackLimit).append(NL);
        code.append(TAB).append(".limit locals ").append(this.maxRegLimit).append(NL);
        code.append(methodBody);
        code.append(".end method\n");


        // unset method
        currentMethod = null;
        //System.out.println("ENDING METHOD " + method.getMethodName());
        return code.toString();
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();

        // generate code for loading what's on the right
        code.append(apply(assign.getRhs()));

        // store value in the stack in destination
        var lhs = assign.getDest();

        if (!(lhs instanceof Operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        var operand = (Operand) lhs;

        // get register
        var reg = currentMethod.getVarTable().get(operand.getName());

        var type = types.getJasminType(operand.getType());
        var prefix = types.getPrefix(type);


        code.append(generateStores(prefix,reg.getVirtualReg())).append(NL);

        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return apply(singleOp.getSingleOperand());
    }

    private String generateGoto(GotoInstruction gotoInstruction){
        StringBuilder code = new StringBuilder();
        code.append("goto ").append(gotoInstruction.getLabel()).append(NL);

        return code.toString();
    }
    private String generateSingleOpCondInst(SingleOpCondInstruction singleOpCondInstruction){
        StringBuilder code = new StringBuilder();
        this.stackLimitIncrement(-1);
        code.append(apply(singleOpCondInstruction.getCondition()));
        code.append("ifne ").append(singleOpCondInstruction.getLabel());

        return code.toString();
    }

    private String generateLiteral(LiteralElement literal) {
        this.stackLimitIncrement(1);
        var type = literal.getType();
        if(types.getJasminType(type).equals("Z")){

            return "iconst_"+literal.getLiteral()+NL;
        }
        if(types.getJasminType(type).equals("I")){
            var literalValue = Integer.parseInt(literal.getLiteral());
            if(literalValue == -1){
                return "iconst_m1"+NL;
            }
            if(literalValue >=0 && literalValue <=5){
                return "iconst_"+literal.getLiteral()+NL;
            }
            if(literalValue >=-128 && literalValue<=127){
                return "bipush "+literal.getLiteral()+NL;
            }
            if(literalValue >= -32768 && literalValue<=32767){
                return "sipush "+literal.getLiteral()+NL;
            }
        }
        return "ldc " + literal.getLiteral() + NL;
    }

    private String generateOperand(Operand operand) {
        // get register
        var reg = currentMethod.getVarTable().get(operand.getName());
        String prefix = types.getPrefix(types.getJasminType(operand.getType()));
        this.stackLimitIncrement(1);
        if(reg.getVirtualReg()>=0 && reg.getVirtualReg()<=3){
            return prefix+"load_"+reg.getVirtualReg()+NL;
        }
        return prefix +"load " + reg.getVirtualReg() + NL;
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        // load values on the left and on the right
        this.stackLimitIncrement(-2);



        code.append(apply(binaryOp.getLeftOperand()));
        code.append(apply(binaryOp.getRightOperand()));

        // TODO: Hardcoded for int type, needs to be expanded

        var typePrefix = "i";

        // apply operation
        if(binaryOp.getOperation().getOpType() == OperationType.LTH){

            int tagNum = types.getTagForIf_icmplt();
            code.append("if_icmplt ").append("j_true_").append(tagNum).append(NL)
                    .append("iconst_0").append(NL).append("goto ").append("j_end").append(tagNum).append(NL)
                    .append("j_true_").append(tagNum).append(":").append(NL)
                    .append("iconst_1").append(NL)
                    .append("j_end").append(tagNum).append(":").append(NL);
            return code.toString();
        }


        var op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "add";
            case MUL -> "mul";
            case SUB -> "sub";
            case DIV -> "div";

            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        code.append(typePrefix + op).append(NL);

        return code.toString();
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();
        var operand = returnInst.getOperand().orElse(null);
        // TODO: Hardcoded for int type, needs to be expanded
        String returnType =types.getJasminType(returnInst.getReturnType());
        if(returnType.startsWith("[")){

            code.append(apply(operand));
            returnType="areturn";
        }
        switch (returnType){
            case "I", "Z" ->{
                code.append(apply(operand));
                returnType="ireturn";
            }
            case "V" -> returnType="return";
        }
        code.append(returnType).append(NL);

        this.stackLimitIncrement(-1);

        return code.toString();
    }

    private String generateNewInstruction(NewInstruction newInstruction) {
        var code = new StringBuilder();

        // Get the class name to instantiate
        var className = newInstruction.getClass().getName().toString();

        // Generate Jasmin code for object creation
        code.append("new ").append(className).append(NL);
        code.append("dup").append(NL);
        code.append("invokespecial ").append(className).append("/<init>()V").append(NL);

        return code.toString();
    }

    private String generateInvokeStatic(InvokeStaticInstruction invokeStaticInstruction){
        StringBuilder code = new StringBuilder();

        for(var args : invokeStaticInstruction.getArguments()){
            code.append(apply(args));
        }

        code.append("invokestatic ").append(((Operand)invokeStaticInstruction.getCaller()).getName())
                .append("/").append(((LiteralElement)invokeStaticInstruction.getMethodName()).getLiteral()).append("(");

        for(var argsType : invokeStaticInstruction.getArguments()){
            code.append(types.getJasminType(argsType.getType()));
        }

        var returnType =types.getJasminType(invokeStaticInstruction.getReturnType());
        code.append(")").append(returnType);

        this.stackLimitIncrement(invokeStaticInstruction.getArguments().size());
        if(!returnType.equals("V")){
            this.stackLimitIncrement(1);
        }
        return code.toString();
    }
}