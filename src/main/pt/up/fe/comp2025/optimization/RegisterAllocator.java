package pt.up.fe.comp2025.optimization;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;
import pt.up.fe.comp.jmm.ollir.OllirResult;

import java.util.*;

/**
 * Register allocator using graph coloring.
 */
public class RegisterAllocator {
    private final OllirResult ollirResult;
    private final int maxRegisters;

    public RegisterAllocator(OllirResult ollirResult, int maxRegisters) {
        this.ollirResult = ollirResult;
        this.maxRegisters = maxRegisters;
    }

    public void optimizeRegisters() {
        ClassUnit classUnit = ollirResult.getOllirClass();

        // Build CFGs for all methods
        classUnit.buildCFGs();

        // Process each method in the class
        for (Method method : classUnit.getMethods()) {
            // Skip methods with no variables to optimize
            if (method.getVarTable().isEmpty()) {
                continue;
            }

            optimizeRegistersForMethod(method);
        }
    }

    private void optimizeRegistersForMethod(Method method) {
        // Get method instructions
        List<Instruction> instructions = method.getInstructions();
        if (instructions.isEmpty()) {
            return; // Skip methods with no instructions
        }

        // 1. Perform liveness analysis
        Map<Instruction, Set<String>> defSets = new HashMap<>();
        Map<Instruction, Set<String>> useSets = new HashMap<>();
        Map<Instruction, Set<String>> inSets = new HashMap<>();
        Map<Instruction, Set<String>> outSets = new HashMap<>();

        computeDefUse(method, defSets, useSets);
        performLivenessAnalysis(method, defSets, useSets, inSets, outSets);

        // 2. Build interference graph
        Map<String, Set<String>> interferenceGraph = buildInterferenceGraph(method, defSets, outSets);

        // 3. Apply graph coloring
        Map<String, Integer> colorAssignment = colorGraph(interferenceGraph, method);

        // 4. Update register allocation
        updateRegisterAllocation(method, colorAssignment);
    }

    private void computeDefUse(Method method, Map<Instruction, Set<String>> defSets, Map<Instruction, Set<String>> useSets) {
        for (Instruction instruction : method.getInstructions()) {
            Set<String> defSet = new HashSet<>();
            Set<String> useSet = new HashSet<>();

            if (instruction instanceof AssignInstruction) {
                AssignInstruction assignInst = (AssignInstruction) instruction;
                // The assigned variable is defined
                if (assignInst.getDest() instanceof Operand) {
                    Operand dest = (Operand) assignInst.getDest();
                    if (dest.getName() != null) {
                        defSet.add(dest.getName());
                    }
                }

                // Process the right-hand side as uses
                extractUsesFromInstruction(assignInst.getRhs(), useSet);
            } else {
                // For other instruction types, all operands are considered used
                extractUsesFromInstruction(instruction, useSet);
            }

            defSets.put(instruction, defSet);
            useSets.put(instruction, useSet);
        }
    }

    private void extractUsesFromInstruction(Instruction instruction, Set<String> useSet) {
        if (instruction instanceof SingleOpInstruction) {
            SingleOpInstruction singleOp = (SingleOpInstruction) instruction;
            extractUseFromElement(singleOp.getSingleOperand(), useSet);

        } else if (instruction instanceof BinaryOpInstruction) {
            BinaryOpInstruction binaryOp = (BinaryOpInstruction) instruction;
            extractUseFromElement(binaryOp.getLeftOperand(), useSet);
            extractUseFromElement(binaryOp.getRightOperand(), useSet);

        } else if (instruction instanceof UnaryOpInstruction) {
            UnaryOpInstruction unaryOp = (UnaryOpInstruction) instruction;
            extractUseFromElement(unaryOp.getOperand(), useSet);

        } else if (instruction instanceof CallInstruction) {
            CallInstruction callInst = (CallInstruction) instruction;

            Element caller = callInst.getCaller();
            if (caller != null) {
                extractUseFromElement(caller, useSet);
            }

            for (Element arg : callInst.getArguments()) {
                extractUseFromElement(arg, useSet);
            }

        } else if (instruction instanceof ReturnInstruction) {
            ReturnInstruction returnInst = (ReturnInstruction) instruction;
            returnInst.getOperand().ifPresent(op -> extractUseFromElement(op, useSet));

        } else if (instruction instanceof CondBranchInstruction) {
            CondBranchInstruction condInst = (CondBranchInstruction) instruction;
            for (Element element : condInst.getOperands()) {
                extractUseFromElement(element, useSet);
            }

        } else if (instruction instanceof PutFieldInstruction) {
            PutFieldInstruction putField = (PutFieldInstruction) instruction;
            List<Element> operands = putField.getOperands();

            // Usually: [targetObject, fieldName (unused), value]
            if (operands.size() > 0) extractUseFromElement(operands.get(0), useSet); // target object
            if (operands.size() > 2) extractUseFromElement(operands.get(2), useSet); // value to store

        } else if (instruction instanceof GetFieldInstruction) {
            GetFieldInstruction getField = (GetFieldInstruction) instruction;
            List<Element> operands = getField.getOperands();

            // Usually: [targetObject, fieldName]
            if (operands.size() > 0) extractUseFromElement(operands.get(0), useSet); // target object
        }
    }

