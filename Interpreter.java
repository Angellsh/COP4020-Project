package plc.project;
import java.awt.*;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Stack;
public class Interpreter implements Ast.Visitor<Environment.PlcObject> {
    private Scope scope = new Scope(null);
    public Interpreter(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", 1, args -> {
            System.out.println(args.get(0).getValue());
            return Environment.NIL;
        });
        StringWriter writer = new StringWriter();
        scope.defineFunction("log", 1, args -> {
            writer.write(String.valueOf(args.get(0).getValue()));
            return args.get(0);
        });

    }
    public Scope getScope() {
        return scope;
    }
    @Override
    public Environment.PlcObject visit(Ast.Source ast) {

        for (int i =0; i< ast.getFields().size(); i++){
            visit(ast.getFields().get(i));
        }
        for (int i =0; i<ast.getMethods().size(); i++){
            visit(ast.getMethods().get(i));
        }
        try{
            return scope.lookupFunction("main", 0).invoke(new ArrayList<>());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    public Environment.PlcObject visit(Ast.Field ast) {
        if (ast.getValue().isPresent()) {
            scope.defineVariable(ast.getName(), ast.getConstant(),
                    visit(ast.getValue().get()));
        }
        else{
            scope.defineVariable(ast.getName(), ast.getConstant(),
                    Environment.NIL);
        }
        return Environment.NIL;
    }
    @Override
    public Environment.PlcObject visit(Ast.Method ast) {
        scope.defineFunction(ast.getName(), ast.getParameters().size(),
                args
                -> {
            Scope global = scope; //copying scope
                try {
                scope = new Scope(scope);

                global.defineVariable(ast.getName(), false, new
                       Environment.PlcObject(scope, ast.getName()));

                for (int i = 0; i < ast.getParameters().size(); i++)
                {
                    scope.defineVariable(ast.getParameters().get(i), false,
                            args.get(i));
                }
                for (int i = 0; i < ast.getStatements().size(); i++) {

                    visit(ast.getStatements().get(i));


                }

            } catch (Exception e) {
                    if ( e.getCause() instanceof Return ) { //temporary
                        return ((Return) e.getCause()).get();

                    }
                    if ( e instanceof Return ) {
                        return  ((Return) e).get();

                    }


            } finally {
                    scope = global;
            }
                    return Environment.NIL;
        });

        return Environment.NIL;
    }
    @Override
    public Environment.PlcObject visit(Ast.Statement.Expression ast) {
        visit(ast.getExpression());
        return Environment.NIL;
    }
    @Override
    public Environment.PlcObject visit(Ast.Statement.Declaration ast) {
        if (ast.getValue().isPresent()) {
            scope.defineVariable(ast.getName(), false,
                    visit(ast.getValue().get()));
        }
        else{
            scope.defineVariable(ast.getName(), false, Environment.NIL);
        }
        return Environment.NIL;
    }
    @Override
    public Environment.PlcObject visit(Ast.Statement.Assignment ast) {
        try {
            if (ast.getReceiver() instanceof Ast.Expression.Access) {
                Environment.PlcObject receiver = visit(ast.getReceiver());
                Environment.PlcObject value = visit(ast.getValue());
                Environment.Variable var = null;
                if (receiver.getValue().toString().contains(".")){
                    String scope_str = receiver.getValue().toString().split("\\.")[0];
                    String object = receiver.getValue().toString().split("\\.")[1];
                    var = scope.lookupVariable(scope_str).getValue().getField(object);
                }
                else {
                    var = scope.lookupVariable((String) ((Ast.Expression.Access) ast.getReceiver()).getName());
                }
                if(!var.getConstant())
                    var.setValue(value);
                else
                    throw new RuntimeException("Cannot modify the contents of a constant variable");

            }
            else{
                throw new RuntimeException("Not an instance of Ast access");
            }
            return Environment.NIL;
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    public Environment.PlcObject visit(Ast.Statement.If ast) {
        Scope prev = scope;
        try {
            Environment.PlcObject condition = visit(ast.getCondition());
            scope = new Scope(scope);
            if (requireType(Boolean.class, condition).equals(true)){
            //    for (int i =0; i<ast.getThenStatements().size();i++){
                //    visit(ast.getThenStatements().get(i));
              //  }
                ast.getThenStatements().forEach(this::visit);
            }
            else if (requireType(Boolean.class, condition).equals(false)){
                ast.getElseStatements().forEach(this::visit);
            }
            else
                throw new RuntimeException("Not a boolean type.");
        }
        catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
        finally {
            scope = prev;
        }
        return Environment.NIL;
    }
    @Override
    public Environment.PlcObject visit(Ast.Statement.For ast) {
// private final Statement initialization;
// private final Ast.Expression condition;
// private final Statement increment;
// private final List<Statement> statements;
//for int i = 5; i<10; i++
//execute
        if (ast.getInitialization() !=null)
            visit(ast.getInitialization());
        while(requireType(Boolean.class, visit(ast.getCondition())).equals(true)){
            ast.getStatements().forEach(this :: visit);
            if (ast.getIncrement()!=null)
                visit(ast.getIncrement());
        }
        return Environment.NIL;
    }
    @Override
    public Environment.PlcObject visit(Ast.Statement.While ast) {
        Scope parent = scope;
        try {
            Ast.Expression condition = ast.getCondition();
            Environment.PlcObject cond = visit(condition);

            while (requireType(Boolean.class, visit(condition)) ){
                scope = new Scope(scope);
                for (Ast.Expression.Statement statement : ast.getStatements()) {
                    visit(statement);
                }

            }
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
        finally{
            scope = parent;
        }
        return Environment.NIL;
    }
    @Override
    public Environment.PlcObject visit(Ast.Statement.Return ast) {


        Environment.PlcObject res = visit(ast.getValue());
        throw new Return(res);
    }
    @Override
    public Environment.PlcObject visit(Ast.Expression.Literal ast) {
        if (ast.getLiteral() ==null) {
            return Environment.NIL;
        }
        Environment.PlcObject obj = Environment.create( ast.getLiteral());
        return obj;
    }
    @Override
    public Environment.PlcObject visit(Ast.Expression.Group ast) {
        return visit(ast.getExpression());
    }
    @Override
    public Environment.PlcObject visit(Ast.Expression.Binary ast) {
        String operator = ast.getOperator();
        Object res;
        Environment.PlcObject left = visit(ast.getLeft());

        if (ast.getOperator().equals("OR")) {
            if (!(left.getValue() instanceof Boolean)) {
                throw new RuntimeException("Not a boolean type.");
            }
            if ((left.getValue().equals(true)))
                    res = true;
            else{
                Environment.PlcObject right = visit(ast.getRight());
                if (!(right.getValue() instanceof Boolean)) {
                    throw new RuntimeException("Not a boolean type.");
                }
                if (right.getValue().equals(true))
                    res = true;
                else
                    res = false;
            }
            return Environment.create(res);
        }
        //short circuit and
        if (ast.getOperator().equals("AND")) {
            if (!(left.getValue() instanceof Boolean)) {
                throw new RuntimeException("Not a boolean type.");
            }
            if ((left.getValue().equals(false)))
                res = false;

            else{
                Environment.PlcObject right = visit(ast.getRight());
                if (!(right.getValue() instanceof Boolean)) {
                    throw new RuntimeException("Not a boolean type.");
                }
                if (right.getValue().equals(true))
                    res = true;
                 else
                    res = false;
            }

                res = false;
            return Environment.create(res);
        }

        Environment.PlcObject right = visit(ast.getRight());
        switch (operator){
            case "+":
                if(left.getValue() instanceof String || right.getValue() instanceof
                        String){
                    String leftStr;
                    String rightStr;

                    leftStr = left.getValue().toString();
                    rightStr = right.getValue().toString();

                    res = leftStr + rightStr;
                }
                else if ((left.getValue() instanceof BigDecimal) &&
                        (right.getValue() instanceof BigDecimal)){
                    res = ((BigDecimal)left.getValue()).add((BigDecimal)
                            right.getValue());
                }
                else if((left.getValue() instanceof BigInteger) &&
                        (right.getValue() instanceof BigInteger)){
                    res = ((BigInteger)left.getValue()).add((BigInteger)
                            right.getValue());
                }
                else
                    throw new RuntimeException("Not a numeric type.");
                break;
            case "-":
                if ((left.getValue() instanceof BigDecimal) && (right.getValue()
                        instanceof BigDecimal)){
                    res = ((BigDecimal)left.getValue()).subtract((BigDecimal)
                            right.getValue());
                }
                else if((left.getValue() instanceof BigInteger) &&
                        (right.getValue() instanceof BigInteger)){
                    res = ((BigInteger)left.getValue()).subtract((BigInteger)
                            right.getValue());
                }
                else
                    throw new RuntimeException("Not a numeric type.");
                break;
            case "*":
                if ((left.getValue() instanceof BigDecimal && right.getValue() instanceof BigDecimal)){res = ((BigDecimal)left.getValue()).multiply((BigDecimal)
                        right.getValue());
                }
                else if (left.getValue() instanceof BigInteger && right.getValue()
                        instanceof BigInteger){
                    res = ((BigInteger)left.getValue()).multiply((BigInteger)
                            right.getValue());
                }
                else
                    throw new RuntimeException("Not a numeric type.");
                break;
            case "/":
                try {
                    if ((left.getValue() instanceof BigDecimal && right.getValue()
                            instanceof BigDecimal)) {
                        if (right.getValue().equals(0))
                            throw new RuntimeException("The denominator cannot be zero");
                        res = ((BigDecimal) left.getValue()).divide((BigDecimal)
                                right.getValue(), RoundingMode.HALF_EVEN);
                    } else if ((left.getValue() instanceof BigInteger &&
                            right.getValue() instanceof BigInteger)) {
                        if (right.getValue().equals(0))
                            throw new RuntimeException("The denominator cannot be zero");
                        res = ((BigInteger) left.getValue()).divide((BigInteger)
                                right.getValue());
                    } else {
                        throw new RuntimeException("Not a numeric type.");
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                break;
            case "<":
                if (!(left.getValue() instanceof Comparable<?> && right.getValue()
                        instanceof Comparable<?>)
                        || (left.getValue().getClass().getName() !=
                        right.getValue().getClass().getName()))
                {throw new RuntimeException("Invalid types");
                }
                else{
                    int i =
                            ((Comparable<Object>)left.getValue()).compareTo(right.getValue());
                    res = true ? i <0 : false;
                }
//comparable
                break;
            case ">":
                if (!(left.getValue() instanceof Comparable<?> && right.getValue()
                        instanceof Comparable<?>)
                        || (left.getValue().getClass().getName() !=
                        right.getValue().getClass().getName()))
                {
                    throw new RuntimeException("Invalid types");
                }
                else{
                    int i = ((Comparable<Object>)left.getValue()).compareTo(right.getValue());
                    res = true ? i >0 : false;
                }
                break;
            case "<=":
                if (!(left.getValue() instanceof Comparable<?> && right.getValue()
                        instanceof Comparable<?>)
                        || (left.getValue().getClass().getName() !=
                        right.getValue().getClass().getName()))
                {
                    throw new RuntimeException("Invalid types");
                }
                else{
                    int i =((Comparable<Object>)left.getValue()).compareTo(right.getValue());
                    res = true ? i <=0 : false;
                }
                break;
            case ">=":
                if (!(left.getValue() instanceof Comparable<?> && right.getValue()
                        instanceof Comparable<?>)
                        || (left.getValue().getClass().getName() !=
                        right.getValue().getClass().getName()))
                {
                    throw new RuntimeException("Invalid types");
                }
                else{
                    int i = ((Comparable<Object>)left.getValue()).compareTo(right.getValue());
                    res = true ? i >=0 : false;
                }
                break;
            case "==":



                res = true ? (left.getValue().equals(right.getValue()) ) : false;
                break;
            case "!=":
                res = false ? (left.getValue().equals(right.getValue()) ) : true;
                break;
            default:
                throw new RuntimeException("No binary operation like this");
        };
        return Environment.create(res);
    }
    @Override
    public Environment.PlcObject visit(Ast.Expression.Access ast) {
        if (ast.getReceiver().isPresent()){
            Environment.PlcObject current_scope = visit(ast.getReceiver().get());
            return current_scope.getField(ast.getName()).getValue();
        }
        return scope.lookupVariable(ast.getName()).getValue();
    }
    @Override
    public Environment.PlcObject visit(Ast.Expression.Function ast) {
        ArrayList<Environment.PlcObject> arguments = new ArrayList<Environment.PlcObject>();
        for (int i = 0; i < ast.getArguments().size(); i++){
            arguments.add(visit(ast.getArguments().get(i)));
        }

        if (ast.getReceiver().isPresent()){
            Environment.PlcObject receiver = visit(ast.getReceiver().get());
            if(receiver.getValue() instanceof String) {
                Environment.PlcObject object =
                        scope.lookupVariable(receiver.getValue().toString()).getValue();
                return object.callMethod(ast.getName(), arguments);
            }
            else
                throw new RuntimeException("Not an instance of a literal.");
        }
        return scope.lookupFunction(ast.getName(),
                ast.getArguments().size()).invoke(arguments);
    }
    /**
     * Helper function to ensure an object is of the appropriate type.
     */
    private static <T> T requireType(Class<T> type, Environment.PlcObject object) {
        if (type.isInstance(object.getValue())) {
            return type.cast(object.getValue());
        } else {
            throw new RuntimeException("Expected type " + type.getName() + ",received " + object.getValue().getClass().getName() + ".");
        }
    }
    /**
     * Exception class for returning values.
     */
    public static class Return extends RuntimeException {
        private final Environment.PlcObject value;
        private Return(Environment.PlcObject value) {
            this.value = value;
        }
        public Environment.PlcObject get(){
            return value;
        }
    }
}



