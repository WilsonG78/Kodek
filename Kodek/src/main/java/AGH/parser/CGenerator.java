package AGH.parser;

import org.antlr.v4.runtime.tree.*;
import org.antlr.v4.runtime.*;
import java.util.*;

/**
 * CGenerator – przechodzi po drzewie parsowania Kodek
 * i generuje poprawny kod w języku C.
 *
 * Naprawione błędy v2:
 *  - definicje funkcji są emitowane PRZED main() (C nie pozwala na zagnieżdżanie funkcji)
 *  - tablica symboli jest warstwowa (scoped) – oddzielne zakresy dla bloków/funkcji
 *  - czytaj(tekst) generuje bufor char[256] – bez segfaultu
 *  - deklaracja lista nie produkuje podwójnego średnika
 *  - rozmiar listy jest pobierany z AST, nie przez split(",")
 *  - pisz/piszln używa inferencji typów (typeOf) zamiast guessType
 *  - góra() / dół() zaimplementowane jako helper w C
 *  - dodaj(lista, elem) generuje listName[listName_len++] = elem
 *  - dla-w (for-each) używa scoped zmiennej iteratora
 *  - srand() wywoływane raz na początku main()
 *
 * Naprawione błędy v3:
 *  - break/continue dozwolone tylko wewnątrz pętli (loopBlock/loopStatement)
 *  - dodano visitLoopBlock, visitLoopStatement, visitLoopSimpleStmt,
 *    visitLoopBlockStmt, visitLoopIfStmt
 *  - forLoop i whileLoop używają loopBlock zamiast block
 */
public class CGenerator extends KodekBaseVisitor<String> {

    // =========================================================
    //  TABLICA SYMBOLI – ZAKRESY (SCOPE STACK)
    // =========================================================

    /** Stos zakresów: peek() = bieżący zakres (najgłębszy). */
    private final Deque<Map<String, String>> scopeStack = new ArrayDeque<>();

    private void pushScope() { scopeStack.push(new HashMap<>()); }
    private void popScope()  { if (!scopeStack.isEmpty()) scopeStack.pop(); }

    /** Deklaruje zmienną w bieżącym (najgłębszym) zakresie. */
    private void declareVar(String name, String type) {
        if (!scopeStack.isEmpty()) scopeStack.peek().put(name, type);
    }

    /** Szuka zmiennej od najgłębszego do globalnego zakresu. */
    private String lookupVar(String name) {
        for (Map<String, String> scope : scopeStack) {
            if (scope.containsKey(name)) return scope.get(name);
        }
        return null;
    }

    /**
     * Zwraca poprawne referencje C do zmiennej listy:
     *  - lokalna KodekLista  → "&name"
     *  - parametr KodekLista* ("lista_ptr") → "name"  (już jest wskaźnikiem)
     */
    private String listRef(String varName) {
        return isListPtr(lookupVar(varName)) ? varName : "&" + varName;
    }

    /**
     * Pobiera listRef gdy znamy tylko ExpressionContext.
     * Jeśli wyrażenie to proste ID – używa listRef(id), inaczej zakłada lokalną wartość.
     */
    private String listRefFromExpr(KodekParser.ExpressionContext expr) {
        String id = extractSimpleId(expr);
        return id != null ? listRef(id) : "&" + visit(expr);
    }

    /** Kanoniczny typ listy przekazanej jako argument (z tablicy symboli lub inferencji). */
    private String listTypeOfArg(KodekParser.ExpressionContext expr) {
        String id = extractSimpleId(expr);
        String t  = (id != null) ? lookupVar(id) : null;
        return (t != null) ? t : typeOf(expr);
    }

    // =========================================================
    //  TYPY ZWRACANE PRZEZ FUNKCJE (KODEK → C)
    // =========================================================

    /** Typ zwracany przez każdą zdefiniowaną funkcję (typ Kodek). */
    private final Map<String, String> functionReturnTypes = new HashMap<>();

    // =========================================================
    //  WCIĘCIA
    // =========================================================

    private int indentLevel = 0;
    private String indent() { return "    ".repeat(indentLevel); }

    // =========================================================
    //  MAPOWANIE TYPÓW
    // =========================================================

    private String toCType(String kodekType) {
        if (isList(kodekType)) return listStruct(listElem(kodekType));
        switch (kodekType) {
            case "liczba":   return "int";
            case "ułamek":   return "double";
            case "tekst":    return "char*";      // dla parametrów funkcji
            case "logiczny": return "int";
            case "lista":    return "KodekLista"; // dynamiczny vector (legacy)
            default:         return "int";
        }
    }

    private String printfFormat(String kodekType) {
        switch (kodekType) {
            case "liczba":   return "%d";
            case "ułamek":   return "%f";
            case "tekst":    return "%s";
            case "logiczny": return "%d";
            default:         return "%d";
        }
    }

    // =========================================================
    //  TYPY LIST (element) – kanoniczne kodowanie i mapowanie na C
    //
    //  Kanoniczny typ Kodek:
    //    skalar : "liczba" | "ułamek" | "tekst" | "logiczny"
    //    lista  : "lista:<element>"          (zmienna/wartość lokalna)
    //             "lista_ptr:<element>"      (parametr funkcji – wskaźnik)
    //
    //  Trzy "kubełki" implementacji C (liczba i logiczny dzielą int):
    //    int    → KodekLista       / lista_*      (LEGACY – zgodność wsteczna)
    //    double → KodekLista_u      / lista_u_*
    //    str    → KodekLista_t      / lista_t_*
    // =========================================================