    private void extractUseFromElement(Element element, Set<String> useSet) {
        if (element instanceof Operand) {
            Operand operand = (Operand) element;
            if (operand.getName() != null) {
                useSet.add(operand.getName());
            }
        } else if (element instanceof ArrayOperand) {
            ArrayOperand arrayOp = (ArrayOperand) element;
            if (arrayOp.getName() != null) {
                useSet.add(arrayOp.getName());
            }
            for (Element indexElement : arrayOp.getIndexOperands()) {
                extractUseFromElement(indexElement, useSet);
            }
        }
    }

    private void performLivenessAnalysis(Method method, Map<Instruction, Set<String>> defSets,
                                         Map<Instruction, Set<String>> useSets,
                                         Map<Instruction, Set<String>> inSets,
                                         Map<Instruction, Set<String>> outSets) {
        // Initialize empty sets for all instructions
        for (Instruction instruction : method.getInstructions()) {
            inSets.put(instruction, new HashSet<>());
            outSets.put(instruction, new HashSet<>());
        }

        boolean changed;
        do {
            changed = false;

            // Iterate through instructions in reverse order (bottom-up)
            List<Instruction> instructions = method.getInstructions();
            for (int i = instructions.size() - 1; i >= 0; i--) {
                Instruction instruction = instructions.get(i);

                // Calculate new out set (union of all in sets of successors)
                Set<String> newOutSet = new HashSet<>();
                for (Node successor : instruction.getSuccessors()) {
                    newOutSet.addAll(inSets.getOrDefault(successor, new HashSet<>()));
                }

                // Calculate new in set (use + (out - def))
                Set<String> newInSet = new HashSet<>(useSets.getOrDefault(instruction, new HashSet<>()));
                Set<String> outMinusDef = new HashSet<>(newOutSet);
                outMinusDef.removeAll(defSets.getOrDefault(instruction, new HashSet<>()));
                newInSet.addAll(outMinusDef);

                // Check if there were changes to in or out sets
                if (!newOutSet.equals(outSets.get(instruction)) || !newInSet.equals(inSets.get(instruction))) {
                    changed = true;
                    outSets.put(instruction, newOutSet);
                    inSets.put(instruction, newInSet);
                }
            }
        } while (changed);
    }

    private Map<String, Set<String>> buildInterferenceGraph(Method method,
                                                            Map<Instruction, Set<String>> defSets,
                                                            Map<Instruction, Set<String>> outSets) {
        Map<String, Set<String>> interferenceGraph = new HashMap<>();

        // Initialize empty sets for all variables in the method
        for (String varName : method.getVarTable().keySet()) {
            interferenceGraph.put(varName, new HashSet<>());
        }

        // For each instruction, variables in (def ∪ out) interfere with each other
        for (Instruction instruction : method.getInstructions()) {
            Set<String> defSet = defSets.getOrDefault(instruction, new HashSet<>());
            Set<String> outSet = outSets.getOrDefault(instruction, new HashSet<>());

            // Union of def and out sets
            Set<String> interferingVars = new HashSet<>(defSet);
            interferingVars.addAll(outSet);

            // Add interferences between all pairs of variables in the set
            List<String> varList = new ArrayList<>(interferingVars);
            for (int i = 0; i < varList.size(); i++) {
                for (int j = i + 1; j < varList.size(); j++) {
                    String var1 = varList.get(i);
                    String var2 = varList.get(j);

                    // Skip if either variable should be excluded from interference
                    if (shouldExcludeFromAllocation(var1, method) || shouldExcludeFromAllocation(var2, method)) {
                        continue;
                    }

                    // Add edges to the interference graph
                    interferenceGraph.computeIfAbsent(var1, k -> new HashSet<>()).add(var2);
                    interferenceGraph.computeIfAbsent(var2, k -> new HashSet<>()).add(var1);
                }
            }
        }

        return interferenceGraph;
    }

