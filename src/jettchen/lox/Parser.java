package jettchen.lox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static jettchen.lox.TokenType.*;

public class Parser {
    private static class ParseError extends RuntimeException {}
    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens){
        this.tokens=tokens;
    }

    List<Stmt> parse(){
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()){
            statements.add(declaration());
        }
        return statements;
    }

    private Expr expression(){
        return assignment();
    }


    private Stmt declaration(){
        try{
            if (match(CLASS)) return classDeclaration();
            if (match(FUN)) return function("function");
            if (match(VAR))return varDeclaration();
            return statement();
        }catch (ParseError error){
            synchronize();
            return null;
        }
    }


    private Stmt classDeclaration() {
        Token name = consume(IDENTIFIER, "Expect class name");
        Expr.Variable superclass = null;
        if (match(LESS)){
            consume(IDENTIFIER, "Expect superclass name.");
            superclass = new Expr.Variable(previous());
        }

        consume(LEFT_BRACE, "Expect '{' before class body");
        List<Stmt.Function> methods = new ArrayList<>();
        while (!check(RIGHT_BRACE) && !isAtEnd()){
            methods.add(function("method"));
        }
        consume(RIGHT_BRACE, "Expect '}' before class body");
        return new Stmt.Class(name, superclass, methods);
    }

    private Stmt.Function function(String kind){
        Token name = consume(IDENTIFIER, "Expect "+kind+" name.");
        consume(LEFT_PAREN, String.format("Expect '(' after %s name",kind));
        List<Token> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)){
            do {
                if (parameters.size()>=255){
                    error(peek(),
                            "Can't have more than 255 parameters");
                }
                parameters.add(consume(IDENTIFIER, "Expect parameter name."));
            }while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.");
        consume(LEFT_BRACE, String.format("'Expect '{' before %s body",kind));
        List<Stmt> body = block();
        return new Stmt.Function(name, parameters, body);
    }

    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");
        Expr initializer = null;
        if (match(EQUAL)){
            initializer = expression();
        }
        consume(SEMICOLON, "Expect ';' after variable declaration");
        return new Stmt.Var(name, initializer);
    }

    private Stmt statement(){
        if(match(PRINT)) return printStatement();
        if (match(RETURN)) return returnStatement();
        if (match(IF)) return ifStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());
        if (match(WHILE)) return whileStatement();
        if (match(FOR))return forStatement();
        return expressionStatement();
    }

    private Stmt returnStatement() {
        Token keyword = previous();
        Expr value = null;
        if (!check(SEMICOLON)){
            value = expression();
        }
        consume(SEMICOLON, "Expect ';' after return value");
        return new Stmt.Return(keyword, value);
    }

    private Stmt forStatement(){
        consume(LEFT_PAREN, "expect '(' after 'for'.");
        Stmt initializer;
        if (match(SEMICOLON)){
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        Expr condition = null;
        if(!check(SEMICOLON)){
            condition=expression();
        }
        consume(SEMICOLON, "Expect ';' after loop condition");

        Expr increment = null;
        if (!check(RIGHT_PAREN)){
            increment=expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses");

        Stmt body = statement();
        if (increment!=null){
            body = new Stmt.Block(
                    Arrays.asList(
                            body,
                            new Stmt.Expression(increment)
                    )
            );
        }
        if (condition==null)condition = new Expr.Literal(true);
        body = new Stmt.While(condition, body);
        if(initializer!=null){
            body = new Stmt.Block(Arrays.asList(initializer,body));
        }
        return body;
    }

    private Stmt whileStatement() {
        consume(LEFT_PAREN, "expect '(' after 'while'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "expect ')' after while condition");
        Stmt body = statement();
        return new Stmt.While(condition,body);
    }

    private Stmt ifStatement() {
        consume(LEFT_PAREN, "expect '(' after 'if'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "expect ')' after if condition");
        Stmt then_stmt = statement();
        Stmt else_stmt = null;
        if (match(ELSE)){
            else_stmt = statement();
        }
        return new Stmt.If(condition, then_stmt, else_stmt);
    }

    private Stmt printStatement(){
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    private Stmt expressionStatement(){
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Expression(value);
    }

    private List<Stmt> block(){
        List<Stmt> stmts = new ArrayList<>();
        while(!check(RIGHT_BRACE) && !isAtEnd()){
            stmts.add(declaration());
        }
        consume(RIGHT_BRACE, "Expect '}' after block.");
        return stmts;
    }

    private Expr assignment(){
        Expr expr = or();
        if (match(EQUAL)){
            Token eq = previous();
            Expr ass = comma();
            if(expr instanceof Expr.Variable){
                return new Expr.Assign(((Expr.Variable) expr).name, ass);
            } else if(expr instanceof Expr.Get){
                Expr.Get get = (Expr.Get) expr;
                return new Expr.Set(get.object, get.name, ass);
            }
            throw new RuntimeError(eq, "Invalid assignment target.");
        }
        return expr;
    }

    private Expr or (){
        Expr expr = and();
        while(match(OR)){
            Token operand = previous();
            Expr right = and();
            expr = new Expr.Logical(expr,operand,right);
        }
        return expr;
    }

    private Expr and(){
        Expr expr = equality();
        while (match(AND)){
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr,operator,right);
        }
        return expr;
    }

    private boolean match(TokenType... types){
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private boolean check(TokenType type){
        if(isAtEnd()) return false;
        return peek().type == type;
    }

    private Token advance(){
        if(!isAtEnd())current++;
        return previous();
    }

    private boolean isAtEnd(){
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current-1);
    }

    private Expr comma(){
        Expr expr = ternary();
        while (match(COMMA)){
            Expr right = ternary();
            expr = new Expr.Comma(expr,right);
        }
        return expr;
    }

    private Expr ternary(){
        Expr condition = equality();
        if (match(QUESTION)){
            Expr then = equality();
            if(match(COLON)){
                Expr otherwise = equality();
                return new Expr.Ternary(condition,then,otherwise);
            }else {
                throw error(peek(), "There must be a `:` operator in ternary operators!");
            }
        }
        return condition;
    }

    private Expr equality(){
        Expr expr = comparison();
        while (match(BANG_EQUAL, EQUAL_EQUAL)){
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr comparison() {
        Expr expr = term();
        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr term(){
        Expr expr = factor();
        while (match(MINUS, PLUS)){
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr,operator,right);
        }
        return expr;
    }

    private Expr factor(){
        Expr expr = unary();
        while(match(SLASH, STAR)){
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        if (match(BANG, MINUS)){
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }
        return call();
    }

    private Expr call(){
        Expr expr = primary();

        while (true){
            if (match(LEFT_PAREN)){
                expr = finishCall(expr);
            } else if (match(DOT)) {
                Token name = consume(IDENTIFIER, "Expect property name after '.'.");
                expr = new Expr.Get(expr, name);
            } else break;
        }
        return expr;
    }

    private Expr finishCall(Expr expr) {
        List<Expr> args = new ArrayList<>();
        if (!check(RIGHT_PAREN)){
            do {
                if(args.size()>=255){
                    error(peek(), "Can't have more than 255 arguments");
                }
                args.add(expression());
            } while (match(COMMA));
        }
        Token paren = consume(RIGHT_PAREN, "Expect ')' after function call!");
        return new Expr.Call(expr, paren,args);
    }

    private Expr primary(){
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);
        if (match(NUMBER,STRING)){
            return new Expr.Literal(previous().literal);
        }
        if (match(SUPER)) {
            Token keyword = previous();
            consume(DOT, "Expect ',' after 'super'.");
            Token method = consume(IDENTIFIER,
                    "Expect superclass method name.");
            return new Expr.Super(keyword, method);
        }
        if (match(THIS)) return new Expr.This(previous());
        if (match(IDENTIFIER)){
            return new Expr.Variable(previous());
        }
        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expect expression.");
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message);
    }

    private ParseError error(Token token, String message){
        Lox.error(token, message);
        return new ParseError();
    }

    private void synchronize(){
        advance();
        if(previous().type == SEMICOLON) return;
        switch (peek().type){
            case CLASS:
            case FUN:
            case VAR:
            case FOR:
            case IF:
            case WHILE:
            case PRINT:
            case RETURN:
                return;
        }
        advance();
    }
}
