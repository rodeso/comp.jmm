package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import static pt.up.fe.comp2025.ast.Kind.TYPE;

import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.collections.AccumulatorMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

/**
 * Utility methods related to the optimization middle-end.
 */
public class OptUtils {


    private final AccumulatorMap<String> temporaries;

    private final AccumulatorMap<String> ifBranches;

    private final AccumulatorMap<String> whileBranches;

    private final TypeUtils types;

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

        // Subtract 1 because the base is 1
        var nextTempNum = temporaries.add(prefix) - 1;

        return prefix + nextTempNum;
    }

    public String nextIfBranch(){
        return nextIfBranch("then");
    }

    public String nextIfBranch(String prefix){
        var nextBranchNum = ifBranches.add(prefix) - 1;
        return prefix + nextBranchNum;
    }

    public String nextWhileBranch(){
        return nextWhileBranch("while");
    }

    public String nextWhileBranch(String prefix){
        var nextBranchNum = ifBranches.add(prefix) - 1;
        return prefix + nextBranchNum;
    }

    public String toOllirType(JmmNode typeNode) {



        TYPE.checkOrThrow(typeNode);

        return toOllirType(types.convertType(typeNode));
    }

    public String toOllirType(Type type) {
        return toOllirType(type.getName());
    }

    private String toOllirType(String typeName) {

        String type = "." + switch (typeName) {
            case "int" -> "i32";
            case "boolean" -> "bool";
            case "void" -> "V";
            case "String" -> "String";
            case "int[]" -> "array.i32";
            default -> typeName;
            //default -> throw new NotImplementedException(typeName);
        };

        return type;
    }


}
