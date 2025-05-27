package pt.up.fe.comp2025.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.type.*;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.HashMap;
import java.util.Map;

public class JasminUtils {

    private final OllirResult ollirResult;

    public JasminUtils(OllirResult ollirResult) {
        // Can be useful to have if you expand this class with more methods
        this.ollirResult = ollirResult;
    }


    public String getModifier(AccessModifier accessModifier) {
        return accessModifier != AccessModifier.DEFAULT ?
                accessModifier.name().toLowerCase() + " " :
                "";
    }

    public boolean isInImport(String name, String fullPath){

        String[] parsedPath = fullPath.split("\\.");
        return  name.equals(parsedPath[parsedPath.length-1]);
    }

    public String getJasminType(Type type){
        if( type instanceof BuiltinType){
            return getJasminBuiltInType((BuiltinType) type);
        } else if (type instanceof ArrayType) {
            return getJasminType(type) + getJasminType(((ArrayType) type).getElementType());
        } else if (type instanceof  ClassType) {
            return getJasminType(type);
        }

        return "";

    }
    public String getJasminType(ClassType type){
        return "";

    }

    private String getJasminBuiltInType(BuiltinType type){
        return switch (type.getKind()){
            case  INT32 -> "I";
            case BOOLEAN -> "Z";
            case STRING -> "Ljava/lang/String";
            case VOID -> "V";
        };

    }

    public String getJasminType(ArrayType type){
        return "[".repeat(type.getNumDimensions()) ;

    }


}