    private static boolean isList(String t)    { return t != null && (t.startsWith("lista:") || t.startsWith("lista_ptr:")); }
    private static boolean isListPtr(String t) { return t != null && t.startsWith("lista_ptr:"); }

    /** Typ elementu listy z kanonicznego typu (domyślnie "liczba"). */
    private static String listElem(String t) {
        if (t == null) return "liczba";
        int i = t.indexOf(':');
        return (i >= 0) ? t.substring(i + 1) : "liczba";
    }

    /** Kubełek implementacji C dla typu elementu: "int" | "double" | "str". */
    private static String listBucket(String elem) {
        if ("ułamek".equals(elem)) return "double";
        if ("tekst".equals(elem))  return "str";
        return "int"; // liczba, logiczny
    }

    /** Nazwa struktury C dla listy danego typu elementu. */
    private static String listStruct(String elem) {
        switch (listBucket(elem)) {
            case "double": return "KodekLista_u";
            case "str":    return "KodekLista_t";
            default:       return "KodekLista";   // legacy (liczba/logiczny)
        }
    }

    /** Nazwa funkcji C (init/dodaj/get/set/len) dla listy danego typu elementu. */
    private static String listFn(String elem, String op) {
        switch (listBucket(elem)) {
            case "double": return "lista_u_" + op;
            case "str":    return "lista_t_" + op;
            default:       return "lista_" + op;  // legacy
        }
    }

    /** Kanoniczna reprezentacja typu Kodek z węzła typeName. */
    private static String kodekType(KodekParser.TypeNameContext ctx) {
        if (ctx.getChildCount() > 0 && "lista".equals(ctx.getChild(0).getText())) {
            String elem = (ctx.scalarType() != null) ? ctx.scalarType().getText() : "liczba";
            return "lista:" + elem;
        }
        return ctx.scalarType().getText();
    }

    // =========================================================
    //  INFERENCJA TYPÓW WYRAŻEŃ
    // =========================================================

    private String typeOf(KodekParser.ExpressionContext ctx) {
        return typeOfLogicalOr(ctx.logicalOr());
    }

    private String typeOfLogicalOr(KodekParser.LogicalOrContext ctx) {
        if (ctx.logicalAnd().size() > 1) return "logiczny";
        return typeOfLogicalAnd(ctx.logicalAnd(0));
    }

    private String typeOfLogicalAnd(KodekParser.LogicalAndContext ctx) {
        if (ctx.negation().size() > 1) return "logiczny";
        return typeOfNegation(ctx.negation(0));
    }

    private String typeOfNegation(KodekParser.NegationContext ctx) {
        if (ctx.negation() != null) return "logiczny";  // 'nie' X
        return typeOfComparison(ctx.comparison());
    }

    private String typeOfComparison(KodekParser.ComparisonContext ctx) {
        if (!ctx.compOp().isEmpty()) return "logiczny";
        return typeOfArithmetic(ctx.arithmetic(0));
    }

    private String typeOfArithmetic(KodekParser.ArithmeticContext ctx) {
        for (KodekParser.TermContext t : ctx.term()) {
            if ("ułamek".equals(typeOfTerm(t))) return "ułamek";
        }
        return typeOfTerm(ctx.term(0));
    }

    private String typeOfTerm(KodekParser.TermContext ctx) {
        for (KodekParser.FactorContext f : ctx.factor()) {
            if ("ułamek".equals(typeOfFactor(f))) return "ułamek";
        }
        return typeOfFactor(ctx.factor(0));
    }

    private String typeOfFactor(KodekParser.FactorContext ctx) {
        if (ctx.factor() != null) return "ułamek";  // potęgowanie → pow() → double
        return typeOfBase(ctx.base());
    }

    private String typeOfBase(KodekParser.BaseContext ctx) {
        if (ctx.expression() != null) return typeOf(ctx.expression());
        return typeOfAtom(ctx.atom());
    }

    private String typeOfAtom(KodekParser.AtomContext ctx) {
        if (ctx.NUMBER()       != null) return ctx.NUMBER().getText().contains(".") ? "ułamek" : "liczba";
        if (ctx.STRING()       != null) return "tekst";
        if (ctx.BOOLEAN()      != null) return "logiczny";
        if (ctx.listLiteral()  != null) {
            // typ elementu wnioskowany z pierwszego elementu (pusta lista → liczba)
            java.util.List<KodekParser.ExpressionContext> elems = ctx.listLiteral().expression();
            return "lista:" + (elems.isEmpty() ? "liczba" : typeOf(elems.get(0)));
        }
        if (ctx.functionCall() != null) return typeOfFunctionCall(ctx.functionCall());
        if (ctx.listAccess()   != null) return listElem(lookupVar(ctx.listAccess().ID().getText()));
        if (ctx.ID()           != null) {
            String t = lookupVar(ctx.ID().getText());
            return t != null ? t : "liczba";
        }
        return "liczba";
    }

