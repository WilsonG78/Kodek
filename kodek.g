grammar Kodek;

options {
    language = C;
    output = AST;
}

tokens {
    INDENT;
    DEDENT;
}

// --- Parser Rules ---

program
    : statement* EOF
    ;

statement
    : ( varDecl
      | assignment
      | ifStmt
      | forLoop
      | whileLoop
      | functionDef
      | functionCall
      | returnStmt
      | readStmt
      | writeStmt
      | fileStmt
      | breakStmt
      | continueStmt
      ) NEWLINE
    ;

// Types
type
    : 'liczba'
    | 'ułamek'
    | 'tekst'
    | 'logiczny'
    | 'lista'
    ;

// Variable Declaration and Assignment
varDecl
    : 'zmienna' type ID ('=' expression)?
    ;

assignment
    : ID '=' expression
    | listAccess '=' expression
    ;

// Expressions
expression
    : logicalOr
    ;

logicalOr
    : logicalAnd ('lub' logicalAnd)*
    ;

logicalAnd
    : negation ('oraz' negation)*
    ;

negation
    : 'nie' negation
    | comparison
    ;

comparison
    : arithmetic (compOp arithmetic)*
    ;

compOp
    : '==' | '!=' | '<' | '>' | '<=' | '>='
    ;

arithmetic
    : term (('+' | '-') term)*
    ;

term
    : factor (('*' | '/' | '%') factor)*
    ;

factor
    : base ('^' factor)?
    ;

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

listLiteral
    : '[' (expression (',' expression)*)? ']'
    ;

// Control Flow
ifStmt
    : 'jeśli' '(' expression ')' ':' block
      ('inaczej' 'jeśli' '(' expression ')' ':' block)*
      ('inaczej' ':' block)?
    ;

forLoop
    : 'dla' ID 'od' expression 'do' expression ':' block
    | 'dla' ID 'w' expression ':' block
    ;

whileLoop
    : 'dopóki' '(' expression ')' ':' block
    ;

block
    : NEWLINE INDENT statement* DEDENT
    ;

// Functions
functionDef
    : 'funkcja' ID '(' paramList? ')' ('zwraca' type)? ':' block
    ;

paramList
    : type ID (',' type ID)*
    ;

functionCall
    : ID '(' argumentList? ')'
    ;

argumentList
    : expression (',' expression)*
    ;

returnStmt
    : 'zwróć' expression
    ;

// I/O Operations
readStmt
    : 'czytaj' '(' ID ')'
    ;

writeStmt
    : ('pisz' | 'piszln') '(' expression ')'
    ;

// File Operations
fileStmt
    : 'otwórz' '(' expression ',' ID ')'
    | 'zamknij' '(' ID ')'
    ;

// Loop Control
breakStmt
    : 'przerwij'
    ;

continueStmt
    : 'kontynuuj'
    ;

// List/Array Access
listAccess
    : ID '[' expression ']'
    ;

// --- Lexer Rules ---

BOOLEAN
    : 'prawda' | 'fałsz'
    ;

ID
    : LETTER (LETTER | DIGIT | '_')*
    ;

NUMBER
    : DIGIT+ ('.' DIGIT+)?
    ;

STRING
    : '"' ~('"')* '"'
    ;

fragment DIGIT
    : '0'..'9'
    ;

fragment LETTER
    : 'a'..'z' | 'A'..'Z'
    | 'Ą' | 'ą' // Ą, ą
    | 'Ć' | 'ć' // Ć, ć
    | 'Ę' | 'ę' // Ę, ę
    | 'Ł' | 'ł' // Ł, ł
    | 'Ń' | 'ń' // Ń, ń
    | 'Ó' | 'ó' // Ó, ó
    | 'Ś' | 'ś' // Ś, ś
    | 'Ź' | 'ź' // Ź, ź
    | 'Ż' | 'ż' // Ż, ż
    ;

NEWLINE
    : ('\r'? '\n')+
    ;

WS
    : (' ' | '\t')+ { $channel = HIDDEN; }
    ;

// Virtual tokens INDENT and DEDENT are injected by a pre-processing step
// to handle Python-style indentation
