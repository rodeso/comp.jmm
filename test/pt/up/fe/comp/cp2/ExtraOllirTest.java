package pt.up.fe.comp.cp2;

import org.junit.Test;
import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;
import pt.up.fe.comp.CpUtils;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.specs.util.SpecsIo;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ExtraOllirTest {
    private static final String BASE_PATH = "pt/up/fe/comp/cp2/ollir/";

    static OllirResult getOllirResult(String filename) {
        return CpUtils.getOllirResult(SpecsIo.getResource(BASE_PATH + filename), Collections.emptyMap(), false);
    }

    @Test
    public void testArrayArgsReturn() {
        var result = getOllirResult("extra/ArrayArgsReturn.jmm");
        System.out.println("---------------------- ArrayArgsReturn OLLIR ----------------------");
        System.out.println(result.getOllirCode());
        System.out.println("------------------------------------------------------------------");

        // Test method signature and return type
        var method = CpUtils.getMethod(result, "varArgsMethod");
        CpUtils.assertEquals("Method return type", "int[]", CpUtils.toString(method.getReturnType()), result);

        // Test if parameter exists
        var parameters = method.getParams();
        CpUtils.assertTrue("Method should have at least one parameter", parameters.size() > 0, result);

        // Check if parameter type contains "array" in its string representation
        String paramType = parameters.get(0).getType().toString();
        CpUtils.assertTrue("Parameter should be an array type: " + paramType,
                paramType.contains("array") || paramType.contains("Array"), result);
    }

    @Test
    public void testFieldAccess() {
        var result = getOllirResult("extra/FieldAccess.jmm");
        System.out.println("---------------------- FieldAccess OLLIR ----------------------");
        System.out.println(result.getOllirCode());
        System.out.println("--------------------------------------------------------------");

        // Check for field access instructions in methods
        var method = CpUtils.getMethod(result, "accessField");

        // Count getfield instructions
        int getFieldCount = 0;
        // Count putfield instructions
        int putFieldCount = 0;

        for (Instruction inst : method.getInstructions()) {
            if (inst instanceof GetFieldInstruction) {
                getFieldCount++;
            }
            if (inst instanceof PutFieldInstruction) {
                putFieldCount++;
            }
        }

        CpUtils.assertTrue("Should have at least one getfield instruction", getFieldCount >= 1, result);
        CpUtils.assertTrue("Should have at least one putfield instruction", putFieldCount >= 1, result);
    }

    @Test
    public void testMethodInArrayIndex() {
        var result = getOllirResult("extra/MethodInArrayIndex.jmm");
        System.out.println("---------------------- MethodInArrayIndex OLLIR ----------------------");
        System.out.println(result.getOllirCode());
        System.out.println("------------------------------------------------------------------");

        // Check if we have method calls inside array access
        var method = CpUtils.getMethod(result, "main");

        // Count call instructions
        int callCount = 0;
        for (Instruction inst : method.getInstructions()) {
            if (inst instanceof CallInstruction) {
                callCount++;
            }
        }

        CpUtils.assertTrue("Should have at least one method call", callCount >= 1, result);

        // Check for array accesses in assignments
        boolean hasArrayAccess = false;
        for (Instruction inst : method.getInstructions()) {
            if (inst instanceof AssignInstruction) {
                AssignInstruction assign = (AssignInstruction) inst;
                assign.getRhs();
                if (assign.getDest() instanceof ArrayOperand) {
                    hasArrayAccess = true;
                    break;
                }
            }
        }

        CpUtils.assertTrue("Should have at least one array access", hasArrayAccess, result);
    }

    @Test
    public void testNestedArrayAccess() {
        var result = getOllirResult("extra/NestedArrayAccess.jmm");
        System.out.println("---------------------- NestedArrayAccess OLLIR ----------------------");
        System.out.println(result.getOllirCode());
        System.out.println("------------------------------------------------------------------");

        // Check for array operations in code
        var method = CpUtils.getMethod(result, "accessNestedArray");

        // Count array store operations
        int arrayStoreCount = 0;
        for (Instruction inst : method.getInstructions()) {
            if (inst instanceof AssignInstruction) {
                AssignInstruction assign = (AssignInstruction) inst;
                if (assign.getDest() instanceof ArrayOperand) {
                    arrayStoreCount++;
                }
            }
        }

        CpUtils.assertTrue("Should have at least one array store operation", arrayStoreCount >= 1, result);
    }

    @Test
    public void testPrecedence() {
        var result = getOllirResult("extra/Precedence.jmm");
        System.out.println("---------------------- Precedence OLLIR ----------------------");
        System.out.println(result.getOllirCode());
        System.out.println("------------------------------------------------------------");

        // Check for binary operations in code
        var method = CpUtils.getMethod(result, "testPrecedence");

        // Count binary operations
        int binaryOpCount = 0;
        for (Instruction inst : method.getInstructions()) {
            if (inst instanceof AssignInstruction) {
                AssignInstruction assign = (AssignInstruction) inst;
                if (assign.getRhs() instanceof BinaryOpInstruction) {
                    binaryOpCount++;
                }
            }
        }

        CpUtils.assertTrue("Should have at least one binary operation", binaryOpCount >= 1, result);
    }
}