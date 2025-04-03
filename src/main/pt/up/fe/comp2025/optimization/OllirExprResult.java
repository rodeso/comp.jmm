package pt.up.fe.comp2025.optimization;

public class OllirExprResult {

    public static final OllirExprResult EMPTY = new OllirExprResult("", "");

    private final String computation;
    private final String ref;

    public OllirExprResult(String ref, String computation) {
        this.ref = ref;
        this.computation = computation;
    }

    public OllirExprResult(String ref) {
        this(ref, "");
    }

    public OllirExprResult(String ref, StringBuilder computation) {
        this(ref, computation.toString());
    }

    public String getComputation() {
        return computation;
    }

    public String getref() {
        return ref;
    }

    @Override
    public String toString() {
        return "OllirNodeResult{" +
                "computation='" + computation + '\'' +
                ", ref='" + ref + '\'' +
                '}';
    }
}
