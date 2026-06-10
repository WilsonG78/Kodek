package AGH.parser;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.util.*;

/**
 * KodekErrorHandler – semantyczna analiza drzewa parsowania Kodek.
 *
 * Uruchamiany PO parsowaniu (faza 2.5), PRZED generowaniem kodu C (faza 3).
 *
 * Wykrywane błędy:
 *  1.  Użycie niezadeklarowanej zmiennej
 *  2.  Ponowna deklaracja zmiennej w tym samym zakresie
 *  3.  Wywołanie niezdefiniowanej funkcji
 *  4.  Zła liczba argumentów przy wywołaniu funkcji
 *  5.  Niezgodność typów przy przypisaniu (liczba ↔ tekst itp.)
 *  6.  Przypisanie do listy bez indeksu gdy oczekiwany jest skalar
 *  7.  Dostęp do elementu tablicy na zmiennej niebędącej listą
 *  8.  Zwracanie wartości w funkcji void / brak zwrotu w funkcji z typem
 *  9.  break / continue poza pętlą  (gramatyka już to blokuje; tu dodatkowe ostrzeżenie)
 * 10.  Użycie operatora arytmetycznego na typie tekst / logiczny
 * 11.  Wywołanie czytaj() / pisz() z nieprawidłowym argumentem
 * 12.  Dzielenie przez zero (tylko dla literałów)
 * 13.  Redeklaracja wbudowanej funkcji (np. 'funkcja pisz(...)')
 * 14.  Zmienna zasłaniająca nazwę funkcji użytkownika
 * 15.  Wynik funkcji void użyty w wyrażeniu (jako argument lub operand)
 * 16.  Puste ciało funkcji nieVoid (brak jakichkolwiek instrukcji)
 * 17.  Ujemny literał jako indeks listy
 * 18.  Użycie operatora arytmetycznego / porównania na typie logiczny
 * 19.  Wywołanie funkcji jako instrukcja gdy funkcja zwraca wartość (ostrzeżenie o ignorowanym wyniku)
 * 20.  Deklaracja zmiennej o nazwie takiej samej jak wbudowana funkcja
 *
 */
public class KodekErrorHandler extends KodekBaseVisitor<Void> {

    // =========================================================
    //  STRUKTURY DANYCH
    // =========================================================

    /** Jeden błąd semantyczny. */
    public static class SemanticError {
        public final int    line;
        public final int    column;
        public final String message;

        SemanticError(int line, int column, String message) {
            this.line    = line;
            this.column  = column;
            this.message = message;
        }

        @Override
        public String toString() {
            return String.format("  [błąd semantyczny] %d:%d — %s", line, column, message);
        }
    }

    /** Definicja funkcji (parametry + typ zwracany). */
    private static class FunctionInfo {
        final String       returnType;   // typ Kodek lub "void"
        final List<String> paramTypes;   // typy kolejnych parametrów

        FunctionInfo(String returnType, List<String> paramTypes) {
            this.returnType = returnType;
            this.paramTypes = paramTypes;
        }
    }

    // =========================================================
    //  STAN ANALIZATORA
    // =========================================================

    private final List<SemanticError>         errors          = new ArrayList<>();
    private final Deque<Map<String, String>>  scopeStack      = new ArrayDeque<>();
    private final Map<String, FunctionInfo>   functions       = new HashMap<>();
    private       int                         loopDepth       = 0;   // zagłębienie pętli
    private       String                      currentFuncName = null; // aktualnie odwiedzana funkcja

    // =========================================================
    //  WBUDOWANE FUNKCJE
    // =========================================================

    private static final Map<String, FunctionInfo> BUILTINS = new HashMap<>();
    static {
        BUILTINS.put("pisz",                new FunctionInfo("void",    List.of("*")));
        BUILTINS.put("piszln",              new FunctionInfo("void",    List.of("*")));
        BUILTINS.put("czytaj",              new FunctionInfo("void",    List.of("*")));
        BUILTINS.put("pierwiastek",         new FunctionInfo("ułamek",  List.of("liczba")));
        BUILTINS.put("wartość_bezwzględna", new FunctionInfo("liczba",  List.of("liczba")));
        BUILTINS.put("zaokrąglij",          new FunctionInfo("liczba",  List.of("ułamek")));
        BUILTINS.put("góra",                new FunctionInfo("tekst",   List.of("tekst")));
        BUILTINS.put("dół",                 new FunctionInfo("tekst",   List.of("tekst")));
        BUILTINS.put("losowa_liczba",       new FunctionInfo("liczba",  List.of("liczba","liczba")));
        BUILTINS.put("długość",             new FunctionInfo("liczba",  List.of("tekst")));
        BUILTINS.put("rozmiar",             new FunctionInfo("liczba",  List.of("lista")));
        // dodaj(lista, element): element jest sprawdzany osobno (musi pasować do typu listy),
        // dlatego drugi parametr jest oznaczony jako dowolny "*"
        BUILTINS.put("dodaj",               new FunctionInfo("void",    List.of("lista","*")));
    }

    // =========================================================
    //  PUBLICZNE API
    // =========================================================

