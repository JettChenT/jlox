package jettchen.tools;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

public class GenerateAst {
    public static void main(String[] args) throws IOException{
        if(args.length!=1){
            System.err.println("Usage: generate_ast <output directory>");
            System.exit(64);
        }
        String outputDir = args[0];
        defineAst(outputDir, "Expr", Arrays.asList(
                "Assign : Token name, Expr value",
                "Binary : Expr left, Token operator, Expr right",
                "Call : Expr callee, Token paren, List<Expr> arguments",
                "Get : Expr object, Token name",
                "Grouping : Expr expression",
                "Literal : Object value",
                "Logical : Expr left, Token operator, Expr right",
                "Set : Expr object, Token name, Expr value",
                "Super : Token keyword, Token method",
                "This : Token keyword",
                "Unary : Token operator, Expr right",
                "Variable : Token name",
                "Comma : Expr left, Expr right",
                "Ternary : Expr condition, Expr then, Expr otherwise"
        ));
        defineAst(outputDir, "Stmt", Arrays.asList(
                "Block : List<Stmt> statements",
                "Class : Token name, Expr.Variable superclass, List<Stmt.Function> methods",
                "Expression : Expr expression",
                "Function : Token name, List<Token> params," +
                        " List<Stmt> body",
                "If: Expr condition, Stmt thenBranch, Stmt elseBranch",
                "Print : Expr expression",
                "Return : Token keyword, Expr value",
                "Var : Token name, Expr initializer",
                "While : Expr condition, Stmt body"
        ));
    }

    private static void defineAst(String outputDir, String baseName, List<String> types)
            throws IOException{
        String path = outputDir+"/"+baseName+".java";
        PrintWriter printer = new PrintWriter(path, "UTF-8");
        printer.println("package jettchen.lox;");
        printer.println();
        printer.println("import java.util.List;");
        printer.println();
        printer.println("abstract class "+baseName+" {");

        defineVisitor(printer, baseName, types);
        for(String def:types){
            String className  = def.split(":")[0].trim();
            String fields = def.split(":")[1].trim();
            defineType(printer, baseName, className, fields);
        }
        printer.println();
        printer.println(" abstract <R> R accept(Visitor<R> visitor);");
        printer.println("}");
        printer.close();
    }

    private static void defineType(PrintWriter printer, String baseName, String className, String fieldList) {
        printer.println(" static class "+className+" extends "+baseName+" {");
        printer.println(" "+className+"("+fieldList+"){ ");;
        String[] fields = fieldList.split(", ");
        for(String field:fields){
            String varname = field.split(" ")[1];
            printer.println(String.format("this.%s = %s;", varname,varname));
        }
        printer.println("}");
        // Visitor pattern
        printer.println();
        printer.println(" @Override");
        printer.println(" <R> R accept(Visitor<R> visitor) {");
        printer.println(" return visitor.visit"+className+baseName+"(this);");
        printer.println("} ");
        for(String field:fields){
            printer.println(" final "+field+";");
        }

        printer.println(" }");
    }

    public static void defineVisitor(PrintWriter printer, String baseName, List<String> types){
        printer.println(" interface Visitor<R> {");
        for (String type:types){
            String typeName = type.split(":")[0].trim();
            printer.println(" R visit"+typeName+baseName+"("+
                    typeName+" "+baseName.toLowerCase()+");");
        }
        printer.println(" }");
    }
}
