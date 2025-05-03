package pt.up.fe.comp2025.optimization;

import java.util.Collections;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp2025.CompilerConfig;

public class JmmOptimizationImpl implements JmmOptimization {

    @Override
    public OllirResult toOllir(JmmSemanticsResult semanticsResult) {

        // Create visitor that will generate the OLLIR code
        var visitor = new OllirGeneratorVisitor(semanticsResult.getSymbolTable());

        // Visit the AST and obtain OLLIR code
        var ollirCode = visitor.visit(semanticsResult.getRootNode());

        //System.out.println("\nOLLIR:\n\n" + ollirCode);

        return new OllirResult(semanticsResult, ollirCode, Collections.emptyList());
    }

    @Override
    public JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult) {

        //TODO: Do your AST-based optimizations here
        if(CompilerConfig.getOptimize(semanticsResult.getConfig())){

            // do the optimizations
            ConstantFolding constantFolding = new ConstantFolding();
            do {
                constantFolding.setHasModified(false);
                constantFolding.visit(semanticsResult.getRootNode());
            } while (constantFolding.hasModified());
        }

        return semanticsResult;
    }

    @Override
    public OllirResult optimize(OllirResult ollirResult) {

        //TODO: Register allocation

        int maxRegs = Integer.parseInt(ollirResult.getConfig().getOrDefault("registerAllocation", "-1"));

        if(maxRegs >= 0) {
            RegisterAllocator regAlloc = new RegisterAllocator(ollirResult, maxRegs);
            regAlloc.optimizeRegisters();
        }

        return ollirResult;
    }


}