    /**
     * Główna metoda – odwiedź cały program i zbierz błędy.
     * @param tree korzeń drzewa parsowania (ParseTree z parser.program())
     */
    public void check(ParseTree tree) {
        pushScope();
        // Wstępne skanowanie: zarejestruj wszystkie funkcje zanim odwiedzimy ciała
        prescanFunctions(tree);
        visit(tree);
        popScope();
        // Osobny przebieg: dzielenie przez literał 0 w dowolnym kontekście
        checkDivisionByZero(tree);
    }

    public boolean hasErrors() { return !errors.isEmpty(); }

    public List<SemanticError> getErrors() { return Collections.unmodifiableList(errors); }

    public void printErrors(java.io.PrintStream out) {
        for (SemanticError e : errors) out.println(e);
        out.printf("  → Łącznie błędów semantycznych: %d%n", errors.size());
    }

    // =========================================================
    //  PRESKAN FUNKCJI
    // =========================================================

    private void prescanFunctions(ParseTree tree) {
        if (!(tree instanceof KodekParser.ProgramContext)) return;
        KodekParser.ProgramContext prog = (KodekParser.ProgramContext) tree;
        for (KodekParser.StatementContext stmt : prog.statement()) {
            if (stmt.blockStmt() != null && stmt.blockStmt().functionDef() != null) {
                registerFunction(stmt.blockStmt().functionDef());
            }
        }
    }

    private void registerFunction(KodekParser.FunctionDefContext ctx) {
        String name       = ctx.ID().getText();
        String returnType = (ctx.typeName() != null) ? kodekType(ctx.typeName()) : "void";
        List<String> paramTypes = new ArrayList<>();
        if (ctx.paramList() != null) {
            for (KodekParser.TypeNameContext t : ctx.paramList().typeName()) {
                paramTypes.add(kodekType(t));
            }
        }

        // Błąd 13: redeklaracja wbudowanej funkcji
        if (BUILTINS.containsKey(name)) {
            addError(ctx.ID().getSymbol(),
                    "redeklaracja wbudowanej funkcji '" + name + "' jest niedozwolona");
        }

        functions.put(name, new FunctionInfo(returnType, paramTypes));
    }

    // =========================================================
    //  SCOPE HELPERS
    // =========================================================

    private void pushScope() { scopeStack.push(new LinkedHashMap<>()); }
    private void popScope()  { if (!scopeStack.isEmpty()) scopeStack.pop(); }

    private String lookupVar(String name) {
        for (Map<String, String> scope : scopeStack) {
            if (scope.containsKey(name)) return scope.get(name);
        }
        return null;
    }

    // =========================================================
    //  BŁĘDY
    // =========================================================

    private void addError(int line, int col, String msg) {
        errors.add(new SemanticError(line, col, msg));
    }

    private void addError(Token token, String msg) {
        addError(token.getLine(), token.getCharPositionInLine(), msg);
    }

    // =========================================================
    //  PROGRAM
    // =========================================================

    @Override
    public Void visitProgram(KodekParser.ProgramContext ctx) {
        for (KodekParser.StatementContext stmt : ctx.statement()) visit(stmt);
        return null;
    }

    // =========================================================
    //  DEKLARACJA ZMIENNEJ
    // =========================================================

    @Override
    public Void visitVarDecl(KodekParser.VarDeclContext ctx) {
        String type = kodekType(ctx.typeName());
        String name = ctx.ID().getText();
        Token  tok  = ctx.ID().getSymbol();

        // Błąd 22: zmienna o nazwie takiej samej jak wbudowana funkcja
        if (BUILTINS.containsKey(name)) {
            addError(tok,
                    "nazwa zmiennej '" + name + "' koliduje z wbudowaną funkcją o tej samej nazwie");
        }

        // Błąd 14: zmienna zasłaniająca nazwę funkcji użytkownika
        if (functions.containsKey(name)) {
            addError(tok,
                    "zmienna '" + name + "' zasłania zdefiniowaną funkcję o tej samej nazwie");
        }

        // Błąd 2: Sprawdź czy nazwa nie koliduje z istniejącą zmienną w bieżącym zakresie
        if (!scopeStack.isEmpty() && scopeStack.peek().containsKey(name)) {
            addError(tok,
                    "zmienna '" + name + "' jest już zadeklarowana w tym zakresie");
        } else {
            scopeStack.peek().put(name, type);
        }

        // Sprawdź wyrażenie inicjalizujące
        if (ctx.expression() != null) {
            String exprType = inferType(ctx.expression());

            // Błąd 16: wynik void użyty jako wartość inicjalizująca
            checkVoidUsedAsValue(ctx.expression(), tok);

            if (!typesCompatible(type, exprType)) {
                addError(tok,
                        "niezgodność typów: zmienna '" + name + "' jest typu '" + pretty(type)
                                + "', ale wyrażenie ma typ '" + pretty(exprType) + "'");
            }
            visit(ctx.expression());
        }
        return null;
    }

    // =========================================================
    //  PRZYPISANIE
    // =========================================================

