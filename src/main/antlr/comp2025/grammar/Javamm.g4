grammar Javamm;

@header {
    package pt.up.fe.comp2025;
}

CLASS : 'class' ;
INT : 'int' ;
BOOL : 'boolean' ;
STRING : 'String';
PUBLIC : 'public' ;
RETURN : 'return' ;

INTEGER :  '0'
        | [1-9][0-9]*;


ID : [a-zA-Z_$][a-zA-Z_$0-9]* ;

WS : [ \t\n\r\f]+ -> skip ;
L_COMMENT : '//' (.*?) [\r\n] -> skip;
MULTI_L_COMMENT : '/*' (.*?) '*/' -> skip;

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
    | type name=ID op1='[' op2=']' ';'
    ;

type
    : name=baseType (op1='[' op2=']')? // Arrays are handled separately
    ;

baseType
    : name=INT
    | name=BOOL
    | name=STRING
    | name=INT '...'
    | name=ID
    | name='void'
    ;



methodDecl locals[boolean isPublic=false]
    : (PUBLIC {$isPublic=true;})?
        type name=ID
        '(' paramList? ')'
        '{' varDecl* stmt* 'return' expr ';' '}'
    | (PUBLIC {$isPublic=true;})? 'static' 'void' name='main' '(' STRING '[' ']' ID ')' '{' ( varDecl
       )* ( stmt )* '}'
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
    | 'if' '(' expr ')' stmt elseifStmt* elseStmt? #IfStmt
    | 'while' '('expr')' stmt #WhileStmt
    | expr ';' #SimpleExpr
    | '{' (stmt)* '}' #BracketsStmt
    | 'for' '(' expr ')' stmt #ForStmt
    ;

elseifStmt
    : 'else if' '('expr ')' stmt
    ;

elseStmt
    : 'else' stmt
    ;

expr
    : op= '(' expr op= ')' #PriorityExpr
    | op= '!' expr #UnaryExpr
    | expr op= ('*' | '/') expr #BinaryExpr
    | expr op= ('+' | '-') expr #BinaryExpr
    | expr op= '<' expr #BinaryExpr
    | expr op=('<=' | '==' | '!=' | '+=' | '-=' | '*=' | '/=') expr #BinaryExpr
    | expr op= '&&' expr #BinaryExpr
    | expr '[' expr ']' #ArrayAccess
    | '[' (expr (',' expr)*)? ']' #ArrayLiteral
    | expr '.' 'length' #LengthExpr
    | expr '.' name=ID '(' ( expr ( ',' expr )* )? ')' #ClassFunctionExpr
    | expr 'new' expr 'int' expr '[' expr ']' #Label
    | 'new' type '[' expr ']' #ArrayCreation
    | 'new' name=ID '(' (expr (',' expr) *)?')' #New
    | value=INTEGER #IntegerLiteral //
    | value= ('true' | 'false') #BooleanLiteral
    | value = 'this' #ObjectReference
    | name=ID #VarRefExpr //
    | name=ID op=('++' | '--') #IncrementByOne
    ;
