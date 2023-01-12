package jettchen.lox;

import java.util.List;

class LoxFunction implements LoxCallable {
    private final Stmt.Function declaration;
    private final Environment closure;

    private boolean isInitializer;
    LoxFunction(Stmt.Function declaration, Environment closure, boolean isInitializer){
        this.closure = closure;
        this.declaration = declaration;
        this.isInitializer=isInitializer;
    }

    @Override
    public int arity() {
        return declaration.params.size();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Environment env = new Environment(closure);
        for (int i = 0; i < declaration.params.size(); i++) {
            env.define(declaration.params.get(i).lexeme, arguments.get(i));
        }
        try {
            interpreter.executeBlock(declaration.body, env);
        } catch (Return r){
            if (isInitializer)return closure.getAt(0,"this");
            return r.value;
        }
        if (isInitializer)return closure.getAt(0, "this");
        return null;
    }

    @Override
    public String toString() {
        return String.format("<fn %s>",declaration.name.lexeme);
    }

    LoxFunction bind(LoxInstance instance) {
        Environment environment = new Environment(closure);
        environment.define("this", instance);
        return new LoxFunction(declaration, environment, isInitializer);
    }
}
