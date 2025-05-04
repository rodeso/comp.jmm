package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.ollir.OllirResult;
import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;
import org.specs.comp.ollir.type.*;

import java.util.*;

public class RegisterAllocator {
    private final OllirResult ollirResult;
    private final int maxRegisters;

    public RegisterAllocator(OllirResult ollirResult, int maxRegisters) {
        this.ollirResult = ollirResult;
        this.maxRegisters = maxRegisters;
    }

    public void optimizeRegisters() {
        ClassUnit classUnit = ollirResult.getOllirClass();
        classUnit.buildCFGs();

        for (Method method : classUnit.getMethods()) {
            List<Instruction> instrs = method.getInstructions();
            if (instrs.isEmpty()) continue;
            optimizeRegistersForMethod(method, instrs);
        }
    }

    private void optimizeRegistersForMethod(Method method, List<Instruction> instrs) {
        Map<Instruction, Set<String>> defSets = new HashMap<>();
        Map<Instruction, Set<String>> useSets = new HashMap<>();
        Map<String, Type> varTypes = new HashMap<>();
        computeDefUse(method, defSets, useSets, varTypes);

        Map<Instruction, Set<String>> inSets = new HashMap<>();
        Map<Instruction, Set<String>> outSets = new HashMap<>();
        performLiveness(instrs, defSets, useSets, inSets, outSets);

        Map<String, Set<String>> interference = buildInterference(defSets, outSets, method, varTypes);

        Map<String, Integer> coloring = colorGraph(interference, method);

        updateVarTable(method, coloring, varTypes);

        printRegisters(method, coloring);
    }

    private void computeDefUse(Method method, Map<Instruction, Set<String>> defSets, Map<Instruction, Set<String>> useSets, Map<String, Type> varTypes) {
        for (Map.Entry<String, Descriptor> entry : method.getVarTable().entrySet()) {
            varTypes.put(entry.getKey(), entry.getValue().getVarType());
        }

        for (Instruction instr : method.getInstructions()) {
            Set<String> defs = new HashSet<>();
            Set<String> uses = new HashSet<>();

            if (instr instanceof AssignInstruction) {
                AssignInstruction ai = (AssignInstruction) instr;
                Element dest = ai.getDest();
                if (dest instanceof Operand) {
                    Operand od = (Operand) dest;
                    defs.add(od.getName());
                    varTypes.putIfAbsent(od.getName(), od.getType());
                }
                if (ai.getRhs() != null) {
                    extractUsesFromInstruction(ai.getRhs(), uses, varTypes);
                }
            } else {
                extractUsesFromInstruction(instr, uses, varTypes);
            }

            defSets.put(instr, defs);
            useSets.put(instr, uses);
        }
    }

    private void extractUsesFromInstruction(Instruction instr, Set<String> uses, Map<String, Type> varTypes) {
        if (instr instanceof SingleOpInstruction) {
            recordUse(((SingleOpInstruction) instr).getSingleOperand(), uses, varTypes);
        } else if (instr instanceof BinaryOpInstruction) {
            BinaryOpInstruction b = (BinaryOpInstruction) instr;
            recordUse(b.getLeftOperand(), uses, varTypes);
            recordUse(b.getRightOperand(), uses, varTypes);
        } else if (instr instanceof UnaryOpInstruction) {
            recordUse(((UnaryOpInstruction) instr).getOperand(), uses, varTypes);
        } else if (instr instanceof CallInstruction) {
            CallInstruction c = (CallInstruction) instr;
            if (c.getCaller() != null) recordUse(c.getCaller(), uses, varTypes);
            for (Element arg : c.getArguments()) recordUse(arg, uses, varTypes);
        } else if (instr instanceof ReturnInstruction) {
            ((ReturnInstruction) instr).getOperand().ifPresent(op -> recordUse(op, uses, varTypes));
        } else if (instr instanceof CondBranchInstruction) {
            for (Element e : ((CondBranchInstruction) instr).getOperands())
                recordUse(e, uses, varTypes);
        } else if (instr instanceof PutFieldInstruction) {
            List<Element> ops = ((PutFieldInstruction) instr).getOperands();
            if (!ops.isEmpty()) recordUse(ops.get(0), uses, varTypes);
            if (ops.size() > 2) recordUse(ops.get(2), uses, varTypes);
        } else if (instr instanceof GetFieldInstruction) {
            List<Element> ops = ((GetFieldInstruction) instr).getOperands();
            if (!ops.isEmpty()) recordUse(ops.get(0), uses, varTypes);
        }
    }

    private void recordUse(Element e, Set<String> uses, Map<String, Type> varTypes) {
        if (e instanceof Operand) {
            Operand o = (Operand) e;
            uses.add(o.getName());
            varTypes.putIfAbsent(o.getName(), o.getType());
        } else if (e instanceof ArrayOperand) {
            ArrayOperand a = (ArrayOperand) e;
            uses.add(a.getName());
            varTypes.putIfAbsent(a.getName(), a.getType());
            for (Element idx : a.getIndexOperands()) recordUse(idx, uses, varTypes);
        }
    }

