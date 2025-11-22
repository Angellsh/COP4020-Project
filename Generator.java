package plc.project;

import javax.lang.model.type.NullType;
import java.io.PrintWriter;

public final class Generator implements Ast.Visitor<Void> {

    private final PrintWriter writer;
    private int indent = 0;

    public Generator(PrintWriter writer) {
        this.writer = writer;
    }


    private void print(Object... objects) {
        for (Object object : objects) {
            if (object instanceof Ast) {
                visit((Ast) object);
            } else {
                writer.write(object.toString());
            }
        }
    }

    private void newline(int indent) {
        writer.println();
        for (int i = 0; i < indent; i++) {
            writer.write("    ");
        }
    }

    @Override
    public Void visit(Ast.Source ast) {
        print("public class Main {\n");
        indent++;


        for (int i=0;i<ast.getFields().size();i++){
            newline(indent);
            print(ast.getFields().get(i));
        }
        if(!ast.getFields().isEmpty())
            print("\n");

        newline(indent);
        print("public static void main(String[] args) {");
        indent++;
        newline(indent);
        print("System.exit(new Main().main());");//
        indent--;
        newline(indent);
        print("}");
        print("\n");


        for (int i=0;i<ast.getMethods().size();i++){
            if(i>0)
                print("\n");
            newline(indent);
            print(ast.getMethods().get(i));

        }
        indent--;
        newline(indent);
        newline(indent);
        print("}");
        return null;

    }

    @Override
    public Void visit(Ast.Field ast) {
        if( ast.getConstant()){
            print("final ");
        }
        print(ast.getVariable().getType().getJvmName());
        print(" ");
        print(ast.getName());


        if(ast.getValue().isPresent()){
            print(" = ");
            print(ast.getValue().get());
        }
        print(";");
        return null;

    }

    @Override
    public Void visit(Ast.Method ast) {
        boolean FuncReturns = true;
        //newline(indent);
        print(ast.getFunction().getType().getJvmName());
        print(" ");
        print(ast.getFunction().getName());
        if (ast.getFunction().getName().equals("main"))
            FuncReturns = false;

        print("(");
        for (int i=0; i<ast.getParameters().size();i++){
            if(i>0)
                print(", ");
            print(ast.getFunction().getParameterTypes().get(i).getJvmName());
            print(" ");
            print(ast.getParameters().get(i));
        }
        print(") {");
        indent++;
        for (int i=0; i<ast.getStatements().size(); i++){
            newline(indent);
            print(ast.getStatements().get(i));
            if (ast.getStatements().get(i) instanceof Ast.Statement.Return)
                FuncReturns=true;
        }
        if (!FuncReturns) {
            newline(indent);
            print("return 0;");
        }

        indent--;
        newline(indent);
        print("}");
     //  indent--;
        return null;


    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {

        print(ast.getExpression());
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        print(ast.getVariable().getType().getJvmName()); //fix it
        print(" ");
        print(ast.getName());
        if(ast.getValue().isPresent()) {
            print(" = ");
            print(ast.getValue().get());
        }
        print(";");
        return null;



    }
    public void statementHelper(Ast.Statement.Assignment ast){
       // print(ast.getReceiver().getType());
     //   print(" ");
        print(ast.getReceiver());
        print(" = ");
        print(ast.getValue());

    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {

        statementHelper(ast);

        print(";");
        return null;

    }

    @Override
    public Void visit(Ast.Statement.If ast) {

        print("if (");
        print(ast.getCondition());
        print(") {");
        indent++;
        for (int i = 0; i < ast.getThenStatements().size(); i++) {
            newline(indent);
            print(ast.getThenStatements().get(i));
        }
        if (!ast.getElseStatements().isEmpty()){
            newline(indent-1);
            print("} else {");
            for(int i=0; i< ast.getElseStatements().size(); i++){
                newline(indent);
                print(ast.getElseStatements().get(i));
            }

        }
        indent--;
        newline(indent);
        print("}");
        return null;



    }

    @Override
    public Void visit(Ast.Statement.For ast) {


        print("for ( ");
        if (ast.getInitialization() != null){
            print(ast.getInitialization());
            print(" ");
        }else {
        print("; ");
        }
        print(ast.getCondition());
        print("; ");
        if( ast.getIncrement() != null) {
            statementHelper((Ast.Statement.Assignment) ast.getIncrement());
            print(" ) {");

        }
        else {
            print(") {");
        }

        indent++;
        for (int i=0; i<ast.getStatements().size(); i++){
            newline(indent);
            print(ast.getStatements().get(i));
        }
        indent--;
        newline(indent);
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.While ast) {
        print ("while");
        print(" ");
        print("(");
        print(ast.getCondition());
        print(")");
        if (!ast.getStatements().isEmpty()){
            print(" {");
            indent++;

        }
        else{
            print(";");
            return null;
        }
        for (int i =0; i< ast.getStatements().size();i++){
            newline(indent);
            print(ast.getStatements().get(i));
            //print(";");
        }
        if (!ast.getStatements().isEmpty()) {
            indent--;
            newline(indent);
            print("}");
        }

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        print("return ");
        print(ast.getValue());
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {

        if (ast.getType().equals(Environment.Type.STRING)
            || ast.getType().equals(Environment.Type.CHARACTER)){
            print("\"" + ast.getLiteral() + "\"");

        }
        else
            print(ast.getLiteral());
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {
        print("(");
        print(ast.getExpression());
        print(")");
        return null;

    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        print(ast.getLeft());
        print(" ");
        print(ast.getOperator());
        print(" ");
        print(ast.getRight());

        return null;

    }

    @Override
    public Void visit(Ast.Expression.Access ast) {
        if(ast.getReceiver().isPresent()){
            print(ast.getReceiver().get());
            print(".");
        }
        print(ast.getName());
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {
    if(ast.getReceiver().isPresent()){
        print(ast.getReceiver().get());
        print(".");

    }
    print(ast.getFunction().getJvmName());
    print("(");
    for(int i=0; i<ast.getArguments().size(); i++){
        print(ast.getArguments().get(i));
        if(i<ast.getArguments().size()-1)
            print(", ");
    }
    print(")");

    return null;



    }
}