    @Override
    public Void visitAssignment(KodekParser.AssignmentContext ctx) {
        String name = ctx.ID().getText();
        Token  tok  = ctx.ID().getSymbol();
        String varType = lookupVar(name);

        if (varType == null) {
            addError(tok, "użycie niezadeklarowanej zmiennej '" + name + "'");
        } else if (ctx.expression().size() == 2) {
            // lista[i] = val

            // Błąd 18: ujemny literał jako indeks
            checkNegativeIndex(ctx.expression(0), tok);

            if (!isList(varType)) {
                addError(tok,
                        "indeksowanie tablicy: '" + name + "' nie jest listą (typ: '" + pretty(varType) + "')");
            } else {
                String elem = listElem(varType);
                String valType = inferType(ctx.expression(1));
                if (!typesCompatible(elem, valType)) {
                    addError(tok,
                            "niezgodność typów: lista '" + name + "' przechowuje '" + pretty(elem)
                                    + "', a przypisywana wartość ma typ '" + pretty(valType) + "'");
                }
            }
            visit(ctx.expression(0));
            visit(ctx.expression(1));
        } else {
            // zwykłe przypisanie
            String exprType = inferType(ctx.expression(0));

            // Błąd 16: wynik void użyty jako wartość
            checkVoidUsedAsValue(ctx.expression(0), tok);

            if (!typesCompatible(varType, exprType)) {
                addError(tok,
                        "niezgodność typów przy przypisaniu do '" + name + "': "
                                + "oczekiwano '" + pretty(varType) + "', dostano '" + pretty(exprType) + "'");
            }
            visit(ctx.expression(0));
        }
        return null;
    }

    // =========================================================
    //  DOSTĘP DO LISTY
    // =========================================================

    @Override
    public Void visitListAccess(KodekParser.ListAccessContext ctx) {
        String name    = ctx.ID().getText();
        Token  tok     = ctx.ID().getSymbol();
        String varType = lookupVar(name);

        if (varType == null) {
            addError(tok, "użycie niezadeklarowanej zmiennej '" + name + "'");
        } else if (!isList(varType)) {
            addError(tok,
                    "indeksowanie: '" + name + "' nie jest listą (typ: '" + pretty(varType) + "')");
        }

        // Błąd 18: ujemny literał jako indeks
        checkNegativeIndex(ctx.expression(), tok);

        visit(ctx.expression());
        return null;
    }

    // =========================================================
    //  WYWOŁANIE FUNKCJI
    // =========================================================

    @Override
    public Void visitFunctionCall(KodekParser.FunctionCallContext ctx) {
        String name = ctx.ID().getText();
        Token  tok  = ctx.ID().getSymbol();

        FunctionInfo info = functions.get(name);
        if (info == null) info = BUILTINS.get(name);

        if (info == null) {
            addError(tok, "wywołanie niezdefiniowanej funkcji '" + name + "'");
        } else {
            // Sprawdź liczbę argumentów (pomijaj dla funkcji z parametrem "*" – dowolna liczba)
            int expected = info.paramTypes.size();
            int given    = (ctx.argumentList() != null)
                    ? ctx.argumentList().expression().size() : 0;
            boolean wildcard = (expected == 1 && "*".equals(info.paramTypes.get(0)));

            if (!wildcard && given != expected) {
                addError(tok,
                        "funkcja '" + name + "' oczekuje " + expected
                                + " argument(ów), podano " + given);
            }

            // Sprawdź typy argumentów (tam gdzie znamy oczekiwany typ)
            if (!wildcard && ctx.argumentList() != null) {
                List<KodekParser.ExpressionContext> args = ctx.argumentList().expression();
                for (int i = 0; i < Math.min(given, expected); i++) {
                    String expectedType = info.paramTypes.get(i);
                    String actualType   = inferType(args.get(i));

                    // Błąd 16: wynik void użyty jako argument
                    checkVoidUsedAsValue(args.get(i), tok);

                    if (!"*".equals(expectedType) && !typesCompatible(expectedType, actualType)) {
                        addError(tok,
                                "argument " + (i + 1) + " funkcji '" + name
                                        + "': oczekiwano '" + pretty(expectedType)
                                        + "', podano '" + pretty(actualType) + "'");
                    }
                }
            }
        }

        // Odwiedź argumenty
        if (ctx.argumentList() != null) visit(ctx.argumentList());

        return null;
    }

    // =========================================================
    //  WYWOŁANIE FUNKCJI JAKO SAMODZIELNA INSTRUKCJA (simpleStmt)
    // =========================================================

    @Override
    public Void visitSimpleStmt(KodekParser.SimpleStmtContext ctx) {
        // Błąd 21: wywołanie funkcji z typem zwracanym użyte jako instrukcja –
        //          wynik jest po cichu ignorowany (ostrzeżenie)
        if (ctx.functionCall() != null) {
            KodekParser.FunctionCallContext fc = ctx.functionCall();
            String fname = fc.ID().getText();
            FunctionInfo info = functions.get(fname);
            if (info == null) info = BUILTINS.get(fname);
            if (info != null && !"void".equals(info.returnType)) {
                addError(fc.ID().getSymbol(),
                        "wynik funkcji '" + fname + "' (typ: '" + pretty(info.returnType)
                                + "') jest ignorowany – czy to zamierzone?");
            }
        }
        return visitChildren(ctx);
    }