    private boolean shouldExcludeFromAllocation(String varName, Method method) {
        // Skip 'this' register
        if (varName.equals("this")) {
            return true;
        }

        // Skip method parameters (they already have registers assigned)
        Descriptor descriptor = method.getVarTable().get(varName);
        if (descriptor != null && descriptor.getScope() == VarScope.PARAMETER) {
            return true;
        }

        return false;
    }

    private Map<String, Integer> colorGraph(Map<String, Set<String>> interferenceGraph, Method method) {
        Map<String, Integer> colorAssignment = new HashMap<>();

        // Exclude 'this' and parameters from coloring, keeping their original registers
        for (Map.Entry<String, Descriptor> entry : method.getVarTable().entrySet()) {
            String varName = entry.getKey();
            Descriptor descriptor = entry.getValue();

            if (varName.equals("this") || descriptor.getScope() == VarScope.PARAMETER) {
                colorAssignment.put(varName, descriptor.getVirtualReg());
            }
        }

        // Make a copy of the graph for manipulation
        Map<String, Set<String>> workingGraph = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : interferenceGraph.entrySet()) {
            if (!colorAssignment.containsKey(entry.getKey())) {
                workingGraph.put(entry.getKey(), new HashSet<>(entry.getValue()));
            }
        }

        // Stack to store removed nodes
        Stack<String> removedNodes = new Stack<>();

        // Remove nodes with degree < maxRegisters
        while (!workingGraph.isEmpty()) {
            String nodeToRemove = null;

            // Try to find a node with degree < maxRegisters
            for (String node : workingGraph.keySet()) {
                if (workingGraph.get(node).size() < maxRegisters) {
                    nodeToRemove = node;
                    break;
                }
            }

            // If no suitable node found, spill a node (choose the highest degree node)
            if (nodeToRemove == null) {
                int maxDegree = -1;

                for (String node : workingGraph.keySet()) {
                    if (workingGraph.get(node).size() > maxDegree) {
                        maxDegree = workingGraph.get(node).size();
                        nodeToRemove = node;
                    }
                }
            }

            // Remove the selected node
            if (nodeToRemove != null) {
                // Add to stack
                removedNodes.push(nodeToRemove);

                // Remove from neighbors' adjacency lists
                for (String neighbor : workingGraph.get(nodeToRemove)) {
                    workingGraph.get(neighbor).remove(nodeToRemove);
                }

                // Remove from working graph
                workingGraph.remove(nodeToRemove);
            }
        }

        // Color the nodes
        while (!removedNodes.isEmpty()) {
            String node = removedNodes.pop();

            // Get neighbors and their colors
            Set<Integer> neighborColors = new HashSet<>();
            for (String neighbor : interferenceGraph.getOrDefault(node, new HashSet<>())) {
                if (colorAssignment.containsKey(neighbor)) {
                    neighborColors.add(colorAssignment.get(neighbor));
                }
            }

            // Find the smallest available color (register)
            int color = 0;
            while (neighborColors.contains(color) && color < maxRegisters) {
                color++;
            }

            // Assign color to node (if no color available, it will get maxRegisters-1)
            colorAssignment.put(node, color < maxRegisters ? color : maxRegisters - 1);
        }

        return colorAssignment;
    }

    private void updateRegisterAllocation(Method method, Map<String, Integer> colorAssignment) {
        Map<String, Descriptor> varTable = method.getVarTable();

        // Update register assignments
        for (Map.Entry<String, Integer> entry : colorAssignment.entrySet()) {
            String varName = entry.getKey();
            int register = entry.getValue();

            if (varTable.containsKey(varName)) {
                varTable.get(varName).setVirtualReg(register);
            }
        }
    }
}