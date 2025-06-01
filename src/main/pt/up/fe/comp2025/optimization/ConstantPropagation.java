package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2025.ast.Kind;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConstantPropagation extends PreorderJmmVisitor<Void, Void> {

    private boolean hasModified = false;

    private Map<String,JmmNode> constantVars = new HashMap<>();
    private Map<String,Boolean> partOfWhileCond = new HashMap<>();
    @Override
    protected void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDcl);
        addVisit(Kind.VAR_REF_EXPR, this::visitVarRef);
        addVisit(Kind.ASSIGN_STMT, this::visitAssignStmt);
        addVisit(Kind.WHILE_STMT, this::visitWhileStmt);
        setDefaultVisit(this::defaultVisit);
    }

    private Void visitMethodDcl(JmmNode method, Void unused){
        constantVars.clear();
        partOfWhileCond.clear();
        return null;
    }

    private Void visitWhileStmt(JmmNode node, Void unused){
        JmmNode cond = node.getChild(0);
        JmmNode content = node.getChild(1);

        List<JmmNode> varInCond = cond.getChildren(Kind.VAR_REF_EXPR);
        List<JmmNode> assigmentInWhile = content.getChildren(Kind.ASSIGN_STMT);

        for(JmmNode child : varInCond){
            partOfWhileCond.put(child.get("name"),false);
        }

        for(JmmNode child : assigmentInWhile){
            // name of the variable being assigned
            String varName = child.getChild(0).get("name");
            if(partOfWhileCond.containsKey(varName)){
                partOfWhileCond.put(varName,true);

            }
        }
        return null;
    }

    private Void visitVarRef(JmmNode varRef, Void unused){
        JmmNode parent = varRef.getParent();

        if(Kind.ASSIGN_STMT.check(parent) && parent.getChild(0) == varRef){
            // variable is being assigned
            return null;
        }

        String varName = varRef.get("name");

        if(partOfWhileCond.containsKey(varName) && partOfWhileCond.get(varName)){
            // is part of a while condition
            return null;
        }

       if (constantVars.containsKey(varName)) {

           JmmNode newChild = constantVars.get(varName);
           int childIndex = varRef.getIndexOfSelf();
           parent.removeChild(childIndex);
           parent.add(newChild,childIndex);

            this.hasModified = true;
       }




        return null;
    }

    private Void visitAssignStmt(JmmNode node, Void unused){
        String varName = node.getChild(0).get("name");

        JmmNode value = node.getChild(1);

        if(Kind.INTEGER_LITERAL.check(value) || Kind.BOOLEAN_LITERAL.check(value)){
            constantVars.put(varName,value);

        } else if (constantVars.containsKey(varName)) {
            constantVars.remove(varName);
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