    private String typeOfFunctionCall(KodekParser.FunctionCallContext ctx) {
        switch (ctx.ID().getText()) {
            case "pierwiastek":         return "ułamek";
            case "zaokrąglij":          return "liczba";
            case "losowa_liczba":       return "liczba";
            case "długość":             return "liczba";
            case "rozmiar":             return "liczba";
            case "góra": case "dół":    return "tekst";
            default:
                String r = functionReturnTypes.get(ctx.ID().getText());
                return r != null ? r : "liczba";
        }
    }

    // =========================================================
    //  PUNKT WEJŚCIA
    // =========================================================

    /**
     * Generuje pełny plik .c z drzewa parsowania.
     * Dwie fazy:
     *   1. Definicje funkcji – emitowane PRZED int main()
     *   2. Reszta instrukcji – wewnątrz int main()
     */
    public String generate(org.antlr.v4.runtime.tree.ParseTree tree) {
        pushScope();  // zakres globalny

        KodekParser.ProgramContext programCtx = (KodekParser.ProgramContext) tree;

        // Faza 0: wstępne skanowanie – rejestruj typy zwracane przez funkcje
        for (KodekParser.StatementContext stmt : programCtx.statement()) {
            KodekParser.FunctionDefContext fctx = extractFunctionDef(stmt);
            if (fctx != null && fctx.typeName() != null) {
                functionReturnTypes.put(fctx.ID().getText(), kodekType(fctx.typeName()));
            }
        }

        StringBuilder sb = new StringBuilder();

        // Nagłówki
        sb.append("#include <stdio.h>\n");
        sb.append("#include <stdlib.h>\n");
        sb.append("#include <string.h>\n");
        sb.append("#include <math.h>\n");
        sb.append("#include <ctype.h>\n");
        sb.append("#include <time.h>\n");
        sb.append("\n");

        // Funkcje pomocnicze Kodek (góra, dół, itp.)
        sb.append(generateHelpers());
        sb.append("\n");

        // Faza 1: definicje funkcji na poziomie pliku (przed main)
        for (KodekParser.StatementContext stmt : programCtx.statement()) {
            KodekParser.FunctionDefContext fctx = extractFunctionDef(stmt);
            if (fctx != null) {
                indentLevel = 0;
                sb.append(visitFunctionDef(fctx));
                sb.append("\n");
            }
        }

        // Faza 2: main()
        sb.append("int main() {\n");
        indentLevel = 1;
        sb.append(indent()).append("srand((unsigned int)time(NULL));\n");

        for (KodekParser.StatementContext stmt : programCtx.statement()) {
            if (extractFunctionDef(stmt) == null) {
                sb.append(visit(stmt));
            }
        }

        indentLevel = 0;
        sb.append("    return 0;\n");
        sb.append("}\n");

        popScope();
        return sb.toString();
    }

    /** Wyciąga FunctionDefContext ze Statement, lub null jeśli to nie definicja funkcji. */
    private KodekParser.FunctionDefContext extractFunctionDef(KodekParser.StatementContext stmt) {
        if (stmt.blockStmt() != null && stmt.blockStmt().functionDef() != null) {
            return stmt.blockStmt().functionDef();
        }
        return null;
    }

    /** Generuje pomocnicze funkcje C dołączane przed main(). */
    private String generateHelpers() {
        return "/* ===== KodekLista – dynamiczna lista (vector z C++) ===== */\n"
                + "typedef struct { int* data; int len; int cap; } KodekLista;\n"
                + "static inline void lista_init(KodekLista* l) {\n"
                + "    l->data = NULL; l->len = 0; l->cap = 0;\n"
                + "}\n"
                + "static inline void lista_dodaj(KodekLista* l, int val) {\n"
                + "    if (l->len >= l->cap) {\n"
                + "        l->cap = l->cap == 0 ? 8 : l->cap * 2;\n"
                + "        l->data = (int*)realloc(l->data, l->cap * sizeof(int));\n"
                + "    }\n"
                + "    l->data[l->len++] = val;\n"
                + "}\n"
                + "static inline int lista_get(KodekLista* l, int i) { return l->data[i]; }\n"
                + "static inline void lista_set(KodekLista* l, int i, int val) { l->data[i] = val; }\n"
                + "static inline int lista_len(KodekLista* l) { return l->len; }\n"
                + "\n"
                + "/* ===== KodekLista_u – lista ułamków (double) ===== */\n"
                + "typedef struct { double* data; int len; int cap; } KodekLista_u;\n"
                + "static inline void lista_u_init(KodekLista_u* l) { l->data = NULL; l->len = 0; l->cap = 0; }\n"
                + "static inline void lista_u_dodaj(KodekLista_u* l, double val) {\n"
                + "    if (l->len >= l->cap) {\n"
                + "        l->cap = l->cap == 0 ? 8 : l->cap * 2;\n"
                + "        l->data = (double*)realloc(l->data, l->cap * sizeof(double));\n"
                + "    }\n"
                + "    l->data[l->len++] = val;\n"
                + "}\n"
                + "static inline double lista_u_get(KodekLista_u* l, int i) { return l->data[i]; }\n"
                + "static inline void lista_u_set(KodekLista_u* l, int i, double val) { l->data[i] = val; }\n"
                + "static inline int lista_u_len(KodekLista_u* l) { return l->len; }\n"
                + "\n"
                + "/* ===== KodekLista_t – lista tekstów (char*, kopiowane przez strdup) ===== */\n"
                + "typedef struct { char** data; int len; int cap; } KodekLista_t;\n"
                + "static inline void lista_t_init(KodekLista_t* l) { l->data = NULL; l->len = 0; l->cap = 0; }\n"
                + "static inline void lista_t_dodaj(KodekLista_t* l, const char* val) {\n"
                + "    if (l->len >= l->cap) {\n"
                + "        l->cap = l->cap == 0 ? 8 : l->cap * 2;\n"
                + "        l->data = (char**)realloc(l->data, l->cap * sizeof(char*));\n"
                + "    }\n"
                + "    l->data[l->len++] = strdup(val);\n"
                + "}\n"
                + "static inline char* lista_t_get(KodekLista_t* l, int i) { return l->data[i]; }\n"
                + "static inline void lista_t_set(KodekLista_t* l, int i, const char* val) { l->data[i] = strdup(val); }\n"
                + "static inline int lista_t_len(KodekLista_t* l) { return l->len; }\n"
                + "\n"
                + "/* ===== Funkcje pomocnicze tekstu ===== */\n"
                + "char* _kodek_gora(const char* s) {\n"
                + "    static char r[1024]; int i;\n"
                + "    for (i = 0; s[i] && i < 1023; i++) r[i] = toupper((unsigned char)s[i]);\n"
                + "    r[i] = '\\0'; return r;\n"
                + "}\n"
                + "char* _kodek_dol(const char* s) {\n"
                + "    static char r[1024]; int i;\n"
                + "    for (i = 0; s[i] && i < 1023; i++) r[i] = tolower((unsigned char)s[i]);\n"
                + "    r[i] = '\\0'; return r;\n"
                + "}\n";
    }