    private void performLiveness(List<Instruction> instrs, Map<Instruction, Set<String>> defSets, Map<Instruction, Set<String>> useSets, Map<Instruction, Set<String>> inSets, Map<Instruction, Set<String>> outSets) {
        for (Instruction instr : instrs) {
            inSets.put(instr, new HashSet<>());
            outSets.put(instr, new HashSet<>());
        }
        boolean changed;
        do {
            changed = false;
            for (int i = instrs.size() - 1; i >= 0; i--) {
                Instruction instr = instrs.get(i);
                Set<String> newOut = new HashSet<>();
                for (Node succ : instr.getSuccessors()) {
                    if (succ instanceof Instruction)
                        newOut.addAll(inSets.get((Instruction) succ));
                }
                Set<String> newIn = new HashSet<>(useSets.get(instr));
                Set<String> temp = new HashSet<>(newOut);
                temp.removeAll(defSets.get(instr));
                newIn.addAll(temp);
                if (!newOut.equals(outSets.get(instr)) || !newIn.equals(inSets.get(instr))) {
                    outSets.put(instr, newOut);
                    inSets.put(instr, newIn);
                    changed = true;
                }
            }
        } while (changed);
    }

    private Map<String, Set<String>> buildInterference(Map<Instruction, Set<String>> defSets, Map<Instruction, Set<String>> outSets, Method method, Map<String, Type> varTypes) {

        Set<String> allVars = new HashSet<>();
        for (Instruction instr : defSets.keySet()) {
            allVars.addAll(defSets.get(instr));
            allVars.addAll(outSets.get(instr));
        }

        Map<String, Set<String>> graph = new HashMap<>();
        for (String v : allVars) {
            graph.putIfAbsent(v, new HashSet<>());
        }

        for (Instruction instr : defSets.keySet()) {
            Set<String> live = new HashSet<>(outSets.get(instr));
            live.addAll(defSets.get(instr));
            for (String v1 : live) {
                for (String v2 : live) {
                    if (!v1.equals(v2)) {
                        graph.get(v1).add(v2);
                        graph.get(v2).add(v1);
                    }
                }
            }
        }

        return graph;
    }

    private Map<String, Integer> colorGraph(Map<String, Set<String>> graph, Method method) {
        Map<String, Integer> assign = new HashMap<>();

        // pin `this` and parameters
        for (var entry : method.getVarTable().entrySet()) {
            String varName = entry.getKey();
            Descriptor descriptor = entry.getValue();
            if (varName.equals("this") || descriptor.getScope() == VarScope.PARAMETER) {
                assign.put(varName, descriptor.getVirtualReg());
            }
        }
        int pinnedCount = assign.size();

        // build worklist
        Map<String, Set<String>> work = new HashMap<>();
        for (var entry : graph.entrySet()) {
            if (!assign.containsKey(entry.getKey())) {
                work.put(entry.getKey(), new HashSet<>(entry.getValue()));
            }
        }

        // simplify
        Deque<String> stack = new ArrayDeque<>();
        while (!work.isEmpty()) {
            String pick = null;
            for (var entry : work.entrySet()) {
                if (entry.getValue().size() < maxRegisters) {
                    pick = entry.getKey();
                    break;
                }
            }
            if (pick == null) pick = work.keySet().iterator().next();
            stack.push(pick);
            for (String nbr : work.get(pick)) {
                work.get(nbr).remove(pick);
            }
            work.remove(pick);
        }

        // assign colors with offset
        while (!stack.isEmpty()) {
            String var = stack.pop();
            Set<Integer> used = new HashSet<>();
            for (String nbr : graph.getOrDefault(var, Set.of())) {
                if (assign.containsKey(nbr)) used.add(assign.get(nbr));
            }
            int color = pinnedCount;
            while (used.contains(color)) {
                color++;
                if (color >= pinnedCount + maxRegisters) break;
            }
            assign.put(var, color);
        }

        return assign;
    }

    private void updateVarTable(Method method, Map<String, Integer> coloring, Map<String, Type> varTypes) {
        Map<String, Descriptor> vt = method.getVarTable();
        for (var ent : coloring.entrySet()) {
            String name = ent.getKey(); int reg = ent.getValue();
            if (vt.containsKey(name)) {
                vt.get(name).setVirtualReg(reg);
            } else {
                Descriptor d = new Descriptor(VarScope.LOCAL, reg, varTypes.get(name));
                vt.put(name, d);
            }
        }
    }

    private void printRegisters(Method method, Map<String, Integer> coloring) {
        int max = coloring.values().stream().max(Integer::compareTo).orElse(-1) + 1;
        System.out.println("Register allocation for method `" + method.getMethodName() + "`: " + max + " registers are needed");
        coloring.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(e -> System.out.printf("Variable %s assigned to register #%d%n", e.getKey(), e.getValue()));
    }
}
