package plc.project;

import java.io.PrintWriter;
import java.util.List;

public class Generator implements Ast.Visitor<Void> {
    private final PrintWriter writer;
    private int indent = 0;

    public Generator(PrintWriter writer) {
        this.writer = writer;
    }

    public String generate(Ast.Source source) {
        visit(source);
        writer.flush();
        return writer.toString();
    }

    private void newline(int indent) {
        print(System.lineSeparator());
        for (int i = 0; i < indent; i++) {
            print("    ");
        }
    }

    private void print(String text) {
        writer.append(text);
    }

    @Override
    public Void visit(Ast.Source ast) {
        print("public class Main {" + System.lineSeparator());
        newline(indent);

        print("    public static void main(String[] args) {" + System.lineSeparator());
        print("        System.exit(new Main().main());");
        newline(--indent);
        print("    }" + System.lineSeparator());
        newline(indent);

        for (Ast.Field field : ast.getFields()) {
            visit(field);
        }

        for (Ast.Method method : ast.getMethods()) {
            visit(method);
        }

        newline(--indent);
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Field ast) {
        print(ast.getTypeName() + " " + ast.getName());
        if (ast.getValue().isPresent()) {
            print(" = ");
            visit(ast.getValue().get());
        }
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Method ast) {
        String returnType = ast.getReturnTypeName().orElse("Any");
        if (returnType.equals("Integer")) {
            returnType = "int";
        }

        print("    " + returnType + " " + ast.getName() + "(");
        List<String> parameters = ast.getParameters();
        List<String> parameterTypeNames = ast.getParameterTypeNames();
        for (int i = 0; i < parameters.size(); i++) {
            print(parameterTypeNames.get(i) + " " + parameters.get(i));
            if (i < parameters.size() - 1) {
                print(", ");
            }
        }
        print(") {" + System.lineSeparator());

        if (ast.getStatements().isEmpty()) {
            print("    }");
        } else {
            for (Ast.Stmt stmt : ast.getStatements()) {
                print("        ");
                visit(stmt);
                print(System.lineSeparator());
            }
            print("    }");
            print(System.lineSeparator());
        }

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Expression ast) {
        visit(ast.getExpression());
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Declaration ast) {
        String type;

        if (ast.getTypeName().isPresent()) {
            switch (ast.getTypeName().get()) {
                case "Decimal":
                    type = "double";
                    break;
                case "Integer":
                    type = "int";
                    break;
                case "String":
                    type = "String";
                    break;
                case "Boolean":
                    type = "boolean";
                    break;
                default:
                    type = "Object";
                    break;
            }
        } else if (ast.getVariable() != null) {
            switch (ast.getVariable().getType().getName()) {
                case "Decimal":
                    type = "double";
                    break;
                case "Integer":
                    type = "int";
                    break;
                case "String":
                    type = "String";
                    break;
                case "Boolean":
                    type = "boolean";
                    break;
                default:
                    type = "Object";
                    break;
            }
        } else {
            type = "Object";
        }

        print(type + " " + ast.getName());
        if (ast.getValue().isPresent()) {
            print(" = ");
            visit(ast.getValue().get());
        }
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Assignment ast) {
        visit(ast.getReceiver());
        print(" = ");
        visit(ast.getValue());
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.If ast) {
        print("if (");
        visit(ast.getCondition());
        print(") {");
        newline(++indent);
        for (Ast.Stmt stmt : ast.getThenStatements()) {
            visit(stmt);
            if (ast.getThenStatements().indexOf(stmt) < ast.getThenStatements().size() - 1) {
                newline(indent);
            }
        }
        newline(--indent);
        print("}");

        if (!ast.getElseStatements().isEmpty()) {
            print(" else {");
            newline(++indent);
            for (Ast.Stmt stmt : ast.getElseStatements()) {
                visit(stmt);
                if (ast.getElseStatements().indexOf(stmt) < ast.getElseStatements().size() - 1) {
                    newline(indent);
                }
            }
            newline(--indent);
            print("}");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.For ast) {
        print("for (int " + ast.getName() + " : ");
        visit(ast.getValue());
        print(") {");
        newline(++indent);
        for (Ast.Stmt stmt : ast.getStatements()) {
            visit(stmt);
            newline(indent);
        }
        newline(--indent);
        print("    }");
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.While ast) {
        print("while (");
        visit(ast.getCondition());
        print(") {");
        if (ast.getStatements().isEmpty()) {
            print(" }");
        } else {
            newline(++indent);
            for (Ast.Stmt stmt : ast.getStatements()) {
                visit(stmt);
                newline(indent);
            }
            newline(--indent);
            print("    }");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Return ast) {
        print("return ");
        visit(ast.getValue());
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Literal ast) {
        if (ast.getLiteral() instanceof String) {
            print("\"" + ast.getLiteral() + "\"");
        } else {
            print(ast.getLiteral().toString());
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Group ast) {
        print("(");
        visit(ast.getExpression());
        print(")");
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Binary ast) {
        visit(ast.getLeft());
        String operator = ast.getOperator();
        if (operator.equals("AND")) {
            operator = "&&";
        }
        print(" " + operator + " ");
        visit(ast.getRight());
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Access ast) {
        if (ast.getReceiver().isPresent()) {
            visit(ast.getReceiver().get());
            print(".");
        }
        print(ast.getName());
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Function ast) {
        if (ast.getName().equals("print")) {
            print("System.out.println(");
            visit(ast.getArguments().get(0));
            print(")");
        } else {
            if (ast.getReceiver().isPresent()) {
                visit(ast.getReceiver().get());
                print(".");
            }
            if (ast.getName().equals("slice")) {
                print("substring");
            } else {
                print(ast.getName());
            }
            print("(");
            List<Ast.Expr> arguments = ast.getArguments();
            for (int i = 0; i < arguments.size(); i++) {
                visit(arguments.get(i));
                if (i < arguments.size() - 1) {
                    print(", ");
                }
            }
            print(")");
        }
        return null;
    }
}