    // =========================================================
    //  PROGRAM
    // =========================================================

    @Override
    public String visitProgram(KodekParser.ProgramContext ctx) {
        // Używany przy bezpośrednim wywołaniu visitora (np. testy)
        StringBuilder sb = new StringBuilder();
        for (KodekParser.StatementContext stmt : ctx.statement()) {
            sb.append(visit(stmt));
        }
        return sb.toString();
    }

    // =========================================================
    //  STATEMENT
    // =========================================================

    @Override
    public String visitStatement(KodekParser.StatementContext ctx) {
        return visit(ctx.getChild(0));
    }

    @Override
    public String visitSimpleStmt(KodekParser.SimpleStmtContext ctx) {
        String inner = visit(ctx.getChild(0));
        // Deklaracje listy zwracają gotowy wieloliniowy kod (kończą się \n)
        if (inner.endsWith("\n")) return inner;
        return indent() + inner + ";\n";
    }

    @Override
    public String visitBlockStmt(KodekParser.BlockStmtContext ctx) {
        if (ctx.functionDef() != null) {
            // Zagnieżdżone definicje funkcji są niedozwolone w C
            return indent() + "/* BŁĄD: definicja funkcji wewnątrz bloku jest niedozwolona w C */\n";
        }
        return visit(ctx.getChild(0));
    }

    // =========================================================
    //  LOOP STATEMENT (wewnątrz pętli – dozwolone break/continue)
    // =========================================================

    @Override
    public String visitLoopStatement(KodekParser.LoopStatementContext ctx) {
        return visit(ctx.getChild(0));
    }

    @Override
    public String visitLoopSimpleStmt(KodekParser.LoopSimpleStmtContext ctx) {
        String inner = visit(ctx.getChild(0));
        // Deklaracje listy zwracają gotowy wieloliniowy kod (kończą się \n)
        if (inner.endsWith("\n")) return inner;
        return indent() + inner + ";\n";
    }

    @Override
    public String visitLoopBlockStmt(KodekParser.LoopBlockStmtContext ctx) {
        if (ctx.functionDef() != null) {
            // Zagnieżdżone definicje funkcji są niedozwolone w C
            return indent() + "/* BŁĄD: definicja funkcji wewnątrz bloku jest niedozwolona w C */\n";
        }
        return visit(ctx.getChild(0));
    }

    // =========================================================
    //  DEKLARACJA ZMIENNEJ
    // =========================================================

    @Override
    public String visitVarDecl(KodekParser.VarDeclContext ctx) {
        String type = kodekType(ctx.typeName());
        String name = ctx.ID().getText();
        declareVar(name, type);

        // --- tekst: char name[256] zamiast char* (bezpieczne) ---
        if (type.equals("tekst")) {
            if (ctx.expression() != null) {
                String val = visit(ctx.expression());
                return "char " + name + "[256] = " + val;
            }
            return "char " + name + "[256] = \"\"";
        }

        // --- lista <typ>: KodekLista / KodekLista_u / KodekLista_t (dynamiczny vector) ---
        if (isList(type)) {
            String elem   = listElem(type);
            String struct = listStruct(elem);
            // Generuj gotowy wieloliniowy kod; visitSimpleStmt wykryje \n i nie doda ";"
            StringBuilder sb = new StringBuilder();
            sb.append(indent()).append(struct).append(" ").append(name)
                    .append("; ").append(listFn(elem, "init")).append("(&").append(name).append(");\n");
            if (ctx.expression() != null) {
                KodekParser.ListLiteralContext listLit = extractListLiteral(ctx.expression());
                if (listLit != null) {
                    for (KodekParser.ExpressionContext el : listLit.expression()) {
                        sb.append(indent()).append(listFn(elem, "dodaj")).append("(&").append(name)
                                .append(", ").append(visit(el)).append(");\n");
                    }
                }
            }
            return sb.toString();
        }

        String cType = toCType(type);
        if (ctx.expression() != null) {
            return cType + " " + name + " = " + visit(ctx.expression());
        }
        return cType + " " + name;
    }

