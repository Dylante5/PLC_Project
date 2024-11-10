package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

public final class Analyzer implements Ast.Visitor<Void> {

    protected Scope scope;

    public Analyzer(Scope parent) {
        scope = new Scope(Objects.requireNonNull(parent));
        // Add the print function to the scope
        // Ensure the print function is available in the global scope
        scope.defineFunction("print", 1, args -> Environment.NIL);
    }

    @Override
    public Void visit(Ast.Source ast) {
        // TODO: Implement the main/0 function check and type validation.
        for (Ast.Field field : ast.getFields()) {
            visit(field);
        }
        for (Ast.Method method : ast.getMethods()) {
            visit(method);
        }
        // Check if main function exists and if it has the correct signature.
        if (!scope.lookupFunction("main", 0).getReturnType().equals(Environment.Type.INTEGER)) {
            throw new RuntimeException("Main function must have return type Integer and be defined in the source.");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Field ast) {
        // TODO: Implement field validation and add type to scope.
        Environment.Type type = Environment.getType(ast.getTypeName());
        if (ast.getValue().isPresent()) {
            visit(ast.getValue().get());
            requireAssignable(type, ast.getValue().get().getType());
        }
        Environment.Variable variable = scope.defineVariable(ast.getName(), ast.getName(), type, Environment.NIL);
        ast.setVariable(variable);
        return null;
    }

    @Override
    public Void visit(Ast.Method ast) {
        // TODO: Implement method definition and add to scope.
        List<Environment.Type> parameterTypes = ast.getParameterTypeNames().stream()
                .map(Environment::getType)
                .collect(java.util.stream.Collectors.toList());
        Environment.Type returnType = ast.getReturnTypeName().isPresent() ?
                Environment.getType(ast.getReturnTypeName().get()) : Environment.Type.NIL;
        Environment.Function function = scope.defineFunction(ast.getName(), ast.getName(), parameterTypes, returnType, args -> Environment.NIL);
        ast.setFunction(function);

        scope = new Scope(scope);
        // Add the print function to the new scope to ensure it is always available
        scope.defineFunction("print", 1, args -> Environment.NIL);
        for (int i = 0; i < ast.getParameters().size(); i++) {
            scope.defineVariable(ast.getParameters().get(i), ast.getParameters().get(i), parameterTypes.get(i), Environment.NIL);
        }
        for (Ast.Stmt stmt : ast.getStatements()) {
            visit(stmt);
        }
        scope = scope.getParent();
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Expression ast) {
        // TODO: Implement validation for expression statements.
        visit(ast.getExpression());
        if (!(ast.getExpression() instanceof Ast.Expr.Function)) {
            throw new RuntimeException("Expression statement must be a function call.");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Declaration ast) {
        // TODO: Implement variable declaration and type inference.
        Environment.Type type = ast.getTypeName().isPresent() ?
                Environment.getType(ast.getTypeName().get()) : null;
        if (ast.getValue().isPresent()) {
            visit(ast.getValue().get());
            if (type == null) {
                type = ast.getValue().get().getType();
            } else {
                requireAssignable(type, ast.getValue().get().getType());
            }
        }
        if (type == null) {
            throw new RuntimeException("Declaration must have a type or an initial value.");
        }
        Environment.Variable variable = scope.defineVariable(ast.getName(), ast.getName(), type, Environment.NIL);
        ast.setVariable(variable);
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Assignment ast) {
        // TODO: Implement assignment validation.
        visit(ast.getReceiver());
        if (!(ast.getReceiver() instanceof Ast.Expr.Access)) {
            throw new RuntimeException("Assignment receiver must be an access expression.");
        }
        visit(ast.getValue());
        requireAssignable(ast.getReceiver().getType(), ast.getValue().getType());
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.If ast) {
        // TODO: Implement if statement validation.
        visit(ast.getCondition());
        requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());
        if (ast.getThenStatements().isEmpty()) {
            throw new RuntimeException("If statement must have a non-empty then block.");
        }
        scope = new Scope(scope);
        for (Ast.Stmt stmt : ast.getThenStatements()) {
            visit(stmt);
        }
        scope = scope.getParent();
        if (!ast.getElseStatements().isEmpty()) {
            scope = new Scope(scope);
            for (Ast.Stmt stmt : ast.getElseStatements()) {
                visit(stmt);
            }
            scope = scope.getParent();
        }
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.For ast) {
        // TODO: Implement for statement validation.
        visit(ast.getValue());
        requireAssignable(Environment.Type.INTEGER_ITERABLE, ast.getValue().getType());
        if (ast.getStatements().isEmpty()) {
            throw new RuntimeException("For statement must have a non-empty body.");
        }
        scope = new Scope(scope);
        scope.defineVariable(ast.getName(), ast.getName(), Environment.Type.INTEGER, Environment.NIL);
        for (Ast.Stmt stmt : ast.getStatements()) {
            visit(stmt);
        }
        scope = scope.getParent();
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.While ast) {
        // TODO: Implement while statement validation.
        visit(ast.getCondition());
        requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());
        scope = new Scope(scope);
        for (Ast.Stmt stmt : ast.getStatements()) {
            visit(stmt);
        }
        scope = scope.getParent();
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Return ast) {
        // TODO: Implement return statement validation.
        visit(ast.getValue());
        // Fetch the current function's return type from the scope to properly validate return statement
        requireAssignable(scope.lookupVariable("__currentFunctionReturnType").getType(), ast.getValue().getType());
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Literal ast) {
        // TODO: Implement literal validation and set type.
        Object value = ast.getLiteral();
        if (value == null) {
            ast.setType(Environment.Type.NIL);
        } else if (value instanceof Boolean) {
            ast.setType(Environment.Type.BOOLEAN);
        } else if (value instanceof Character) {
            ast.setType(Environment.Type.CHARACTER);
        } else if (value instanceof String) {
            ast.setType(Environment.Type.STRING);
        } else if (value instanceof BigInteger) {
            BigInteger integer = (BigInteger) value;
            if (integer.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0 ||
                    integer.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
                throw new RuntimeException("Integer literal out of range.");
            }
            ast.setType(Environment.Type.INTEGER);
        } else if (value instanceof BigDecimal) {
            BigDecimal decimal = (BigDecimal) value;
            if (decimal.doubleValue() == Double.POSITIVE_INFINITY ||
                    decimal.doubleValue() == Double.NEGATIVE_INFINITY) {
                throw new RuntimeException("Decimal literal out of range.");
            }
            ast.setType(Environment.Type.DECIMAL);
        } else {
            throw new RuntimeException("Unknown literal type.");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Group ast) {
        // TODO: Implement group expression validation.
        visit(ast.getExpression());
        ast.setType(ast.getExpression().getType());
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Binary ast) {
        // TODO: Implement binary expression validation.
        visit(ast.getLeft());
        visit(ast.getRight());
        switch (ast.getOperator()) {
            case "AND":
            case "OR":
                requireAssignable(Environment.Type.BOOLEAN, ast.getLeft().getType());
                requireAssignable(Environment.Type.BOOLEAN, ast.getRight().getType());
                ast.setType(Environment.Type.BOOLEAN);
                break;
            case "<":
            case "<=":
            case ">":
            case ">=":
            case "==":
            case "!=":
                requireAssignable(ast.getLeft().getType(), ast.getRight().getType());
                if (!Environment.Type.COMPARABLE.equals(ast.getLeft().getType())) {
                    throw new RuntimeException("Operands must be comparable.");
                }
                ast.setType(Environment.Type.BOOLEAN);
                break;
            case "+":
                if (ast.getLeft().getType().equals(Environment.Type.STRING) ||
                        ast.getRight().getType().equals(Environment.Type.STRING)) {
                    ast.setType(Environment.Type.STRING);
                } else {
                    requireAssignable(ast.getLeft().getType(), ast.getRight().getType());
                    ast.setType(ast.getLeft().getType());
                }
                break;
            case "-":
            case "*":
            case "/":
                requireAssignable(ast.getLeft().getType(), ast.getRight().getType());
                ast.setType(ast.getLeft().getType());
                break;
            default:
                throw new RuntimeException("Unknown binary operator.");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Access ast) {
        // TODO: Implement access expression validation.
        if (ast.getReceiver().isPresent()) {
            visit(ast.getReceiver().get());
            Environment.Variable variable = ast.getReceiver().get().getType().getField(ast.getName());
            ast.setVariable(variable);
        } else {
            Environment.Variable variable = scope.lookupVariable(ast.getName());
            ast.setVariable(variable);
        }
        // Access expressions do not need to set their type directly; it is derived from the variable
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Function ast) {
        // TODO: Implement function expression validation.
        if (ast.getReceiver().isPresent()) {
            visit(ast.getReceiver().get());
            Environment.Function function = ast.getReceiver().get().getType().getMethod(ast.getName(), ast.getArguments().size());
            ast.setFunction(function);
        } else {
            Environment.Function function = scope.lookupFunction(ast.getName(), ast.getArguments().size());
            ast.setFunction(function);
        }
        for (int i = 0; i < ast.getArguments().size(); i++) {
            visit(ast.getArguments().get(i));
            requireAssignable(ast.getFunction().getParameterTypes().get(i), ast.getArguments().get(i).getType());
        }
        // Function expressions do not need to set their type directly; it is derived from the function's return type
        return null;
    }

    public static void requireAssignable(Environment.Type target, Environment.Type type) {
        // Updated type assignability check to account for subtypes
        if (!target.equals(Environment.Type.ANY) && !target.equals(type)) {
            if (target.equals(Environment.Type.COMPARABLE)) {
                if (!(type.equals(Environment.Type.INTEGER) ||
                        type.equals(Environment.Type.DECIMAL) ||
                        type.equals(Environment.Type.CHARACTER) ||
                        type.equals(Environment.Type.STRING))) {
                    throw new RuntimeException("Type " + type + " is not assignable to " + target);
                }
            } else {
                throw new RuntimeException("Type " + type + " is not assignable to " + target);
            }
        }
    }
}