    // =========================================================
    //  DEFINICJA FUNKCJI
    // =========================================================

    @Override
    public Void visitFunctionDef(KodekParser.FunctionDefContext ctx) {
        String name       = ctx.ID().getText();
        String returnType = (ctx.typeName() != null) ? kodekType(ctx.typeName()) : "void";

        String previousFunc = currentFuncName;
        currentFuncName = name;

        pushScope();

        // Zarejestruj parametry w nowym zakresie
        if (ctx.paramList() != null) {
            for (int i = 0; i < ctx.paramList().ID().size(); i++) {
                String pType = kodekType(ctx.paramList().typeName(i));
                String pName = ctx.paramList().ID(i).getText();
                scopeStack.peek().put(pName, pType);
            }
        }

        // Błąd 17: puste ciało funkcji nieVoid
        if (!"void".equals(returnType) && ctx.block().statement().isEmpty()) {
            addError(ctx.ID().getSymbol(),
                    "funkcja '" + name + "' powinna zwracać '" + pretty(returnType)
                            + "', ale jej ciało jest puste");
        }

        // Sprawdź ciało (błąd 8: brak zwróć w nieVoid)
        boolean hasReturn = blockHasReturn(ctx.block());
        if (!"void".equals(returnType) && !ctx.block().statement().isEmpty() && !hasReturn) {
            addError(ctx.ID().getSymbol(),
                    "funkcja '" + name + "' powinna zwracać '" + pretty(returnType)
                            + "', ale brakuje instrukcji 'zwróć'");
        }

        visit(ctx.block());
        popScope();
        currentFuncName = previousFunc;
        return null;
    }

    // =========================================================
    //  RETURN
    // =========================================================

    @Override
    public Void visitReturnStmt(KodekParser.ReturnStmtContext ctx) {
        if (currentFuncName == null) {
            // zwróć poza funkcją
            Token tok = ((TerminalNode) ctx.getChild(0)).getSymbol();
            addError(tok, "instrukcja 'zwróć' poza ciałem funkcji");
        } else {
            FunctionInfo info = functions.get(currentFuncName);
            if (info != null && "void".equals(info.returnType)) {
                Token tok = ((TerminalNode) ctx.getChild(0)).getSymbol();
                addError(tok,
                        "funkcja '" + currentFuncName
                                + "' jest void – nie powinna zwracać wartości");
            } else if (info != null) {
                // Błąd 16: wynik void użyty jako zwracana wartość
                checkVoidUsedAsValue(ctx.expression(),
                        ((TerminalNode) ctx.getChild(0)).getSymbol());

                String exprType = inferType(ctx.expression());
                if (!typesCompatible(info.returnType, exprType)) {
                    Token tok = ((TerminalNode) ctx.getChild(0)).getSymbol();
                    addError(tok,
                            "typ zwracanej wartości w '" + currentFuncName
                                    + "': oczekiwano '" + pretty(info.returnType)
                                    + "', zwracany jest '" + pretty(exprType) + "'");
                }
            }
        }
        visit(ctx.expression());
        return null;
    }

    // =========================================================
    //  PĘTLE
    // =========================================================

    @Override
    public Void visitForLoop(KodekParser.ForLoopContext ctx) {
        loopDepth++;
        pushScope();

        String varName = ctx.ID().getText();
        scopeStack.peek().put(varName, "liczba");

        if (ctx.expression().size() == 2) {
            // dla k od X do Y
            visit(ctx.expression(0));
            visit(ctx.expression(1));
        } else {
            // dla elem w lista
            String listExprType = inferType(ctx.expression(0));
            if (!isList(listExprType) && !UNKNOWN.equals(listExprType)) {
                addError(ctx.ID().getSymbol(),
                        "pętla 'dla ... w': wyrażenie nie jest listą (typ: '"
                                + pretty(listExprType) + "')");
            }
            visit(ctx.expression(0));
        }

        visit(ctx.loopBlock());
        popScope();
        loopDepth--;
        return null;
    }

    @Override
    public Void visitWhileLoop(KodekParser.WhileLoopContext ctx) {
        loopDepth++;
        visit(ctx.condition());
        pushScope();
        visit(ctx.loopBlock());
        popScope();
        loopDepth--;
        return null;
    }

    // =========================================================
    //  BREAK / CONTINUE
    // =========================================================

    @Override
    public Void visitBreakStmt(KodekParser.BreakStmtContext ctx) {
        // Gramatyka LoopBlock już wymusza poprawność; to ostrzeżenie jest failsafe
        if (loopDepth == 0) {
            Token tok = ((TerminalNode) ctx.getChild(0)).getSymbol();
            addError(tok, "'przerwij' poza pętlą");
        }
        return null;
    }

