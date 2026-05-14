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
    | breakStmt
    | continueStmt
    ;

blockStmt
    : ifStmt
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

forLoop
    : 'dla' ID 'od' expression 'do' expression block
    | 'dla' ID 'w' expression block
    ;

whileLoop : 'dopóki' '(' condition ')' block ;

block : '{' statement* '}' ;

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
    | [Ąą]   // Ą ą
    | [Ćć]   // Ć ć
    | [Ęę]   // Ę ę
    | [Łł]   // Ł ł
    | [Ńń]   // Ń ń
    | [Óó]   // Ó ó
    | [Śś]   // Ś ś
    | [Źź]   // Ź ź
    | [Żż]   // Ż ż
    ;