    /**
     * Próbuje wyciągnąć ListLiteralContext z wyrażenia (gdy expression jest prostym literałem listy).
     * Przechodzi przez łańcuch: expression → logicalOr → ... → atom → listLiteral
     */
    private KodekParser.ListLiteralContext extractListLiteral(KodekParser.ExpressionContext expr) {
        try {
            KodekParser.AtomContext atom = expr
                    .logicalOr().logicalAnd(0)
                    .negation(0).comparison()
                    .arithmetic(0).term(0)
                    .factor(0).base().atom();
            return (atom != null) ? atom.listLiteral() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Próbuje wyciągnąć prostą nazwę zmiennej z wyrażenia (gdy expression to samo ID).
     */
    private String extractSimpleId(KodekParser.ExpressionContext expr) {
        try {
            KodekParser.AtomContext atom = expr
                    .logicalOr().logicalAnd(0)
                    .negation(0).comparison()
                    .arithmetic(0).term(0)
                    .factor(0).base().atom();
            if (atom != null && atom.ID() != null
                    && atom.functionCall() == null && atom.listAccess() == null) {
                return atom.ID().getText();
            }
        } catch (Exception e) { /* nie jest prostym ID */ }
        return null;
    }

    // =========================================================
    //  PRZYPISANIE
    // =========================================================

    @Override
    public String visitAssignment(KodekParser.AssignmentContext ctx) {
        String name = ctx.ID().getText();
        String varType = lookupVar(name);

        if (ctx.expression().size() == 2) {
            // lista[i] = val  →  lista_set(&name, i, val)  lub  lista_set(name, i, val) gdy ptr
            String elem  = listElem(varType);
            String index = visit(ctx.expression(0));
            String val   = visit(ctx.expression(1));
            return listFn(elem, "set") + "(" + listRef(name) + ", " + index + ", " + val + ")";
        } else {
            String val = visit(ctx.expression(0));
            // tekst: przypisanie przez strcpy (char[] nie może być przypisane przez =)
            if ("tekst".equals(varType)) {
                return "strcpy(" + name + ", " + val + ")";
            }
            return name + " = " + val;
        }
    }

    // =========================================================
    //  WYRAŻENIA
    // =========================================================

    @Override
    public String visitExpression(KodekParser.ExpressionContext ctx) {
        return visit(ctx.logicalOr());
    }

    @Override
    public String visitLogicalOr(KodekParser.LogicalOrContext ctx) {
        if (ctx.logicalAnd().size() == 1) return visit(ctx.logicalAnd(0));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ctx.logicalAnd().size(); i++) {
            if (i > 0) sb.append(" || ");
            sb.append(visit(ctx.logicalAnd(i)));
        }
        return sb.toString();
    }

    @Override
    public String visitLogicalAnd(KodekParser.LogicalAndContext ctx) {
        if (ctx.negation().size() == 1) return visit(ctx.negation(0));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ctx.negation().size(); i++) {
            if (i > 0) sb.append(" && ");
            sb.append(visit(ctx.negation(i)));
        }
        return sb.toString();
    }

    @Override
    public String visitNegation(KodekParser.NegationContext ctx) {
        if (ctx.getChild(0).getText().equals("nie")) {
            return "!(" + visit(ctx.negation()) + ")";
        }
        return visit(ctx.comparison());
    }

    @Override
    public String visitComparison(KodekParser.ComparisonContext ctx) {
        if (ctx.arithmetic().size() == 1) return visit(ctx.arithmetic(0));
        StringBuilder sb = new StringBuilder(visit(ctx.arithmetic(0)));
        for (int i = 0; i < ctx.compOp().size(); i++) {
            sb.append(" ").append(ctx.compOp(i).getText()).append(" ");
            sb.append(visit(ctx.arithmetic(i + 1)));
        }
        return sb.toString();
    }

    @Override
    public String visitArithmetic(KodekParser.ArithmeticContext ctx) {
        StringBuilder sb = new StringBuilder(visit(ctx.term(0)));
        for (int i = 1; i < ctx.term().size(); i++) {
            String op = ctx.getChild(2 * i - 1).getText();
            sb.append(" ").append(op).append(" ").append(visit(ctx.term(i)));
        }
        return sb.toString();
    }

    @Override
    public String visitTerm(KodekParser.TermContext ctx) {
        StringBuilder sb = new StringBuilder(visit(ctx.factor(0)));
        for (int i = 1; i < ctx.factor().size(); i++) {
            String op = ctx.getChild(2 * i - 1).getText();
            sb.append(" ").append(op).append(" ").append(visit(ctx.factor(i)));
        }
        return sb.toString();
    }

    @Override
    public String visitFactor(KodekParser.FactorContext ctx) {
        if (ctx.factor() != null) {
            return "pow(" + visit(ctx.base()) + ", " + visit(ctx.factor()) + ")";
        }
        return visit(ctx.base());
    }

