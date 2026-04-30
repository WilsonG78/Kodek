grammar Kodek;

tokens { INDENT, DEDENT }

// Obsługa wcięć Python-style (INDENT/DEDENT wstrzykiwane przez nextToken)
@lexer::header {
import java.util.*;
}

@lexer::members {

private final Deque<Integer> indentStack = new ArrayDeque<>(Arrays.asList(0));
private final Queue<Token>   pending     = new LinkedList<>();

@Override
public Token nextToken() {
    if (!pending.isEmpty()) return pending.poll();

    Token t = super.nextToken();

    if (t.getType() == NEWLINE) {
        String text = t.getText();
        int newIndent = 0;
        boolean seenNl = false;
        for (char c : text.toCharArray()) {
            if (c == '\n')          { seenNl = true; newIndent = 0; }
            else if (seenNl) {
                if (c == ' ')       newIndent++;
                else if (c == '\t') newIndent += 4;
            }
        }
        int current = indentStack.peek();
        if (newIndent > current) {
            indentStack.push(newIndent);
            pending.offer(mk(KodekParser.INDENT, "INDENT"));
        } else {
            while (newIndent < indentStack.peek()) {
                indentStack.pop();
                pending.offer(mk(KodekParser.DEDENT, "DEDENT"));
            }
        }
    }

    if (t.getType() == Token.EOF) {
        while (indentStack.peek() > 0) {
            indentStack.pop();
            pending.offer(mk(NEWLINE, "\n"));
            pending.offer(mk(KodekParser.DEDENT, "DEDENT"));
        }
        if (!pending.isEmpty()) { pending.offer(t); return pending.poll(); }
    }

    return t;
}

private Token mk(int type, String text) {
    CommonToken t = new CommonToken(type, text);
    t.setLine(getLine());
    t.setCharPositionInLine(getCharPositionInLine());
    return t;
}

}

// ── Reguły parsera ────────────────────────────────────────────

// NEWLINE na poziomie programu i bloku pozwala na puste linie / linie z komentarzem
program
    : (NEWLINE | statement)* EOF
    ;

// Instrukcje proste kończą się NEWLINE; blokowe nie (block już zawiera DEDENT)
statement
    : simpleStmt NEWLINE
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

// Typy
typeName
    : 'liczba'
    | 'ułamek'
    | 'tekst'
    | 'logiczny'
    | 'lista'
    ;

// Deklaracja i przypisanie
varDecl
    : 'zmienna' typeName ID ('=' expression)?
    ;

assignment
    : ID '=' expression
    | ID '[' expression ']' '=' expression
    ;

// Wyrażenia (kolejność = priorytet operatorów)
expression : logicalOr ;

logicalOr  : logicalAnd ('lub'  logicalAnd)* ;
logicalAnd : negation   ('oraz' negation)*   ;

negation
    : 'nie' negation
    | comparison
    ;

comparison : arithmetic (compOp arithmetic)* ;

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

// Sterowanie
ifStmt
    : 'jeśli' '(' expression ')' ':' block
      ('inaczej' 'jeśli' '(' expression ')' ':' block)*
      ('inaczej' ':' block)?
    ;

forLoop
    : 'dla' ID 'od' expression 'do' expression ':' block
    | 'dla' ID 'w' expression ':' block
    ;

whileLoop : 'dopóki' '(' expression ')' ':' block ;

block : NEWLINE INDENT (NEWLINE | statement)+ DEDENT ;

breakStmt    : 'przerwij'  ;
continueStmt : 'kontynuuj' ;

// Funkcje
functionDef
    : 'funkcja' ID '(' paramList? ')' ('zwraca' typeName)? ':' block
    ;

paramList
    : typeName ID (',' typeName ID)*
    ;

functionCall : ID '(' argumentList? ')' ;
argumentList : expression (',' expression)* ;

returnStmt : 'zwróć' expression ;

// Wejście / wyjście
readStmt  : 'czytaj' '(' ID ')' ;
writeStmt : ('pisz' | 'piszln') '(' expression ')' ;

// Pliki
fileStmt
    : 'otwórz'  '(' expression ',' ID ')'
    | 'zamknij' '(' ID ')'
    ;

// ── Reguły leksera ────────────────────────────────────────────

BOOLEAN : 'prawda' | 'fałsz' ;

ID     : LETTER (LETTER | DIGIT | '_')* ;
NUMBER : DIGIT+ ('.' DIGIT+)? ;
STRING : '"' ~["\r\n]* '"' ;

COMMENT : '#' ~[\r\n]* -> skip ;

// NEWLINE musi pochłaniać wiodące spacje kolejnej linii
// — na ich podstawie mierzymy wcięcie
NEWLINE : '\r'? '\n' [ \t]* ;

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
