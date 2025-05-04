package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2025.ast.Kind;

import java.util.Collections;

public class ConstantFolding extends PreorderJmmVisitor<Void, Void> {

    private boolean hasModified = false;
    @Override
    protected void buildVisitor() {
        addVisit(Kind.BINARY_EXPR, this::visitBinaryExpr);
        setDefaultVisit(this::defaultVisit);
    }



    private Void visitBinaryExpr(JmmNode expr, Void unused){

        JmmNode left = expr.getChild(0);
        JmmNode right = expr.getChild(1);
        var operator = expr.get("op");

        if(Kind.INTEGER_LITERAL.check(left) && Kind.INTEGER_LITERAL.check(right)){
            if(operator.equals("<")){
                boolean value=Boolean.parseBoolean(left.get("value")) && Boolean.parseBoolean(right.get("value"));

                JmmNode newChild = new JmmNodeImpl(Collections.singletonList("IntegerLiteral"));
                newChild.putObject("value",String.valueOf(value));

                this.setHasModified(true);

                //expr.replace(newChild);
                JmmNode parent = expr.getParent();
                int childIndex = expr.getIndexOfSelf();
                parent.removeChild(childIndex);
                parent.add(newChild,childIndex);

                return null;
            }
            int value=0;
            switch (operator){
                case "+" -> value = Integer.parseInt(left.get("value")) + Integer.parseInt(right.get("value"));
                case "-" -> value = Integer.parseInt(left.get("value")) - Integer.parseInt(right.get("value"));
                case "*" -> value = Integer.parseInt(left.get("value")) * Integer.parseInt(right.get("value"));
                case "/" -> value = Integer.parseInt(left.get("value")) / Integer.parseInt(right.get("value"));
            }

            JmmNode newChild = new JmmNodeImpl(Collections.singletonList("IntegerLiteral"));
            newChild.putObject("value",String.valueOf(value));

            this.setHasModified(true);

            //expr.replace(newChild);
            JmmNode parent = expr.getParent();
            int childIndex = expr.getIndexOfSelf();
            parent.removeChild(childIndex);
            parent.add(newChild,childIndex);

            return null;

        }

        if(Kind.BOOLEAN_LITERAL.check(left) && Kind.BOOLEAN_LITERAL.check(right)){
            boolean value=Boolean.parseBoolean(left.get("value")) && Boolean.parseBoolean(right.get("value"));

            JmmNode newChild = new JmmNodeImpl(Collections.singletonList("BooleanLiteral"));
            newChild.putObject("value",String.valueOf(value));

            this.setHasModified(true);

            //expr.replace(newChild);
            JmmNode parent = expr.getParent();
            int childIndex = expr.getIndexOfSelf();
            parent.removeChild(childIndex);
            parent.add(newChild,childIndex);

            return null;

        }





        return null;
    }

    public boolean hasModified(){
        return this.hasModified;
    }

    public void setHasModified(boolean mod){
        this.hasModified = mod;
    }

    private Void defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return null;
    }
}