    @Override
    public String visitBase(KodekParser.BaseContext ctx) {
        if (ctx.expression() != null) {
            return "(" + visit(ctx.expression()) + ")";
        }
        return visit(ctx.atom());
    }

    @Override
    public String visitAtom(KodekParser.AtomContext ctx) {
        if (ctx.NUMBER()       != null) return ctx.NUMBER().getText();
        if (ctx.STRING()       != null) return ctx.STRING().getText();
        if (ctx.BOOLEAN()      != null) return ctx.BOOLEAN().getText().equals("prawda") ? "1" : "0";
        if (ctx.listLiteral()  != null) return visit(ctx.listLiteral());
        if (ctx.functionCall() != null) return visit(ctx.functionCall());
        if (ctx.listAccess()   != null) return visit(ctx.listAccess());
        if (ctx.ID()           != null) return ctx.ID().getText();
        return "";
    }

    // =========================================================
    //  LISTA
    // =========================================================

    @Override
    public String visitListLiteral(KodekParser.ListLiteralContext ctx) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < ctx.expression().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(visit(ctx.expression(i)));
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public String visitListAccess(KodekParser.ListAccessContext ctx) {
        // name[i]  →  lista_get(&name, i)  lub  lista_get(name, i) gdy ptr
        String varName = ctx.ID().getText();
        String elem    = listElem(lookupVar(varName));
        return listFn(elem, "get") + "(" + listRef(varName) + ", " + visit(ctx.expression()) + ")";
    }

    // =========================================================
    //  WARUNEK (condition – używany w if/while)
    // =========================================================

