package pt.up.fe.comp2025.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static pt.up.fe.comp2025.ast.Kind.*;

public class JmmSymbolTableBuilder {

    // In case we want to already check for some semantic errors during symbol table building.
    private List<Report> reports;

    public List<Report> getReports() {
        return reports;
    }

    private static Report newError(JmmNode node, String message) {
        return Report.newError(
                Stage.SEMANTIC,
                node.getLine(),
                node.getColumn(),
                message,
                null);
    }

    public JmmSymbolTable build(JmmNode root) {
        reports = new ArrayList<>();

        // Extract imports
        List<String> imports = extractImports(root);

        // Find class declaration
        JmmNode classDecl = null;
        for (JmmNode child : root.getChildren()) {
            if (Kind.CLASS_DECL.check(child)) {
                classDecl = child;
                break;
            }
        }

        SpecsCheck.checkArgument(classDecl != null, () -> "Expected a class declaration");
        String className = classDecl.get("name");

        // Extract fields
        List<Symbol> fields = extractFields(classDecl);

        // Build methods and related data
        List<String> methods = buildMethods(classDecl);
        Map<String, Type> returnTypes = buildReturnTypes(classDecl);
        Map<String, List<Symbol>> params = buildParams(classDecl);
        Map<String, List<Symbol>> locals = buildLocals(classDecl);

        // Handle super class
        String superClass = "";
        if (classDecl.getObject("isSub", Boolean.class)) {
            superClass = classDecl.get("parent");
        }

        return new JmmSymbolTable(className, imports, fields, methods, returnTypes,
                params, locals, superClass);
    }

    private List<String> extractImports(JmmNode root) {
        List<String> imports = new ArrayList<>();

        for (JmmNode child : root.getChildren()) {
            if (Kind.IMPORT_DECL.check(child)) {
                StringBuilder importPath = new StringBuilder();

                // Get path attributes - using getAttribute instead of getListObject
                if (child.getAttributes().contains("path")) {
                    // The path might be stored differently in your AST
                    // This is a guess based on common patterns
                    String pathStr = child.get("path");
                    String[] pathParts = pathStr.split("\\.");

                    for (String part : pathParts) {
                        importPath.append(part).append(".");
                    }
                }

                // Add the final name
                importPath.append(child.get("name"));
                imports.add(importPath.toString());
            }
        }

        return imports;
    }

    private List<Symbol> extractFields(JmmNode classDecl) {
        List<Symbol> fields = new ArrayList<>();

        for (JmmNode child : classDecl.getChildren()) {
            if (Kind.VAR_DECL.check(child)) {
                // Check if this var decl is directly under class (not inside a method)
                JmmNode parent = child.getParent();
                if (Kind.CLASS_DECL.check(parent)) {
                    Type type = processType(child.getChildren().get(0)); // First child should be type
                    String name = child.get("name");
                    fields.add(new Symbol(type, name));
                }
            }
        }

        return fields;
    }

    private Type processType(JmmNode typeNode) {
        // Check if the typeNode is a valid TYPE node
        if (!Kind.BASE_TYPE.check(typeNode) && !Kind.TYPE.check(typeNode)) {
            reports.add(newError(typeNode, "Expected a TYPE node, but found " + typeNode.getKind()));
            return TypeUtils.newIntType(); // Default to int as fallback
        }
        boolean isArray=false;
        if(Kind.BASE_TYPE.check(typeNode)){
            // Safely retrieve the "name" attribute
            String typeName = typeNode.getOptional("name").orElse(null);
            if (typeName == null || typeName.isEmpty()) {
                reports.add(newError(typeNode, "TYPE node is missing 'name' attribute."));
                return TypeUtils.newIntType(); // Default to int type if missing
            }

            return new Type(typeName, false);
        } else if (Kind.TYPE.check(typeNode)) {
            String typeName = typeNode.getChildren().get(0).getOptional("name").orElse(null);
            if (typeName == null || typeName.isEmpty()) {
                reports.add(newError(typeNode, "TYPE node is missing 'name' attribute."));
                return TypeUtils.newIntType(); // Default to int type if missing
            }
            String op1 = typeNode.getOptional("op1").orElse("");
            String op2 = typeNode.getOptional("op2").orElse("");
            if(op1.equals("[") && op2.equals("]")){
                isArray=true;
            }
            return new Type(typeName, isArray);
        }




        return TypeUtils.newIntType();
    }

