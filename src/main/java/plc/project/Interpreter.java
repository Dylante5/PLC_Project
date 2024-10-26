package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


public class Interpreter implements Ast.Visitor<Environment.PlcObject> {

    private Scope scope = new Scope(null);

    public Interpreter(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", 1, args -> {
            System.out.println(args.get(0).getValue());
            return Environment.NIL;
        });
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Environment.PlcObject visit(Ast.Source ast) {
        for (Ast.Field field : ast.getFields()) {
            visit(field);
        }
        for (Ast.Method method : ast.getMethods()) {
            visit(method);
        }
        return scope.lookupFunction("main", 0).invoke(new ArrayList<>());
    }

    @Override
    public Environment.PlcObject visit(Ast.Field ast) {
        Environment.PlcObject value = ast.getValue().isPresent() ? visit(ast.getValue().get()) : Environment.NIL;
        scope.defineVariable(ast.getName(), value);
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Method ast) {
        scope.defineFunction(ast.getName(), ast.getParameters().size(), args -> {
            Scope functionScope = new Scope(scope);
            for (int i = 0; i < ast.getParameters().size(); i++) {
                functionScope.defineVariable(ast.getParameters().get(i), args.get(i));
            }
            try {
                for (Ast.Stmt stmt : ast.getStatements()) {
                    visit(stmt);
                }
            } catch (Return returnValue) {
                return returnValue.value;
            }
            return Environment.NIL;
        });
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Expression ast) {
        visit(ast.getExpression());
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Declaration ast) {
        Environment.PlcObject value = ast.getValue().isPresent() ? visit(ast.getValue().get()) : Environment.NIL;
        scope.defineVariable(ast.getName(), value);
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Assignment ast) {
        Ast.Expr.Access receiver = (Ast.Expr.Access) ast.getReceiver();
        Environment.PlcObject value = visit(ast.getValue());
        if (receiver.getReceiver().isPresent()) {
            Environment.PlcObject object = visit(receiver.getReceiver().get());
            requireType(Scope.class, object).defineVariable(receiver.getName(), value);
        } else {
            scope.defineVariable(receiver.getName(), value);
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.If ast) {
        Boolean condition = requireType(Boolean.class, visit(ast.getCondition()));
        if (condition) {
            Scope thenScope = new Scope(scope);
            for (Ast.Stmt stmt : ast.getThenStatements()) {
                visit(stmt);
            }
        } else {
            Scope elseScope = new Scope(scope);
            for (Ast.Stmt stmt : ast.getElseStatements()) {
                visit(stmt);
            }
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.For ast) {
        Iterable<Environment.PlcObject> iterable = requireType(Iterable.class, visit(ast.getValue()));
        Scope forScope = new Scope(scope);
        for (Environment.PlcObject element : iterable) {
            forScope.defineVariable(ast.getName(), element);
            for (Ast.Stmt stmt : ast.getStatements()) {
                visit(stmt);
            }
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.While ast) {
        while (requireType(Boolean.class, visit(ast.getCondition()))) {
            Scope whileScope = new Scope(scope);
            for (Ast.Stmt stmt : ast.getStatements()) {
                visit(stmt);
            }
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Return ast) {
        throw new Return(visit(ast.getValue()));
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Literal ast) {
        return Environment.create(ast.getLiteral());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Group ast) {
        return visit(ast.getExpression());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Binary ast) {
        Environment.PlcObject left = visit(ast.getLeft());
        Environment.PlcObject right = visit(ast.getRight());
        switch (ast.getOperator()) {
            case "AND":
                return Environment.create(requireType(Boolean.class, left) && requireType(Boolean.class, right));
            case "OR":
                return Environment.create(requireType(Boolean.class, left) || requireType(Boolean.class, right));
            case "<":
                return Environment.create(requireType(Comparable.class, left).compareTo(right.getValue()) < 0);
            case "<=":
                return Environment.create(requireType(Comparable.class, left).compareTo(right.getValue()) <= 0);
            case ">":
                return Environment.create(requireType(Comparable.class, left).compareTo(right.getValue()) > 0);
            case ">=":
                return Environment.create(requireType(Comparable.class, left).compareTo(right.getValue()) >= 0);
            case "==":
                return Environment.create(left.getValue().equals(right.getValue()));
            case "!=":
                return Environment.create(!left.getValue().equals(right.getValue()));
            case "+":
                if (left.getValue() instanceof String || right.getValue() instanceof String) {
                    return Environment.create(left.getValue().toString() + right.getValue().toString());
                } else if (left.getValue() instanceof BigInteger && right.getValue() instanceof BigInteger) {
                    return Environment.create(((BigInteger) left.getValue()).add((BigInteger) right.getValue()));
                } else if (left.getValue() instanceof BigDecimal && right.getValue() instanceof BigDecimal) {
                    return Environment.create(((BigDecimal) left.getValue()).add((BigDecimal) right.getValue()));
                } else {
                    throw new RuntimeException("Invalid operands for + operator");
                }
            case "-":
                if (left.getValue() instanceof BigInteger && right.getValue() instanceof BigInteger) {
                    return Environment.create(((BigInteger) left.getValue()).subtract((BigInteger) right.getValue()));
                } else if (left.getValue() instanceof BigDecimal && right.getValue() instanceof BigDecimal) {
                    return Environment.create(((BigDecimal) left.getValue()).subtract((BigDecimal) right.getValue()));
                } else {
                    throw new RuntimeException("Invalid operands for - operator");
                }
            case "*":
                if (left.getValue() instanceof BigInteger && right.getValue() instanceof BigInteger) {
                    return Environment.create(((BigInteger) left.getValue()).multiply((BigInteger) right.getValue()));
                } else if (left.getValue() instanceof BigDecimal && right.getValue() instanceof BigDecimal) {
                    return Environment.create(((BigDecimal) left.getValue()).multiply((BigDecimal) right.getValue()));
                } else {
                    throw new RuntimeException("Invalid operands for * operator");
                }
            case "/":
                if (left.getValue() instanceof BigInteger && right.getValue() instanceof BigInteger) {
                    if (right.getValue().equals(BigInteger.ZERO)) {
                        throw new RuntimeException("Division by zero");
                    }
                    return Environment.create(((BigInteger) left.getValue()).divide((BigInteger) right.getValue()));
                } else if (left.getValue() instanceof BigDecimal && right.getValue() instanceof BigDecimal) {
                    if (((BigDecimal) right.getValue()).compareTo(BigDecimal.ZERO) == 0) {
                        throw new RuntimeException("Division by zero");
                    }
                    return Environment.create(((BigDecimal) left.getValue()).divide((BigDecimal) right.getValue(), RoundingMode.HALF_EVEN));
                } else {
                    throw new RuntimeException("Invalid operands for / operator");
                }
            default:
                throw new RuntimeException("Unknown operator: " + ast.getOperator());
        }
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Access ast) {
        if (ast.getReceiver().isPresent()) {
            Environment.PlcObject receiver = visit(ast.getReceiver().get());
            return requireType(Scope.class, receiver).lookupVariable(ast.getName()).getValue();
        } else {
            return scope.lookupVariable(ast.getName()).getValue();
        }
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Function ast) {
        List<Environment.PlcObject> arguments = ast.getArguments().stream()
                .map(this::visit)
                .collect(Collectors.toList());
        if (ast.getReceiver().isPresent()) {
            Environment.PlcObject receiver = visit(ast.getReceiver().get());
            return requireType(Scope.class, receiver).lookupFunction(ast.getName(), arguments.size()).invoke(arguments);
        } else {
            return scope.lookupFunction(ast.getName(), arguments.size()).invoke(arguments);
        }
    }

    /**
     * Helper function to ensure an object is of the appropriate type.
     */
    private static <T> T requireType(Class<T> type, Environment.PlcObject object) {
        if (type.isInstance(object.getValue())) {
            return type.cast(object.getValue());
        } else {
            throw new RuntimeException("Expected type " + type.getName() + ", received " + object.getValue().getClass().getName() + ".");
        }
    }

    /**
     * Exception class for returning values.
     */
    private static class Return extends RuntimeException {

        private final Environment.PlcObject value;

        private Return(Environment.PlcObject value) {
            this.value = value;
        }

    }

}
