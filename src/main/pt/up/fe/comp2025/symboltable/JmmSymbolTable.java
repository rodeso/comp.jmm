package pt.up.fe.comp2025.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.*;
import java.util.stream.Collectors;

public class JmmSymbolTable extends AJmmSymbolTable {

    private final String className;
    private final List<String> imports;
    private final List<Symbol> fields;
    private final List<String> methods;
    private final Map<String, Type> returnTypes;
    private final Map<String, List<Symbol>> params;
    private final Map<String, List<Symbol>> locals;
    private final String parent;


    public JmmSymbolTable(String className,
                          List<String> imports,
                          List<Symbol> fields,
                          List<String> methods,
                          Map<String, Type> returnTypes,
                          Map<String, List<Symbol>> params,
                          Map<String, List<Symbol>> locals, String parent) {

        this.className = className;
        this.imports = imports;
        this.fields = fields;
        this.methods = methods;
        this.returnTypes = returnTypes;
        this.params = params;
        this.locals = locals;
        this.parent = parent;
    }

    @Override
    public List<String> getImports() {
        return imports;
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public String getSuper() {
        return this.parent;
    }

    @Override
    public List<Symbol> getFields() {
        return fields;
    }


    @Override
    public List<String> getMethods() {
        return methods;
    }

    @Override
    public Type getReturnType(String methodSignature) {
        // TODO: Simple implementation that needs to be expanded
        //Type returnType = this.returnTypes.get(methodSignature);
        return this.returnTypes.get(methodSignature);
    }

    @Override
    public List<Symbol> getParameters(String methodSignature) {
        return params.get(methodSignature);
    }

    @Override
    public List<Symbol> getLocalVariables(String methodSignature) {
        return locals.get(methodSignature);
    }

    @Override
    public String toString() {
        return print();
    }


}