    private List<String> buildMethods(JmmNode classDecl) {
        List<String> methods = new ArrayList<>();

        for (JmmNode child : classDecl.getChildren()) {
            if (Kind.METHOD_DECL.check(child)) {
                if (child.hasAttribute("name")) {
                    methods.add(child.get("name"));
                } else {
                    // Report or log an error if a METHOD_DECL does not have a "name"
                    reports.add(newError(child, "METHOD_DECL node missing 'name' attribute."));
                }
            }
        }

        return methods;
    }

    private Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        Map<String, Type> map = new HashMap<>();

        for (JmmNode method : classDecl.getChildren()) {
            if (Kind.METHOD_DECL.check(method)) {
                // Check if the method has a name attribute
                if (!method.hasAttribute("name")) {
                    reports.add(newError(method, "METHOD_DECL node missing 'name' attribute."));
                    continue; // Skip this method
                }

                String name = method.get("name");

                // Find the return type node
                Type returnType = TypeUtils.newIntType(); // Default

                for (JmmNode child : method.getChildren()) {
                    if (Kind.TYPE.check(child)) {
                        // This might be the return type
                        returnType = processType(child);
                        break;
                    }
                }

                map.put(name, returnType);
            }
        }

        return map;
    }

    private Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        Map<String, List<Symbol>> map = new HashMap<>();

        for (JmmNode method : classDecl.getChildren()) {
            if (Kind.METHOD_DECL.check(method)) {
                // Check if the method has a name attribute
                if (!method.hasAttribute("name")) {
                    reports.add(newError(method, "METHOD_DECL node missing 'name' attribute."));
                    continue; // Skip this method
                }

                String name = method.get("name");
                List<Symbol> methodParams = new ArrayList<>();

                // Find parameter list
                for (JmmNode child : method.getChildren()) {
                    if (Kind.PARAM_LIST.check(child)) {
                        // Process each parameter
                        for (JmmNode param : child.getChildren()) {
                            if (Kind.PARAM.check(param)) {
                                // Find type and name
                                Type paramType = TypeUtils.newIntType(); // Default
                                for (JmmNode paramChild : param.getChildren()) {
                                    if (Kind.TYPE.check(paramChild)) {
                                        paramType = processType(paramChild);
                                        break;
                                    }
                                }
                                String paramName = param.get("name");
                                methodParams.add(new Symbol(paramType, paramName));
                            }
                        }
                    }
                }

                map.put(name, methodParams);
            }
        }

        return map;
    }

    private Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {
        Map<String, List<Symbol>> map = new HashMap<>();

        for (JmmNode method : classDecl.getChildren()) {
            if (Kind.METHOD_DECL.check(method)) {
                // Check if the method has a name attribute
                if (!method.hasAttribute("name")) {
                    reports.add(newError(method, "METHOD_DECL node missing 'name' attribute."));
                    continue; // Skip this method
                }

                String name = method.get("name");
                List<Symbol> methodLocals = new ArrayList<>();

                // Find all VAR_DECL within this method
                for (JmmNode child : method.getChildren()) {
                    if (Kind.VAR_DECL.check(child)) {
                        Type localType = TypeUtils.newIntType(); // Default

                        // Get the type from the first child
                        if (child.getNumChildren() > 0) {
                            JmmNode typeNode = child.getChildren().get(0);
                            if (Kind.TYPE.check(typeNode)) {
                                localType = processType(typeNode);
                            }
                        }

                        String localName = child.get("name");
                        methodLocals.add(new Symbol(localType, localName));
                    }
                }

                map.put(name, methodLocals);
            }
        }

        return map;
    }
}