package plc.project;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * See the specification for information about what the different visit
 * methods should do.
 */
public final class Analyzer implements Ast.Visitor<Void> {
    public Scope scope;
    private Ast.Method method;

    public Analyzer(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL);
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Void visit(Ast.Source ast) {
        ast.getFields().forEach(this::visit);
        ast.getMethods().forEach(this::visit);


        try{

            if (scope.lookupFunction("main", 0).getType().equals(Environment.Type.INTEGER)){
                return null;
            }

        }
        catch(Exception e){

            throw new RuntimeException("Main is not defined in this scope.");
        }

        return null;

    }

    @Override
    public Void visit(Ast.Field ast) {
        if(ast.getValue().isPresent()){
            visit(ast.getValue().get());
            requireAssignable(ast.getValue().get().getType(), Environment.getType(ast.getTypeName()));

        }
        if (ast.getConstant() && ast.getValue().isEmpty()){
            throw new RuntimeException("A constant field must have an initial value assigned.");

        }
        scope.defineVariable(ast.getName(), ast.getName(), Environment.getType(ast.getTypeName()), ast.getConstant(), Environment.NIL);
        ast.setVariable(scope.lookupVariable(ast.getName()));

       return null;
    }

    @Override
    public Void visit(Ast.Method ast) {
        try {
            List<Environment.Type> ParamTypes = new ArrayList<>();

            for (int i =0; i<ast.getParameterTypeNames().size(); i++){
                ParamTypes.add(Environment.getType(ast.getParameterTypeNames().get(i)));

            }

            if(ast.getReturnTypeName().isPresent()){
                scope.defineFunction(ast.getName(), ast.getName(), ParamTypes, Environment.getType(ast.getReturnTypeName().get()),  args  -> { return Environment.NIL;});
                ast.setFunction(scope.lookupFunction(ast.getName(), ParamTypes.size()));
            }

            else{
                scope.defineFunction(ast.getName(), ast.getName(), ParamTypes, Environment.Type.NIL,  args  -> { return Environment.NIL;});
                ast.setFunction(scope.lookupFunction(ast.getName(), ParamTypes.size()));

            }
            scope = new Scope(scope);

            for ( int i=0; i<ParamTypes.size(); i++){
                scope.defineVariable(ast.getParameters().get(i),ast.getParameters().get(i), ParamTypes.get(i), false, Environment.NIL);


            }
            for (int i = 0; i < ast.getStatements().size(); i++) {
               visit(ast.getStatements().get(i));
               if (ast.getStatements().get(i) instanceof Ast.Statement.Return){
                   if(ast.getReturnTypeName().isEmpty()){
                       throw new RuntimeException();
                   }


                   Environment.Type returntype = ((Ast.Statement.Return) ast.getStatements().get(i)).getValue().getType();

                   // if(!(Environment.getType(ast.getReturnTypeName().get()).equals(returntype))){
                    if(!scope.lookupVariable(ast.getName()).getType().equals(returntype)){
                       throw new RuntimeException("Return types differ.");
                   }
               }
           }
       } catch (RuntimeException e) {
           throw new RuntimeException(e);
       }
       finally{
           scope = scope.getParent();
       }
       return null;
       }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        visit(ast.getExpression());
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {

        try{
            if (ast.getValue().isPresent()) {
                if (ast.getTypeName().isPresent()) {
                    requireAssignable(Environment.getType(ast.getTypeName().get()), ast.getValue().get().getType());
                }
                visit(ast.getValue().get());
                scope.defineVariable(ast.getName(), ast.getName(), ast.getValue().get().getType(), false, Environment.NIL);
                ast.setVariable(scope.lookupVariable(ast.getName()));
            } else {
                if (!(ast.getTypeName().isPresent()))
                    throw new RuntimeException("No type is defined");


                scope.defineVariable(ast.getName(), ast.getName(), Environment.getType(ast.getTypeName().get()), false, Environment.NIL);
                ast.setVariable(scope.lookupVariable(ast.getName()));

            }


            return null;
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        try {
            visit(ast.getReceiver());
            visit(ast.getValue());
            if (!(ast.getReceiver() instanceof Ast.Expression.Access)
                    || !(ast.getReceiver().getType().equals(ast.getValue().getType()))) {
                throw new RuntimeException("Types mismatch");
            }
            if(((Ast.Expression.Access) ast.getReceiver()).getReceiver().isPresent()){
               Ast.Expression expr =  ((Ast.Expression.Access) ast.getReceiver()).getReceiver().get();
               if(expr.getType().getField(((Ast.Expression.Access) ast.getReceiver()).getName()).getConstant()){
                   throw new RuntimeException("The variable is constant.");

               }


            }
            else {

                if (scope.lookupVariable(((Ast.Expression.Access) ast.getReceiver()).getName()).getConstant()) {
                    throw new RuntimeException("The variable is constant.");
                }
           }
            return null;
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public Void visit(Ast.Statement.If ast) {

        try {
            visit(ast.getCondition());
            requireAssignable(ast.getCondition().getType(), Environment.Type.BOOLEAN);

            if (ast.getThenStatements().isEmpty()){
                throw new RuntimeException("Then Statements list is empty");
            }
            scope = new Scope(scope);

            ast.getThenStatements().forEach(this::visit);

            if (!ast.getElseStatements().isEmpty()) {

                ast.getElseStatements().forEach(this::visit);
            }
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
        finally{
            scope = scope.getParent();




        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.For ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Statement.While ast){
        visit(ast.getCondition());
        requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());
        try{
            scope = new Scope(scope);
            ast.getStatements().forEach(this::visit);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        finally{
            scope = scope.getParent();
        }
        return null;

    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        visit(ast.getValue());
        scope.defineVariable("return", "return",ast.getValue().getType(), false, Environment.NIL);

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {
        if (ast.getLiteral() instanceof BigInteger) {
            if (((BigInteger) ast.getLiteral()).compareTo( BigInteger.valueOf(Integer.MAX_VALUE)) > 0){
                throw new RuntimeException("Integer exceeds the limits.");
            }

            ast.setType(Environment.Type.INTEGER);
        }else if (ast.getLiteral() instanceof BigDecimal) {
            if (((BigDecimal) ast.getLiteral()).compareTo( BigDecimal.valueOf(Double.MAX_VALUE)) > 0){
                throw new RuntimeException("Decimal exceeds the limits");

            }
        }else if (ast.getLiteral() instanceof Boolean) {
                ast.setType(Environment.Type.BOOLEAN);

            } else if (ast.getLiteral() instanceof String) {
                ast.setType(Environment.Type.STRING);
            } else if (ast.getLiteral() instanceof Character) {
                ast.setType(Environment.Type.CHARACTER);
            } else
                ast.setType(Environment.Type.ANY);


                return null;
            }

    @Override
    public Void visit(Ast.Expression.Group ast) {
        if (ast.getExpression() instanceof Ast.Expression.Binary){
            visit(ast.getExpression());
            ast.setType(ast.getExpression().getType());

            return null;
        }
        throw new RuntimeException("Not a binary expression");


    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        visit(ast.getLeft());

        visit(ast.getRight());

        if (ast.getOperator().equals("OR") ||  ast.getOperator().equals("AND") ){
            requireAssignable(ast.getLeft().getType(), Environment.Type.BOOLEAN);
            requireAssignable(ast.getRight().getType(), Environment.Type.BOOLEAN);
            ast.setType(Environment.Type.BOOLEAN);
        }
        else if (Set.of("<", ">", "<=", ">=", "!=", "==").contains(ast.getOperator())) {
            requireAssignable(ast.getLeft().getType(), Environment.Type.COMPARABLE);
            requireAssignable(ast.getRight().getType(), Environment.Type.COMPARABLE);
            ast.setType(Environment.Type.BOOLEAN);

        }
        else if (ast.getOperator().equals( "+") &&
                ((ast.getRight().getType().equals(Environment.Type.STRING)
                || ast.getLeft().getType().equals(Environment.Type.STRING)))) {

                ast.setType(Environment.Type.STRING);


        }
        else if (Set.of("-", "*", "/", "+").contains(ast.getOperator())){

            if (ast.getRight().getType().equals(Environment.Type.INTEGER)){
                requireAssignable(ast.getLeft().getType(), Environment.Type.INTEGER);
                ast.setType(Environment.Type.INTEGER);

            }
            else if(ast.getRight().getType().equals(Environment.Type.DECIMAL)) {
                requireAssignable(ast.getLeft().getType(), Environment.Type.DECIMAL);
                ast.setType(Environment.Type.DECIMAL);

            }
            else
                throw new RuntimeException("Invalid operator's use.");
            return null;

        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Access ast) {

        if (ast.getReceiver().isPresent()){
            visit(ast.getReceiver().get());
            ast.setVariable(ast.getReceiver().get().getType().getField(ast.getName()));

        }
        else {
            ast.setVariable(scope.lookupVariable(ast.getName()));
        }

        return null;
    }






    @Override
    public Void visit(Ast.Expression.Function ast) {
       if (ast.getReceiver().isPresent()){
           visit(ast.getReceiver().get());

           Environment.Function func = ast.getReceiver().get().getType().getFunction(ast.getName(), ast.getArguments().size());
           List<Environment.Type> types = func.getParameterTypes();

           //check if passed args types match
           for (int i =0; i < ast.getArguments().size(); i++ ){
               visit(ast.getArguments().get(i));
               requireAssignable(ast.getArguments().get(i).getType(), types.get(i+1));
           }

           ast.setFunction(func);

       }
       else{
           Environment.Function func = scope.lookupFunction(ast.getName(), ast.getArguments().size());
           List<Environment.Type> types = func.getParameterTypes();

           //check if passed args types match
           for (int i =0; i < ast.getArguments().size(); i++ ){
               visit(ast.getArguments().get(i));

               requireAssignable(types.get(i), ast.getArguments().get(i).getType());
           }

           ast.setFunction(scope.lookupFunction(ast.getName(), ast.getArguments().size()));
       }
        return null;
    }

    public static void requireAssignable(Environment.Type target, Environment.Type type) {
            if (target.equals(Environment.Type.COMPARABLE)
                && (type.equals(Environment.Type.INTEGER) || type.equals(Environment.Type.DECIMAL)
                    || type.equals(Environment.Type.STRING) || type.equals(Environment.Type.CHARACTER)))
            {
            }
            else if (target.equals(Environment.Type.ANY)
                    && (type.equals(Environment.Type.INTEGER) || type.equals(Environment.Type.DECIMAL)
                    || type.equals(Environment.Type.STRING) || type.equals(Environment.Type.CHARACTER)
                    || type.equals(Environment.Type.BOOLEAN)  || type.equals(Environment.Type.COMPARABLE)
                    || type.equals(Environment.Type.ANY)  || type.equals(Environment.Type.NIL)))
            {
            }
            else if (!type.equals(target)){
                throw new RuntimeException("Incorrect assignable");
            }

    }

}