    @Override
    public Void visitContinueStmt(KodekParser.ContinueStmtContext ctx) {
        if (loopDepth == 0) {
            Token tok = ((TerminalNode) ctx.getChild(0)).getSymbol();
            addError(tok, "'kontynuuj' poza pętlą");
        }
        return null;
    }

    // =========================================================
    //  IF
    // =========================================================

    @Override
    public Void visitIfStmt(KodekParser.IfStmtContext ctx) {
        for (KodekParser.ConditionContext cond : ctx.condition()) visit(cond);
        for (KodekParser.BlockContext blk : ctx.block()) {
            pushScope(); visit(blk); popScope();
        }
        return null;
    }

    @Override
    public Void visitLoopIfStmt(KodekParser.LoopIfStmtContext ctx) {
        for (KodekParser.ConditionContext cond : ctx.condition()) visit(cond);
        for (KodekParser.LoopBlockContext blk : ctx.loopBlock()) {
            pushScope(); visit(blk); popScope();
        }
        return null;
    }

    // =========================================================
    //  WARUNEK – sprawdź użyte ID
    // =========================================================

    @Override
    public Void visitCondNeg(KodekParser.CondNegContext ctx) {
        if (ctx.ID() != null) {
            String name = ctx.ID().getText();
            Token  tok  = ctx.ID().getSymbol();
            if (lookupVar(name) == null) {
                addError(tok, "użycie niezadeklarowanej zmiennej '" + name + "' w warunku");
            }
        }
        return visitChildren(ctx);
    }

    // =========================================================
    //  ATOM – sprawdź użyte ID
    // =========================================================

    @Override
    public Void visitAtom(KodekParser.AtomContext ctx) {
        if (ctx.ID() != null && ctx.functionCall() == null && ctx.listAccess() == null) {
            String name = ctx.ID().getText();
            Token  tok  = ctx.ID().getSymbol();
            if (lookupVar(name) == null) {
                addError(tok, "użycie niezadeklarowanej zmiennej '" + name + "'");
            }
        }
        return visitChildren(ctx);
    }

    // =========================================================
    //  ARYTMETYKA – sprawdź typy operandów (błędy 10, 20)
    // =========================================================

