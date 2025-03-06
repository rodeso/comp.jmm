grammar Javamm;

@header {
    package pt.up.fe.comp2025;
}

CLASS : 'class' ;
INT : 'int' ;
BOOL : 'boolean' ;
PUBLIC : 'public' ;
RETURN : 'return' ;

INTEGER :  '0'
        | [1-9][0-9]*;


ID : [a-zA-Z_$][a-zA-Z_$0-9]* ;

WS : [ \t\n\r\f]+ -> skip ;

program
    : (importDecl)* classDecl EOF
    ;

importDecl
    : 'import' (path+=ID '.' )* name=ID ';'
    ;

classDecl locals[boolean isSub=false]
    : CLASS name=ID ('extends' {$isSub = true;} parent=ID)?
        '{'
        varDecl*
        methodDecl*
        '}'
    ;

varDecl
    : type name=ID ';'
    ;

type
    : name= INT '[' ']'
    | name= INT '...'
    | name= BOOL
    | name= INT
    | name= ID
    |;


methodDecl locals[boolean isPublic=false]
    : (PUBLIC {$isPublic=true;})?
        type name=ID
        '(' paramList? ')'
        '{' varDecl* stmt* 'return' expr ';' '}'
    ;

paramList
    : param (',' param)*
    ;

param
    : type name=ID
    ;

stmt
    : expr '=' expr ';' #AssignStmt //
    | RETURN expr ';' #ReturnStmt //
    | ID '['expr']' '=' ID #AcessingArrayStmt
    | 'if' '(' expr ')' stmt 'else' stmt #IfStmt
    | 'while' '('expr')' stmt #WhileStmt
    | expr ';' #SimpleExpr
    | '{' (stmt)* '}' #IfOrLoopStmt
    | 'for' '(' #ForStmt
    ;

expr
    : op= '(' expr op= ')' #PriorityExpr
    | op= '!' expr #UnaryExpr
    | expr op= ('*' | '/') expr #BinaryExpr
    | expr op= ('+' | '-') expr #BinaryExpr
    | expr op= '<' expr #BinaryExpr
    | expr op= '&&' expr #BinaryExpr
    | expr '[' expr ']' #ListExpr
    | expr '.' 'length' #LengthExpr
    | expr '.' ID '(' ( expr ( ',' expr )* )? ')' #ClassFunctionExpr
    | expr 'new' expr 'int' expr '[' expr ']' #Label
    | 'new' ID '(' ')' #New
    | value=INTEGER #IntegerLiteral //
    | value= ('true' | 'false') #BooleanLiteral
    | name=ID #VarRefExpr //
    ;