    @Override
    public String visitCondition(KodekParser.ConditionContext ctx) {
        if (ctx.condAnd().size() == 1) return visit(ctx.condAnd(0));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ctx.condAnd().size(); i++) {
            if (i > 0) sb.append(" || ");
            sb.append(visit(ctx.condAnd(i)));
        }
        return sb.toString();
    }

    @Override
    public String visitCondAnd(KodekParser.CondAndContext ctx) {
        if (ctx.condNeg().size() == 1) return visit(ctx.condNeg(0));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ctx.condNeg().size(); i++) {
            if (i > 0) sb.append(" && ");
            sb.append(visit(ctx.condNeg(i)));
        }
        return sb.toString();
    }

    @Override
    public String visitCondNeg(KodekParser.CondNegContext ctx) {
        String first = ctx.getChild(0).getText();
        if (first.equals("nie"))            return "!(" + visit(ctx.condNeg()) + ")";
        if (ctx.BOOLEAN()          != null) return ctx.BOOLEAN().getText().equals("prawda") ? "1" : "0";
        if (ctx.ID()               != null) return ctx.ID().getText();
        if (ctx.condition()        != null) return "(" + visit(ctx.condition()) + ")";
        if (ctx.strictComparison() != null) return visit(ctx.strictComparison());
        return "";
    }

    @Override
    public String visitStrictComparison(KodekParser.StrictComparisonContext ctx) {
        return visit(ctx.arithmetic(0))
                + " " + ctx.compOp().getText() + " "
                + visit(ctx.arithmetic(1));
    }

    // =========================================================
    //  IF / ELSE IF / ELSE
    // =========================================================

    @Override
    public String visitIfStmt(KodekParser.IfStmtContext ctx) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ctx.condition().size(); i++) {
            String keyword = (i == 0) ? "if" : " else if";
            sb.append(indent()).append(keyword)
                    .append(" (").append(visit(ctx.condition(i))).append(") ");
            sb.append(visitBlock(ctx.block(i)));
        }
        // else – gdy jest więcej bloków niż warunków
        if (ctx.block().size() > ctx.condition().size()) {
            sb.append(indent()).append(" else ");
            sb.append(visitBlock(ctx.block(ctx.block().size() - 1)));
        }
        return sb.toString();
    }

    /**
     * Wariant ifStmt używany wewnątrz pętli – bloki to loopBlock,
     * więc break/continue wewnątrz są dozwolone.
     */
    @Override
    public String visitLoopIfStmt(KodekParser.LoopIfStmtContext ctx) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ctx.condition().size(); i++) {
            String keyword = (i == 0) ? "if" : " else if";
            sb.append(indent()).append(keyword)
                    .append(" (").append(visit(ctx.condition(i))).append(") ");
            sb.append(visitLoopBlock(ctx.loopBlock(i)));
        }
        // else – gdy jest więcej bloków niż warunków
        if (ctx.loopBlock().size() > ctx.condition().size()) {
            sb.append(indent()).append(" else ");
            sb.append(visitLoopBlock(ctx.loopBlock(ctx.loopBlock().size() - 1)));
        }
        return sb.toString();
    }

    // =========================================================
    //  PĘTLE
    // =========================================================

    @Override
    public String visitForLoop(KodekParser.ForLoopContext ctx) {
        StringBuilder sb = new StringBuilder();
        String varName = ctx.ID().getText();

        if (ctx.expression().size() == 2) {
            // === dla k od 1 do 10 { ... } ===
            String from = visit(ctx.expression(0));
            String to   = visit(ctx.expression(1));

            pushScope();
            declareVar(varName, "liczba");

            sb.append(indent())
                    .append("for (int ").append(varName).append(" = ").append(from)
                    .append("; ").append(varName).append(" <= ").append(to)
                    .append("; ").append(varName).append("++) ");
            sb.append(visitLoopBlock(ctx.loopBlock()));

            popScope();

        } else {
            // === dla ocena w oceny { ... } ===
            // Pobierz nazwę zmiennej listy jeśli wyrażenie jest prostym ID
            String listName = extractSimpleId(ctx.expression(0));
            // Wyznacz poprawną referencję C (&name dla lokalnej, name dla wskaźnika)
            String listCRef = (listName != null) ? listRef(listName)
                    : "&" + visit(ctx.expression(0));

            // Typ elementu listy (z deklaracji zmiennej) – iterator dostaje ten typ
            String listType = (listName != null) ? lookupVar(listName) : typeOf(ctx.expression(0));
            String elem     = listElem(listType);
            String elemCType = toCType(elem);

            pushScope();
            declareVar(varName, elem);

            sb.append(indent())
                    .append("for (int _i = 0; _i < ").append(listFn(elem, "len"))
                    .append("(").append(listCRef).append("); _i++) {\n");
            indentLevel++;

            sb.append(indent()).append(elemCType).append(" ").append(varName)
                    .append(" = ").append(listFn(elem, "get"))
                    .append("(").append(listCRef).append(", _i);\n");

            // Odwiedź instrukcje bloku bezpośrednio (zakres iteratora jest już otwarty)
            for (KodekParser.LoopStatementContext stmt : ctx.loopBlock().loopStatement()) {
                sb.append(visit(stmt));
            }

            indentLevel--;
            sb.append(indent()).append("}\n");

            popScope();
        }

        return sb.toString();
    }

    @Override
    public String visitWhileLoop(KodekParser.WhileLoopContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent())
                .append("while (").append(visit(ctx.condition())).append(") ");
        sb.append(visitLoopBlock(ctx.loopBlock()));
        return sb.toString();
    }

    // =========================================================
    //  BLOK  { instrukcje }        (poza pętlą – bez break/continue)
    //  LOOP BLOK  { instrukcje }   (wewnątrz pętli – z break/continue)
    // =========================================================

    @Override
    public String visitBlock(KodekParser.BlockContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        indentLevel++;
        pushScope();
        for (KodekParser.StatementContext stmt : ctx.statement()) {
            sb.append(visit(stmt));
        }
        popScope();
        indentLevel--;
        sb.append(indent()).append("}\n");
        return sb.toString();
    }

    /**
     * Blok wewnątrz pętli – iteruje po loopStatement zamiast statement,
     * dzięki czemu break/continue są legalną instrukcją w tym kontekście.
     */
    @Override
    public String visitLoopBlock(KodekParser.LoopBlockContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        indentLevel++;
        pushScope();
        for (KodekParser.LoopStatementContext stmt : ctx.loopStatement()) {
            sb.append(visit(stmt));
        }
        popScope();
        indentLevel--;
        sb.append(indent()).append("}\n");
        return sb.toString();
    }

    // =========================================================
    //  FUNKCJE
    // =========================================================

    @Override
    public String visitFunctionDef(KodekParser.FunctionDefContext ctx) {
        String name = ctx.ID().getText();
        String kodekReturnType = (ctx.typeName() != null) ? kodekType(ctx.typeName()) : "void";
        functionReturnTypes.put(name, kodekReturnType);

        // Mapuj typ zwracany
        String cReturnType;
        if (kodekReturnType.equals("void")) {
            cReturnType = "void";
        } else if (kodekReturnType.equals("tekst")) {
            cReturnType = "char*";
        } else {
            cReturnType = toCType(kodekReturnType);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(cReturnType).append(" ").append(name).append("(");

        pushScope();  // zakres parametrów + ciała funkcji
        if (ctx.paramList() != null) {
            sb.append(visit(ctx.paramList()));
        }
        sb.append(") {\n");
        indentLevel++;

        // Odwiedź instrukcje bloku bezpośrednio – parametry są już w tym zakresie
        for (KodekParser.StatementContext stmt : ctx.block().statement()) {
            sb.append(visit(stmt));
        }

        indentLevel--;
        sb.append(indent()).append("}\n");
        popScope();

        return sb.toString();
    }

    @Override
    public String visitParamList(KodekParser.ParamListContext ctx) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ctx.typeName().size(); i++) {
            if (i > 0) sb.append(", ");
            String type  = kodekType(ctx.typeName(i));
            String pname = ctx.ID(i).getText();
            // lista param jest <Struct>* – zapamiętujemy jako "lista_ptr:<elem>" by listRef()
            // pominął & (inaczej powstawałby <Struct>** przy lista_get/lista_set)
            if (isList(type)) {
                String elem = listElem(type);
                declareVar(pname, "lista_ptr:" + elem);
                // lista przez wskaźnik – modyfikacje wewnątrz funkcji są widoczne na zewnątrz
                sb.append(listStruct(elem)).append("* ").append(pname);
            } else if (type.equals("tekst")) {
                declareVar(pname, type);
                // tekst przez wskaźnik (char*) – standardowe C
                sb.append("char* ").append(pname);
            } else {
                declareVar(pname, type);
                sb.append(toCType(type)).append(" ").append(pname);
            }
        }
        return sb.toString();
    }

    @Override
    public String visitFunctionCall(KodekParser.FunctionCallContext ctx) {
        String name = ctx.ID().getText();

        // Jeśli użytkownik zdefiniował funkcję o tej nazwie, wywołaj ją bezpośrednio
        // (user-defined functions mają pierwszeństwo nad wbudowanymi)
        if (functionReturnTypes.containsKey(name)) {
            StringBuilder sb = new StringBuilder(name).append("(");
            if (ctx.argumentList() != null) sb.append(visit(ctx.argumentList()));
            sb.append(")");
            return sb.toString();
        }

        switch (name) {
            case "pierwiastek":
                name = "sqrt"; break;
            case "wartość_bezwzględna":
                name = "abs"; break;
            case "zaokrąglij":
                name = "round"; break;
            case "góra":
                name = "_kodek_gora"; break;
            case "dół":
                name = "_kodek_dol"; break;
            case "losowa_liczba":
                if (ctx.argumentList() != null && ctx.argumentList().expression().size() >= 2) {
                    String min = visit(ctx.argumentList().expression(0));
                    String max = visit(ctx.argumentList().expression(1));
                    return "(rand() % (" + max + " - " + min + " + 1) + " + min + ")";
                }
                return "rand()";
            case "długość":
                if (ctx.argumentList() != null) {
                    return "strlen(" + visit(ctx.argumentList().expression(0)) + ")";
                }
                return "0";
            case "rozmiar":
                if (ctx.argumentList() != null) {
                    KodekParser.ExpressionContext arg = ctx.argumentList().expression(0);
                    String ref = listRefFromExpr(arg);
                    String el  = listElem(listTypeOfArg(arg));
                    return listFn(el, "len") + "(" + ref + ")";
                }
                return "0";
            case "dodaj":
                // dodaj(lista, element)  →  lista_dodaj(&lista, element)  lub  lista_dodaj(lista,…) gdy ptr
                if (ctx.argumentList() != null && ctx.argumentList().expression().size() >= 2) {
                    KodekParser.ExpressionContext arg = ctx.argumentList().expression(0);
                    String ref  = listRefFromExpr(arg);
                    String el   = listElem(listTypeOfArg(arg));
                    String elem = visit(ctx.argumentList().expression(1));
                    return listFn(el, "dodaj") + "(" + ref + ", " + elem + ")";
                }
                return "/* dodaj: nieprawidłowe argumenty */";
        }

        StringBuilder sb = new StringBuilder(name).append("(");
        if (ctx.argumentList() != null) {
            sb.append(visit(ctx.argumentList()));
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String visitArgumentList(KodekParser.ArgumentListContext ctx) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ctx.expression().size(); i++) {
            if (i > 0) sb.append(", ");
            KodekParser.ExpressionContext expr = ctx.expression(i);
            String id   = extractSimpleId(expr);
            String type = (id != null) ? lookupVar(id) : null;
            if (isListPtr(type)) {
                // Parametr <Struct>* → już jest wskaźnikiem, przekazujemy as-is
                sb.append(id);
            } else if (isList(type)) {
                // Lokalna lista → funkcja oczekuje <Struct>*, przekazujemy &id
                sb.append("&").append(id);
            } else {
                sb.append(visit(expr));
            }
        }
        return sb.toString();
    }

    @Override
    public String visitReturnStmt(KodekParser.ReturnStmtContext ctx) {
        return "return " + visit(ctx.expression());
    }

    // =========================================================
    //  I/O
    // =========================================================

    @Override
    public String visitWriteStmt(KodekParser.WriteStmtContext ctx) {
        boolean newline = ctx.getChild(0).getText().equals("piszln");
        // Inferencja typu z AST (nie ze stringa) → poprawny format printf
        String type = typeOf(ctx.expression());
        String expr = visit(ctx.expression());
        String fmt  = printfFormat(type) + (newline ? "\\n" : "");
        return "printf(\"" + fmt + "\", " + expr + ")";
    }

    @Override
    public String visitReadStmt(KodekParser.ReadStmtContext ctx) {
        String varName = ctx.ID().getText();
        String type = lookupVar(varName);
        if (type == null) type = "liczba";

        if (type.equals("tekst")) {
            // char[256] – bezpieczny bufor, brak segfaultu
            return "scanf(\"%255s\", " + varName + ")";
        }
        return "scanf(\"" + printfFormat(type) + "\", &" + varName + ")";
    }

    // =========================================================
    //  PLIK
    // =========================================================

    @Override
    public String visitFileStmt(KodekParser.FileStmtContext ctx) {
        if (ctx.getChild(0).getText().equals("otwórz")) {
            String path   = visit(ctx.expression());
            String handle = ctx.ID().getText();
            declareVar(handle, "plik");
            return "FILE *" + handle + " = fopen(" + path + ", \"r\")";
        } else {
            return "fclose(" + ctx.ID().getText() + ")";
        }
    }

    // =========================================================
    //  BREAK / CONTINUE (dozwolone tylko w loopBlock)
    // =========================================================

    @Override
    public String visitBreakStmt(KodekParser.BreakStmtContext ctx) {
        return "break";
    }

    @Override
    public String visitContinueStmt(KodekParser.ContinueStmtContext ctx) {
        return "continue";
    }
}