    @Override
    public Void visitArithmetic(KodekParser.ArithmeticContext ctx) {
        // Sprawdź operandy tylko gdy faktycznie jest operator (+, -)
        if (ctx.term().size() > 1) {
            for (KodekParser.TermContext t : ctx.term()) {
                String type = inferTerm(t);
                Token  tok  = getFirstToken(t);
                if ("tekst".equals(type)) {
                    addError(tok,
                            "operator arytmetyczny (+/-) użyty na wartości typu 'tekst'");
                } else if ("logiczny".equals(type)) {
                    addError(tok,
                            "operator arytmetyczny (+/-) użyty na wartości typu 'logiczny'");
                }
            }
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitTerm(KodekParser.TermContext ctx) {
        // Sprawdź operandy gdy faktycznie jest operator (*, /, %)
        if (ctx.factor().size() > 1) {
            for (KodekParser.FactorContext f : ctx.factor()) {
                String type = inferFactor(f);
                Token  tok  = getFirstToken(f);
                if ("tekst".equals(type)) {
                    addError(tok,
                            "operator arytmetyczny (*/%) użyty na wartości typu 'tekst'");
                } else if ("logiczny".equals(type)) {
                    addError(tok,
                            "operator arytmetyczny (*/%) użyty na wartości typu 'logiczny'");
                }
            }
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitComparison(KodekParser.ComparisonContext ctx) {
        // Błąd 20: operator porównania na typie logiczny (np. prawda > fałsz)
        if (!ctx.compOp().isEmpty()) {
            for (KodekParser.ArithmeticContext a : ctx.arithmetic()) {
                String type = inferArithmetic(a);
                Token  tok  = getFirstToken(a);
                if ("logiczny".equals(type)) {
                    addError(tok,
                            "operator porównania użyty na wartości typu 'logiczny'");
                }
            }
        }
        return visitChildren(ctx);
    }

    // =========================================================
    //  READ / WRITE
    // =========================================================

    @Override
    public Void visitReadStmt(KodekParser.ReadStmtContext ctx) {
        String name = ctx.ID().getText();
        Token  tok  = ctx.ID().getSymbol();
        if (lookupVar(name) == null) {
            addError(tok, "czytaj(): niezadeklarowana zmienna '" + name + "'");
        }
        return null;
    }

    @Override
    public Void visitWriteStmt(KodekParser.WriteStmtContext ctx) {
        // Błąd 16: wynik void użyty jako argument pisz/piszln
        checkVoidUsedAsValue(ctx.expression(), ((TerminalNode) ctx.getChild(0)).getSymbol());
        visit(ctx.expression());
        return null;
    }

    // =========================================================
    //  BLOKI
    // =========================================================

    @Override
    public Void visitBlock(KodekParser.BlockContext ctx) {
        pushScope();
        visitChildren(ctx);
        popScope();
        return null;
    }

    @Override
    public Void visitLoopBlock(KodekParser.LoopBlockContext ctx) {
        pushScope();
        visitChildren(ctx);
        popScope();
        return null;
    }

    // =========================================================
    //  INFERENCJA TYPÓW (uproszczona, analogiczna do CGenerator)
    // =========================================================

    private String inferType(KodekParser.ExpressionContext ctx) {
        return inferLogicalOr(ctx.logicalOr());
    }

    private String inferLogicalOr(KodekParser.LogicalOrContext ctx) {
        if (ctx.logicalAnd().size() > 1) return "logiczny";
        return inferLogicalAnd(ctx.logicalAnd(0));
    }

    private String inferLogicalAnd(KodekParser.LogicalAndContext ctx) {
        if (ctx.negation().size() > 1) return "logiczny";
        return inferNegation(ctx.negation(0));
    }

    private String inferNegation(KodekParser.NegationContext ctx) {
        if (ctx.negation() != null) return "logiczny";
        return inferComparison(ctx.comparison());
    }

    private String inferComparison(KodekParser.ComparisonContext ctx) {
        if (!ctx.compOp().isEmpty()) return "logiczny";
        return inferArithmetic(ctx.arithmetic(0));
    }

    private String inferArithmetic(KodekParser.ArithmeticContext ctx) {
        for (KodekParser.TermContext t : ctx.term()) {
            if ("ułamek".equals(inferTerm(t))) return "ułamek";
        }
        return inferTerm(ctx.term(0));
    }

    private String inferTerm(KodekParser.TermContext ctx) {
        for (KodekParser.FactorContext f : ctx.factor()) {
            if ("ułamek".equals(inferFactor(f))) return "ułamek";
        }
        return inferFactor(ctx.factor(0));
    }

    private String inferFactor(KodekParser.FactorContext ctx) {
        if (ctx.factor() != null) return "ułamek"; // potęgowanie → double
        return inferBase(ctx.base());
    }

    private String inferBase(KodekParser.BaseContext ctx) {
        if (ctx.expression() != null) return inferType(ctx.expression());
        return inferAtom(ctx.atom());
    }

    private String inferAtom(KodekParser.AtomContext ctx) {
        if (ctx.NUMBER()      != null) return ctx.NUMBER().getText().contains(".") ? "ułamek" : "liczba";
        if (ctx.STRING()      != null) return "tekst";
        if (ctx.BOOLEAN()     != null) return "logiczny";
        if (ctx.listLiteral() != null) {
            List<KodekParser.ExpressionContext> elems = ctx.listLiteral().expression();
            return "lista:" + (elems.isEmpty() ? UNKNOWN : inferType(elems.get(0)));
        }
        if (ctx.functionCall()!= null) return inferFunctionCall(ctx.functionCall());
        if (ctx.listAccess()  != null) return listElem(lookupVar(ctx.listAccess().ID().getText()));
        if (ctx.ID()          != null) {
            String t = lookupVar(ctx.ID().getText());
            return t != null ? t : UNKNOWN;   // niezadeklarowana zmienna – typ nieznany
        }
        return UNKNOWN;
    }

    private String inferFunctionCall(KodekParser.FunctionCallContext ctx) {
        String name = ctx.ID().getText();
        FunctionInfo info = functions.get(name);
        if (info == null) info = BUILTINS.get(name);
        return (info != null) ? info.returnType : UNKNOWN;
    }

    // =========================================================
    //  POMOCNICZE
    // =========================================================

    /** Sentinel: typ, którego nie udało się ustalić (np. zmienna niezadeklarowana). */
    private static final String UNKNOWN = "?";

    // ---- pomocniki typów list (kanoniczne: "lista", "lista:tekst", ...) ----

    /** Czy typ oznacza listę (z lub bez sprecyzowanego typu elementu)? */
    private static boolean isList(String t) { return t != null && t.startsWith("lista"); }

    /** Typ elementu listy; "lista" bez ':' = element nieznany (wildcard). */
    private static String listElem(String t) {
        if (t == null) return UNKNOWN;
        int i = t.indexOf(':');
        return (i >= 0) ? t.substring(i + 1) : UNKNOWN;
    }

    /** Czytelna dla użytkownika postać typu w komunikatach ("lista:tekst" → "lista tekst"). */
    private static String pretty(String t) {
        return (t == null) ? null : t.replace("lista:", "lista ");
    }

    /** Kanoniczna reprezentacja typu Kodek z węzła typeName ("liczba", "lista:tekst", ...). */
    private static String kodekType(KodekParser.TypeNameContext ctx) {
        if (ctx.getChildCount() > 0 && "lista".equals(ctx.getChild(0).getText())) {
            return (ctx.scalarType() != null) ? "lista:" + ctx.scalarType().getText() : "lista:liczba";
        }
        return ctx.scalarType().getText();
    }

    /**
     * Sprawdza czy typ wyrażenia jest kompatybilny z typem docelowym.
     * Uproszczone reguły:
     *   liczba  ↔ ułamek   (niejawna konwersja liczbowa)
     *   "?"     ↔ każdy     gdy typ jest nieznany – nie blokujemy (unikamy fałszywych alarmów)
     *   lista   ↔ lista     zgodne gdy zgodne są typy elementów (lub któryś nieokreślony)
     * Twarde niezgodności (np. tekst ↔ liczba, lista tekstów ↔ lista liczb) są zgłaszane jako błąd.
     */
    private boolean typesCompatible(String expected, String actual) {
        if (expected == null || actual == null)         return true;
        if (UNKNOWN.equals(expected) || UNKNOWN.equals(actual)) return true;
        if (expected.equals(actual))                    return true;
        if (isList(expected) && isList(actual))         return typesCompatible(listElem(expected), listElem(actual));
        if (isList(expected) || isList(actual))         return false;
        if ("liczba".equals(actual) && "ułamek".equals(expected)) return true;
        if ("ułamek".equals(actual) && "liczba".equals(expected)) return true;
        return false;
    }

    /**
     * Czy blok zawiera co najmniej jedno bezpośrednie 'zwróć' (bez zagłębiania w funkcje)?
     */
    private boolean blockHasReturn(KodekParser.BlockContext ctx) {
        for (KodekParser.StatementContext stmt : ctx.statement()) {
            if (stmt.simpleStmt() != null && stmt.simpleStmt().returnStmt() != null) return true;
            if (stmt.blockStmt() != null) {
                KodekParser.BlockStmtContext bs = stmt.blockStmt();
                // sprawdź if/else – każda gałąź może mieć return
                if (bs.ifStmt() != null) {
                    KodekParser.IfStmtContext is = bs.ifStmt();
                    boolean allBranchesReturn = true;
                    for (KodekParser.BlockContext b : is.block()) {
                        if (!blockHasReturn(b)) { allBranchesReturn = false; break; }
                    }
                    if (allBranchesReturn && is.block().size() > is.condition().size()) return true;
                }
            }
        }
        return false;
    }

    /**
     * Sprawdza dzielenie przez literał 0 w DOWOLNYM kontekście (deklaracje, przypisania,
     * argumenty funkcji, pisz()/piszln(), warunki itd.). Przechodzi całe drzewo i bada
     * każdy węzeł Term pod kątem operatora "/" lub "%", po którym następny czynnik (factor)
     * jest literałem 0.
     */
    private void checkDivisionByZero(ParseTree node) {
        if (node instanceof KodekParser.TermContext) {
            checkTermForDivByZero((KodekParser.TermContext) node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            checkDivisionByZero(node.getChild(i));
        }
    }

    private void checkTermForDivByZero(KodekParser.TermContext term) {
        List<KodekParser.FactorContext> factors = term.factor();
        for (int i = 1; i < factors.size(); i++) {
            String op = term.getChild(2 * i - 1).getText();
            if (!"/".equals(op) && !"%".equals(op)) continue;
            KodekParser.FactorContext divisor = factors.get(i);
            if (divisor.base() != null
                    && divisor.base().atom() != null
                    && divisor.base().atom().NUMBER() != null) {
                String numText = divisor.base().atom().NUMBER().getText();
                if (isZeroLiteral(numText)) {
                    addError(divisor.base().atom().NUMBER().getSymbol(), "dzielenie przez zero");
                }
            }
        }
    }

    /** Czy literał liczbowy reprezentuje zero (np. "0", "0.0", "0.000")? */
    private boolean isZeroLiteral(String numText) {
        try {
            return Double.parseDouble(numText) == 0.0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // =========================================================
    //  NOWE SPRAWDZENIA – METODY POMOCNICZE
    // =========================================================

    /**
     * Błąd 16: sprawdza czy wyrażenie jest wywołaniem funkcji void i jeśli tak,
     * zgłasza błąd – wynik void nie może być użyty jako wartość.
     */
    private void checkVoidUsedAsValue(KodekParser.ExpressionContext expr, Token contextTok) {
        if (expr == null) return;
        // Szybkie sprawdzenie: wyrażenie to bezpośrednio atom z functionCall?
        KodekParser.FunctionCallContext fc = extractDirectFunctionCall(expr);
        if (fc == null) return;
        String fname = fc.ID().getText();
        FunctionInfo info = functions.get(fname);
        if (info == null) info = BUILTINS.get(fname);
        if (info != null && "void".equals(info.returnType)) {
            Token tok = (contextTok != null) ? contextTok : fc.ID().getSymbol();
            addError(tok,
                    "funkcja '" + fname + "' nie zwraca wartości (void) – "
                            + "jej wynik nie może być użyty jako wartość");
        }
    }

    /**
     * Zwraca FunctionCallContext jeśli wyrażenie sprowadza się wyłącznie do
     * pojedynczego wywołania funkcji (bez żadnych operatorów), null w pozostałych przypadkach.
     */
    private KodekParser.FunctionCallContext extractDirectFunctionCall(KodekParser.ExpressionContext expr) {
        try {
            KodekParser.LogicalOrContext  lor  = expr.logicalOr();
            if (lor.logicalAnd().size() != 1) return null;
            KodekParser.LogicalAndContext land = lor.logicalAnd(0);
            if (land.negation().size() != 1)  return null;
            KodekParser.NegationContext   neg  = land.negation(0);
            if (neg.negation() != null)       return null;
            KodekParser.ComparisonContext  cmp  = neg.comparison();
            if (!cmp.compOp().isEmpty())      return null;
            KodekParser.ArithmeticContext  arith = cmp.arithmetic(0);
            if (arith.term().size() != 1)     return null;
            KodekParser.TermContext        term  = arith.term(0);
            if (term.factor().size() != 1)    return null;
            KodekParser.FactorContext      fac   = term.factor(0);
            if (fac.factor() != null)         return null;
            KodekParser.BaseContext        base  = fac.base();
            if (base.expression() != null)    return null;
            KodekParser.AtomContext        atom  = base.atom();
            return atom.functionCall();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Błąd 18: sprawdza czy wyrażenie indeksu jest ujemnym literałem.
     * Obsługuje formę "- NUMBER" (unarne minus jako osobny token) oraz ujemny literał.
     */
    private void checkNegativeIndex(KodekParser.ExpressionContext indexExpr, Token contextTok) {
        if (indexExpr == null) return;
        try {
            // Sprawdź czy to proste "-NUMBER" (arithmetic z tokenem minus i jednym termem)
            KodekParser.ArithmeticContext arith =
                    indexExpr.logicalOr().logicalAnd(0).negation(0).comparison().arithmetic(0);
            // Forma: term '-' term gdzie pierwszy term to brak, a drugi to liczba → nie, sprawdź inaczej
            // Wystarczy sprawdzić czy arithmetic ma operator '-' i pierwszy term jest 0-owy literał
            // lub czy atom jest liczbą ujemną (gramatyka nie ma unary minus, więc sprawdzamy arithmetic)
            if (arith.term().size() == 1) {
                // Może być literał – sprawdzamy czy to liczba ujemna przez konwersję tekstu
                // Gramatyka Kodek nie obsługuje unary minus w literałach, więc ujemny indeks
                // musiałby pojawić się jako "0 - X". Jeśli jednak parsuje się jako samo NUMBER
                // z wartością ujemną (np. przez rozszerzenie gramatyki), sprawdzamy to:
                KodekParser.AtomContext atom =
                        arith.term(0).factor(0).base().atom();
                if (atom != null && atom.NUMBER() != null) {
                    String txt = atom.NUMBER().getText();
                    try {
                        double val = Double.parseDouble(txt);
                        if (val < 0) {
                            addError(contextTok, "indeks listy nie może być ujemny (podano: " + txt + ")");
                        }
                    } catch (NumberFormatException ignored) { }
                }
            }
            // Forma "0 - N" lub podobna: jeśli wynikiem jest ujemna liczba całkowita (znamy oba operandy)
            if (arith.term().size() >= 2) {
                String firstOp = arith.getChild(1).getText(); // operator między term(0) a term(1)
                if ("-".equals(firstOp)) {
                    // Oba składniki mogą być literałami – sprawdź
                    Double lhs = tryExtractNumber(arith.term(0));
                    Double rhs = tryExtractNumber(arith.term(1));
                    if (lhs != null && rhs != null && (lhs - rhs) < 0) {
                        addError(contextTok,
                                "indeks listy nie może być ujemny (wartość: " + ((Double)(lhs - rhs)).intValue() + ")");
                    }
                }
            }
        } catch (Exception ignored) { }
    }

    /**
     * Próbuje wyłuskać wartość liczbową z prostego term (bez operatorów),
     * zwraca null jeśli niemożliwe.
     */
    private Double tryExtractNumber(KodekParser.TermContext term) {
        try {
            if (term.factor().size() != 1) return null;
            KodekParser.AtomContext atom = term.factor(0).base().atom();
            if (atom == null || atom.NUMBER() == null) return null;
            return Double.parseDouble(atom.NUMBER().getText());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Błąd 19: sprawdza czy pętla 'dla k od X do Y' ma pusty zakres gdy oba krańce
     * są literałami całkowitymi i start > koniec.
     */
    private void checkEmptyForRange(KodekParser.ExpressionContext fromExpr,
                                    KodekParser.ExpressionContext toExpr,
                                    Token varTok) {
        Double from = tryExtractExprNumber(fromExpr);
        Double to   = tryExtractExprNumber(toExpr);
        if (from != null && to != null && from > to) {
            addError(varTok,
                    "pętla 'dla...od...do' ma pusty zakres: " + from.intValue()
                            + " > " + to.intValue() + " – ciało pętli nigdy nie zostanie wykonane");
        }
    }

    /**
     * Próbuje wyłuskać wartość liczbową z prostego wyrażenia (sam literał).
     */
    private Double tryExtractExprNumber(KodekParser.ExpressionContext expr) {
        try {
            return tryExtractNumber(
                    expr.logicalOr().logicalAnd(0).negation(0)
                            .comparison().arithmetic(0).term(0));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Próbuje uzyskać pierwszy token z dowolnego kontekstu parsera
     * (używane do podawania lokalizacji błędów arytmetycznych).
     */
    private Token getFirstToken(ParserRuleContext ctx) {
        Token t = ctx.getStart();
        return (t != null) ? t : new CommonToken(0, "?");
    }
}