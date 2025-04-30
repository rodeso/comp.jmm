package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.collections.AccumulatorMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

public class OptUtils {

    private final AccumulatorMap<String> temporaries;
    private final AccumulatorMap<String> ifBranches;
    private final AccumulatorMap<String> whileBranches;
    private final TypeUtils types; // Mantido caso seja útil para outras funções

    public OptUtils(TypeUtils types) {
        this.types = types;
        this.temporaries = new AccumulatorMap<>();
        this.ifBranches = new AccumulatorMap<>();
        this.whileBranches = new AccumulatorMap<>();
    }

    public String nextTemp() {
        return nextTemp("tmp");
    }

    public String nextTemp(String prefix) {
        var nextTempNum = temporaries.add(prefix) - 1;
        return prefix + nextTempNum;
    }

    public String nextIfBranch() {
        return nextIfBranch("if"); // Alterado prefixo para clareza
    }

    public String nextIfBranch(String prefix) {
        var nextBranchNum = ifBranches.add(prefix) - 1;
        return prefix + nextBranchNum;
    }

    public String nextWhileBranch() {
        return nextWhileBranch("while");
    }

    public String nextWhileBranch(String prefix) {
        // Usar acumulador diferente para while para evitar colisões com if
        var nextBranchNum = whileBranches.add(prefix) - 1;
        return prefix + nextBranchNum;
    }

    public String toOllirType(JmmNode typeNode) {
        if (Kind.TYPE.check(typeNode) || Kind.BASE_TYPE.check(typeNode)) {
            // Usa TypeUtils para converter o nó AST para um objeto Type primeiro
            return toOllirType(TypeUtils.convertType(typeNode));
        } else {
            System.err.println("Warning: toOllirType called on non-type node: " + typeNode.getKind());
            return ".UNKNOWN";
        }
    }

    public String toOllirType(Type type) {
        if (type == null) {
            System.err.println("Warning: toOllirType called with null Type.");
            return ".UNKNOWN";
        }

        String ollirBaseType = switch (type.getName()) {
            case "int" -> "i32";
            case "boolean" -> "bool";
            case "void" -> "V";
            case "String" -> "String";
            default -> type.getName();
        };

        if (type.isArray()) {
            return ".array." + ollirBaseType; // Anexa .array se for array
        } else {
            return "." + ollirBaseType; // Adiciona . ao tipo base
        }
    }
}
