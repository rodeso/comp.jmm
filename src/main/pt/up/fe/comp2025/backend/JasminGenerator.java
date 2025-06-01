package pt.up.fe.comp2025.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;
import org.specs.comp.ollir.tree.TreeNode;
import org.specs.comp.ollir.type.ArrayType;
import org.specs.comp.ollir.type.BuiltinType;
import org.specs.comp.ollir.type.ClassType;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.lang.annotation.ElementType;
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
        generators.put(InvokeSpecialInstruction.class,this::generateInvokeSpecialInst);
        generators.put(InvokeVirtualInstruction.class,this::generateInvokeVirtual);
        generators.put(ArrayLengthInstruction.class,this::generateArrayLength);
        generators.put(ArrayOperand.class, this::generateArrayLoad);

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
            // Peephole optimization for iinc: replace iload, iconst_1, iadd, istore, iload, istore patterns
            code = optimizeIinc(code);
        }

        return code;
    }

    /**
     * Peephole optimization to replace increment patterns with iinc instruction.
     * Looks for sequences: iload_N, iconst_1, iadd, istore_T, iload_T, istore_N -> iinc N 1
     */
    private String optimizeIinc(String code) {
        var sb = new StringBuffer();
        // Regex pattern matching the six-instruction sequence with optional leading whitespace
        String patternStr = "(?m)^[ \\t]*iload_(\\d+)[ \\t]*\\r?\\n" +
                            "[ \\t]*iconst_1[ \\t]*\\r?\\n" +
                            "[ \\t]*iadd[ \\t]*\\r?\\n" +
                            "[ \\t]*istore_(\\d+)[ \\t]*\\r?\\n" +
                            "[ \\t]*iload_\\2[ \\t]*\\r?\\n" +
                            "[ \\t]*istore_\\1";
        var pattern = java.util.regex.Pattern.compile(patternStr);
        var matcher = pattern.matcher(code);
        while (matcher.find()) {
            String reg = matcher.group(1); // the original variable register
            matcher.appendReplacement(sb, "iinc " + reg + " 1");
        }
        matcher.appendTail(sb);
        return sb.toString();
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
        this.stackLimitIncrement(1);
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
        this.stackLimitIncrement(1); // for const
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
            this.regLimitIncrement(method.getParams().size());
        }
        else {
            this.regLimitIncrement(method.getParams().size()-1);
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
        var lhs = assign.getDest();

        if (lhs instanceof Operand && assign.getRhs() instanceof BinaryOpInstruction) {
            Operand destOperand = (Operand) lhs;
            BinaryOpInstruction binaryOp = (BinaryOpInstruction) assign.getRhs();

            // Check if destination is an integer and operation is ADD
            if (types.getJasminType(destOperand.getType()).equals("I") &&
                binaryOp.getOperation().getOpType() == OperationType.ADD) {

                Operand varOpInBinary = null;
                LiteralElement constLiteralInBinary = null;

                Element leftOfBinary = binaryOp.getLeftOperand();
                Element rightOfBinary = binaryOp.getRightOperand();

                if (leftOfBinary instanceof Operand && ((Operand) leftOfBinary).getName().equals(destOperand.getName()) &&
                    rightOfBinary instanceof LiteralElement && types.getJasminType(rightOfBinary.getType()).equals("I")) {
                    varOpInBinary = (Operand) leftOfBinary; // This is destOperand
                    constLiteralInBinary = (LiteralElement) rightOfBinary;
                }
                else if (rightOfBinary instanceof Operand && ((Operand) rightOfBinary).getName().equals(destOperand.getName()) &&
                         leftOfBinary instanceof LiteralElement && types.getJasminType(leftOfBinary.getType()).equals("I")) {
                    varOpInBinary = (Operand) rightOfBinary; // This is destOperand
                    constLiteralInBinary = (LiteralElement) leftOfBinary;
                }

                if (varOpInBinary != null && constLiteralInBinary != null) {
                    try {
                        int incrementValue = Integer.parseInt(constLiteralInBinary.getLiteral());
                        if (incrementValue != 0 && incrementValue >= -128 && incrementValue <= 127) {
                            var varDescriptor = currentMethod.getVarTable().get(destOperand.getName());
                            if (varDescriptor != null) {
                                int regNum = varDescriptor.getVirtualReg();
                                this.regLimitIncrement(regNum);

                                code.append("iinc ").append(regNum).append(" ").append(incrementValue).append(NL);
                                return code.toString();
                            }
                        }
                    } catch (NumberFormatException e) {
                    }
                }
            }
        }

        if (lhs instanceof ArrayOperand) {
            ArrayOperand arrayOperand = (ArrayOperand) lhs;

            var arrayVarDescriptor = currentMethod.getVarTable().get(arrayOperand.getName());
            if (arrayVarDescriptor == null) {
                throw new RuntimeException("Array variable " + arrayOperand.getName() + " not found in VarTable for instruction: " + assign);
            }
            int arrayReg = arrayVarDescriptor.getVirtualReg();
            this.regLimitIncrement(arrayReg);
            this.stackLimitIncrement(1);
            String arrayLoadInstruction;
            if (arrayReg >= 0 && arrayReg <= 3) {
                arrayLoadInstruction = "aload_" + arrayReg;
            } else {
                arrayLoadInstruction = "aload " + arrayReg;
            }
            code.append(arrayLoadInstruction).append(NL);
            code.append(apply(arrayOperand.getIndexOperands().get(0))); // Index is an Element
            code.append(apply(assign.getRhs())); // RHS is an Element
            org.specs.comp.ollir.type.Type elementType = arrayOperand.getType();
            String jasminElementType = types.getJasminType(elementType);
            if (jasminElementType.equals("I") || jasminElementType.equals("Z")) {
                code.append("iastore").append(NL);
            } else {
                code.append("aastore").append(NL);
            }
            this.stackLimitIncrement(-3);

        } else if (lhs instanceof Operand) {
            code.append(apply(assign.getRhs()));
            Operand operand = (Operand) lhs;
            var varDescriptor = currentMethod.getVarTable().get(operand.getName());
            if (varDescriptor == null) {
                 throw new RuntimeException("Variable " + operand.getName() + " not found in VarTable for instruction: " + assign);
            }
            int regNum = varDescriptor.getVirtualReg();
            var jasminType = types.getJasminType(operand.getType());
            var prefix = types.getPrefix(jasminType); // 'i' for int, 'a' for ref, etc.
            code.append(generateStores(prefix, regNum)).append(NL);

        } else {
            throw new NotImplementedException("Unsupported LHS for assignment: " + lhs.getClass().getName() + " in instruction: " + assign);
        }
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

        code.append(apply(singleOpCondInstruction.getCondition()));
        code.append("ifne ").append(singleOpCondInstruction.getLabel());
        this.stackLimitIncrement(-1);
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




        code.append(apply(binaryOp.getLeftOperand()));
        code.append(apply(binaryOp.getRightOperand()));

        // TODO: Hardcoded for int type, needs to be expanded

        var typePrefix = "i";

        this.stackLimitIncrement(-1);
        // apply operation
        if(binaryOp.getOperation().getOpType() == OperationType.LTH){
            String ifInst ="if_icmplt";

            if(binaryOp.getRightOperand().isLiteral() && Integer.parseInt(((LiteralElement)binaryOp.getRightOperand()).getLiteral()) ==0){
                ifInst = "iflt";
            }
            int tagNum = types.getTagForIf_icmplt();
            code.append(ifInst).append(" ").append("j_true_").append(tagNum).append(NL)
                    .append("iconst_0").append(NL).append("goto ").append("j_end").append(tagNum).append(NL)
                    .append("j_true_").append(tagNum).append(":").append(NL)
                    .append("iconst_1").append(NL)
                    .append("j_end").append(tagNum).append(":").append(NL);
            return code.toString();
        }

        if(binaryOp.getOperation().getOpType() == OperationType.GTE){
            String ifInst ="if_icmpge";


            int tagNum = types.getTagForIf_icmplt();
            code.append(ifInst).append(" ").append("j_true_").append(tagNum).append(NL)
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
        if(returnType.startsWith("[") || returnType.startsWith("L")){

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

        if(!returnType.equals("return")){
            this.stackLimitIncrement(-1);
        }

        return code.toString();
    }

    private String generateNewInstruction(NewInstruction newInstruction) {
        var code = new StringBuilder();

        // Get the class name to instantiate


        var returnType = newInstruction.getReturnType();
        if(returnType instanceof ArrayType){
            for(var arg : newInstruction.getArguments()){
                code.append(apply(arg));
            }
            code.append("newarray int").append(NL);
            this.stackLimitIncrement(-1);
            this.stackLimitIncrement(1);
            return code.toString();
        }

        // Generate Jasmin code for object creation
        var className = ((ClassType) returnType).getName();
        code.append("new ").append(className).append(NL);


        this.stackLimitIncrement(1);
        return code.toString();
    }

    private String generateInvokeStatic(InvokeStaticInstruction invokeStaticInstruction){
        StringBuilder code = new StringBuilder();

        for(var args : invokeStaticInstruction.getArguments()){
            code.append(apply(args));
        }

        var className = ((Operand)invokeStaticInstruction.getCaller()).getName();

        for(var imp : ollirResult.getOllirClass().getImports()){
            String[] path = imp.split("\\.");
            if(path.length>0 && className.equals(path[path.length-1])){
                className = imp.replace(".","/");
            }
        }

        code.append("invokestatic ").append(className)
                .append("/").append(((LiteralElement)invokeStaticInstruction.getMethodName()).getLiteral()).append("(");

        for(var argsType : invokeStaticInstruction.getArguments()){
            code.append(types.getJasminType(argsType.getType()));
        }

        var returnType =types.getJasminType(invokeStaticInstruction.getReturnType());
        code.append(")").append(returnType).append(NL);

        this.stackLimitIncrement(-invokeStaticInstruction.getArguments().size());
        if(!returnType.equals("V")){
            this.stackLimitIncrement(1);
        }
        return code.toString();
    }


    private String generateInvokeSpecialInst(InvokeSpecialInstruction invokeSpecialInstruction) {
        StringBuilder code = new StringBuilder();
        Operand caller = (Operand) invokeSpecialInstruction.getCaller();
        ClassType type = (ClassType) caller.getType();
        var reg = currentMethod.getVarTable().get(caller.getName()).getVirtualReg();
        // Load the object reference
        code.append("aload_").append(reg).append(NL);

        // Get method and class details

        var className = type.getName() ;
        var arguments = invokeSpecialInstruction.getArguments();

        // Load arguments
        for (var arg : arguments) {
            code.append(apply(arg));
        }

        for(var imp : ollirResult.getOllirClass().getImports()){
            String[] path = imp.split("\\.");
            if(path.length>0 && className.equals(path[path.length-1])){
                className = imp.replace(".","/");
            }
        }

        // Generate invokespecial instruction
        code.append("invokespecial ").append(className).append("/")
                .append(((LiteralElement)invokeSpecialInstruction.getMethodName()).getLiteral()).append("(");

        for (var arg : arguments) {
            code.append(types.getJasminType(arg.getType()));
        }

        var returnType = types.getJasminType(invokeSpecialInstruction.getReturnType());
        code.append(")").append(returnType).append(NL);
        this.stackLimitIncrement(-invokeSpecialInstruction.getArguments().size());
        if(!returnType.equals("V")){
            this.stackLimitIncrement(1);
        }

        return code.toString();
    }

    private String generateInvokeVirtual(InvokeVirtualInstruction invokeVirtualInstruction){
        StringBuilder code = new StringBuilder();

        var caller = ((Operand) invokeVirtualInstruction.getCaller());

        code.append(apply(caller));

        for(var arg : invokeVirtualInstruction.getArguments()){
            code.append(apply(arg));
        }

        var className = ((ClassType)invokeVirtualInstruction.getCaller().getType()).getName();

        for(var imp : ollirResult.getOllirClass().getImports()){
            String[] path = imp.split("\\.");
            if(path.length>0 && className.equals(path[path.length-1])){
                className = imp.replace(".","/");
            }
        }

        code.append("invokevirtual ").append(className)
                .append("/").append(((LiteralElement)invokeVirtualInstruction.getMethodName()).getLiteral()).append("(");

        for(var argsType : invokeVirtualInstruction.getArguments()){
            code.append(types.getJasminType(argsType.getType()));
        }

        var returnType =types.getJasminType(invokeVirtualInstruction.getReturnType());
        code.append(")").append(returnType).append(NL);

        this.stackLimitIncrement(-invokeVirtualInstruction.getArguments().size());
        if(!returnType.equals("V")){
            this.stackLimitIncrement(1);
        }



        return code.toString();
    }


    private String generateArrayLength(ArrayLengthInstruction arrayLengthInstruction){
        StringBuilder code = new StringBuilder();

        var caller = arrayLengthInstruction.getCaller();

        code.append(apply(caller));

        code.append("arraylength").append(NL);
        // arraylength consumes 1 (arrayref), pushes 1 (length). Net 0.
        // apply(caller) already incremented stack by 1 for arrayref.
        // So no net change to stack height needed here after arraylength itself.

        return code.toString();
    }

    private String generateArrayLoad(ArrayOperand arrayOperand) {
        StringBuilder code = new StringBuilder();

        // 1. Load array reference onto the stack
        var arrayVarDescriptor = currentMethod.getVarTable().get(arrayOperand.getName());
        if (arrayVarDescriptor == null) {
            throw new RuntimeException("Array variable \"" + arrayOperand.getName() + "\" not found in VarTable for loading element from: " + arrayOperand);
        }
        int arrayReg = arrayVarDescriptor.getVirtualReg();
        this.regLimitIncrement(arrayReg); // Ensure reg is considered for .limit locals

        this.stackLimitIncrement(1);      // Array reference pushed to stack (aload)
        if (arrayReg >= 0 && arrayReg <= 3) {
            code.append("aload_").append(arrayReg).append(NL);
        } else {
            code.append("aload ").append(arrayReg).append(NL);
        }

        // 2. Load index onto the stack
        // apply() on the index Element will handle its loading and stack increment
        code.append(apply(arrayOperand.getIndexOperands().get(0)));

        // 3. Perform array load instruction (iaload or aaload)
        // arrayOperand.getType() gives the Type of the element being loaded.
        org.specs.comp.ollir.type.Type elementType = arrayOperand.getType();
        String jasminElementType = types.getJasminType(elementType);

        // iaload/aaload consumes arrayref and index from stack (-2 from current height),
        // then pushes the element (+1). Net effect on stack height: -1.
        this.stackLimitIncrement(-1); 

        if (jasminElementType.equals("I") || jasminElementType.equals("Z")) { // Integer or Boolean
            code.append("iaload").append(NL);
        } else { // Object references
            code.append("aaload").append(NL);
        }

        return code.toString();
    }
}