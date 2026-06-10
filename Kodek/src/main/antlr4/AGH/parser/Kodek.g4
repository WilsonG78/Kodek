grammar Kodek;

program
    : statement* EOF
    ;

statement
    : simpleStmt
    | blockStmt
    ;

simpleStmt
    : varDecl
    | assignment
    | functionCall
    | returnStmt
    | readStmt
    | writeStmt
    | fileStmt
    ;

blockStmt
    : ifStmt
    | forLoop
    | whileLoop
    | functionDef
    ;

loopStatement
    : loopSimpleStmt
    | loopBlockStmt
    ;

loopSimpleStmt
    : varDecl
    | assignment
    | functionCall
    | returnStmt
    | readStmt
    | writeStmt
    | fileStmt
    | breakStmt
    | continueStmt
    ;

loopBlockStmt
    : loopIfStmt
    | forLoop
    | whileLoop
    | functionDef
    ;

typeName
    : 'liczba'
    | 'ułamek'
    | 'tekst'
    | 'logiczny'
    | 'lista'
    ;

varDecl
    : 'zmienna' typeName ID ('=' expression)?
    ;

assignment
    : ID '=' expression
    | ID '[' expression ']' '=' expression
    ;

expression : logicalOr ;

logicalOr  : logicalAnd ('lub'  logicalAnd)* ;
logicalAnd : negation   ('oraz' negation)*   ;

negation
    : 'nie' negation
    | comparison
    ;

comparison : arithmetic (compOp arithmetic)* ;
strictComparison : arithmetic compOp arithmetic ;

compOp : '==' | '!=' | '<' | '>' | '<=' | '>=' ;

arithmetic : term     (('+' | '-') term)*           ;
term       : factor   (('*' | '/' | '%') factor)*  ;
factor     : base     ('^' factor)?                 ;

base
    : atom
    | '(' expression ')'
    ;

atom
    : NUMBER
    | STRING
    | BOOLEAN
    | listLiteral
    | functionCall
    | listAccess
    | ID
    ;

listLiteral : '[' (expression (',' expression)*)? ']' ;
listAccess  : ID '[' expression ']'                   ;


condition
    : condAnd ('lub' condAnd)*
    ;

condAnd
    : condNeg ('oraz' condNeg)*
    ;

condNeg
    : 'nie' condNeg
    | BOOLEAN
    | ID
    | '(' condition ')'
    | strictComparison
    ;


ifStmt
    : 'jeśli' '(' condition ')' block
      ('inaczej' 'jeśli' '(' condition ')' block)*
      ('inaczej' block)?
    ;

loopIfStmt
    : 'jeśli' '(' condition ')' loopBlock
      ('inaczej' 'jeśli' '(' condition ')' loopBlock)*
      ('inaczej' loopBlock)?
    ;

forLoop
    : 'dla' ID 'od' expression 'do' expression loopBlock
    | 'dla' ID 'w' expression loopBlock
    ;

whileLoop : 'dopóki' '(' condition ')' loopBlock ;

block     : '{' statement*     '}' ;
loopBlock : '{' loopStatement* '}' ;

breakStmt    : 'przerwij'  ;
continueStmt : 'kontynuuj' ;


functionDef
    : 'funkcja' ID '(' paramList? ')' ('zwraca' typeName)? block
    ;

paramList
    : typeName ID (',' typeName ID)*
    ;

functionCall : ID '(' argumentList? ')' ;
argumentList : expression (',' expression)* ;

returnStmt : 'zwróć' expression ;


readStmt  : 'czytaj' '(' ID ')' ;
writeStmt : ('pisz' | 'piszln') '(' expression ')' ;


fileStmt
    : 'otwórz'  '(' expression ',' ID ')'
    | 'zamknij' '(' ID ')'
    ;


BOOLEAN : 'prawda' | 'fałsz' ;

ID     : LETTER (LETTER | DIGIT | '_')* ;
NUMBER : DIGIT+ ('.' DIGIT+)? ;
STRING : '"' ~["\r\n]* '"' ;

COMMENT : '#' ~[\r\n]* -> skip ;
NEWLINE : '\r'? '\n' -> skip ;

WS : [ \t]+ -> skip ;

fragment DIGIT  : [0-9] ;

fragment LETTER
    : [a-zA-Z]
    | [Ąą]
    | [Ćć]
    | [Ęę]
    | [Łł]
    | [Ńń]
    | [Óó]
    | [Śś]
    | [Źź]
    | [Żż]
    ;