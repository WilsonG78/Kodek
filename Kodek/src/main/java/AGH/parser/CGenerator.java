package AGH.parser;

import org.antlr.v4.runtime.tree.*;
import org.antlr.v4.runtime.*;
import java.util.HashMap;
import java.util.Map;

/**
 * CGenerator - przechodzi po drzewie parsowania Kodek
 * i generuje odpowiedni kod w języku C.
 *
 * Użycie w Main.java:
 *   CGenerator gen = new CGenerator();
 *   String cCode = gen.generate(tree);
 *   System.out.println(cCode);
 *   // lub zapisz do pliku .c
 */
public class CGenerator extends KodekBaseVisitor<String> {

    // Tablica symboli: nazwa zmiennej -> typ (np. "liczba", "tekst", itp.)
    private final Map<String, String> symbolTable = new HashMap<>();

    // Aktualny poziom wcięcia (dla czytelności generowanego kodu C)
    private int indentLevel = 0;

    // =========================================================
    //  PUNKT WEJŚCIA
    // =========================================================

    /** Generuje pełny plik .c z drzewa parsowania. */
    public String generate(org.antlr.v4.runtime.tree.ParseTree tree) {
        StringBuilder sb = new StringBuilder();
        sb.append("#include <stdio.h>\n");
        sb.append("#include <stdlib.h>\n");
        sb.append("#include <string.h>\n");
        sb.append("#include <math.h>\n");
        sb.append("\n");
        sb.append("int main() {\n");
        indentLevel = 1;
        sb.append(visit(tree));
        indentLevel = 0;
        sb.append("    return 0;\n");
        sb.append("}\n");
        return sb.toString();
    }

    // =========================================================
    //  POMOCNICZE
    // =========================================================

    private String indent() {
        return "    ".repeat(indentLevel);
    }

    /** Mapuje typ Kodek na typ C. */
    private String toCType(String kodekType) {
        switch (kodekType) {
            case "liczba":   return "int";
            case "ułamek":   return "double";
            case "tekst":    return "char*";
            case "logiczny": return "int";
            case "lista":    return "int"; // uproszczenie - lista intów
            default:         return "int";
        }
    }

    /** Zwraca format printf dla danego typu. */
    private String printfFormat(String kodekType) {
        switch (kodekType) {
            case "liczba":   return "%d";
            case "ułamek":   return "%f";
            case "tekst":    return "%s";
            case "logiczny": return "%d";
            default:         return "%d";
        }
    }

    /** Próbuje odgadnąć typ wyrażenia na podstawie jego zawartości. */
    private String guessType(String expr) {
        expr = expr.trim();
        if (expr.startsWith("\""))        return "tekst";
        if (expr.contains("."))           return "ułamek";
        if (expr.equals("0") || expr.equals("1")
                || expr.equals("prawda") || expr.equals("fałsz")) return "logiczny";
        // sprawdź tablicę symboli
        if (symbolTable.containsKey(expr)) return symbolTable.get(expr);
        return "liczba"; // domyślnie
    }

    // =========================================================
    //  PROGRAM
    // =========================================================

    @Override
    public String visitProgram(KodekParser.ProgramContext ctx) {
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
        // proste instrukcje kończą się średnikiem
        return indent() + inner + ";\n";
    }

    @Override
    public String visitBlockStmt(KodekParser.BlockStmtContext ctx) {
        // bloki (if, for, while, funkcja) same dodają wcięcia
        return visit(ctx.getChild(0));
    }

    // =========================================================
    //  DEKLARACJA ZMIENNEJ
    // =========================================================

    @Override
    public String visitVarDecl(KodekParser.VarDeclContext ctx) {
        String type    = ctx.typeName().getText();
        String name    = ctx.ID().getText();
        symbolTable.put(name, type);

        String cType = toCType(type);

        if (type.equals("lista")) {
            // zmienna lista oceny = [5, 4, 3]
            if (ctx.expression() != null) {
                String listExpr = visit(ctx.expression()); // np. "{5, 4, 3}"
                // policz elementy (liczba przecinków + 1)
                int size = listExpr.split(",").length;
                return cType + " " + name + "[] = " + listExpr + ";\n"
                        + indent() + "int " + name + "_len = " + size;
            } else {
                return cType + " *" + name + " = NULL;\n"
                     + indent() + "int " + name + "_len = 0;";
            }
        }

        if (ctx.expression() != null) {
            String val = visit(ctx.expression());
            return cType + " " + name + " = " + val;
        } else {
            return cType + " " + name;
        }
    }

    // =========================================================
    //  PRZYPISANIE
    // =========================================================

    @Override
    public String visitAssignment(KodekParser.AssignmentContext ctx) {
        String name = ctx.ID().getText();

        if (ctx.expression().size() == 2) {
            // lista[i] = val
            String index = visit(ctx.expression(0));
            String val   = visit(ctx.expression(1));
            return name + "[" + index + "] = " + val;
        } else {
            // zmienna = wyrażenie
            String val = visit(ctx.expression(0));
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
            // operator: '+' lub '-' jest tokenem między termami
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
            // potęgowanie: a ^ b  ->  pow(a, b)
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
        if (ctx.NUMBER()  != null) return ctx.NUMBER().getText();
        if (ctx.STRING()  != null) return ctx.STRING().getText();
        if (ctx.BOOLEAN() != null) return ctx.BOOLEAN().getText().equals("prawda") ? "1" : "0";
        if (ctx.listLiteral() != null) return visit(ctx.listLiteral());
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
        return ctx.ID().getText() + "[" + visit(ctx.expression()) + "]";
    }

    // =========================================================
    //  WARUNEK (condition - używany w if/while)
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
        if (first.equals("nie")) {
            return "!(" + visit(ctx.condNeg()) + ")";
        }
        if (ctx.BOOLEAN() != null) {
            return ctx.BOOLEAN().getText().equals("prawda") ? "1" : "0";
        }
        if (ctx.ID() != null) {
            return ctx.ID().getText();
        }
        if (ctx.condition() != null) {
            return "(" + visit(ctx.condition()) + ")";
        }
        if (ctx.strictComparison() != null) {
            return visit(ctx.strictComparison());
        }
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

        // Zbieramy wszystkie bloki (condition + block naprzemiennie)
        // Struktura w drzewie: 'jeśli' '(' cond ')' block
        //                      ('inaczej' 'jeśli' '(' cond ')' block)*
        //                      ('inaczej' block)?
        //
        // ctx.condition() zwraca listę wszystkich warunków
        // ctx.block()     zwraca listę wszystkich bloków

        for (int i = 0; i < ctx.condition().size(); i++) {
            String keyword = (i == 0) ? "if" : " else if";
            sb.append(indent()).append(keyword)
              .append(" (").append(visit(ctx.condition(i))).append(") ");
            sb.append(visitBlock(ctx.block(i)));
        }

        // else (ostatni blok jeśli jest więcej bloków niż warunków)
        if (ctx.block().size() > ctx.condition().size()) {
            sb.append(indent()).append(" else ");
            sb.append(visitBlock(ctx.block(ctx.block().size() - 1)));
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

        if (ctx.getChild(2).getText().equals("od")) {
            // dla k od 1 do 10 { ... }
            String from = visit(ctx.expression(0));
            String to   = visit(ctx.expression(1));
            sb.append(indent())
              .append("for (int ").append(varName).append(" = ").append(from)
              .append("; ").append(varName).append(" <= ").append(to)
              .append("; ").append(varName).append("++) ");
            sb.append(visitBlock(ctx.block()));
        } else {
            // dla ocena w oceny { ... }  ->  for-each po liście
            String listName = visit(ctx.expression(0));
            sb.append(indent())
              .append("for (int _i = 0; _i < ").append(listName).append("_len; _i++) {\n");
            indentLevel++;
            // typ elementu - uproszczenie: int
            String elemType = symbolTable.getOrDefault(listName, "lista");
            String cElemType = elemType.equals("lista") ? "int" : toCType(elemType);
            sb.append(indent()).append(cElemType).append(" ").append(varName)
              .append(" = ").append(listName).append("[_i];\n");
            // wnętrze bloku (bez nawiasów klamrowych bo już je otwieramy)
            for (KodekParser.StatementContext stmt : ctx.block().statement()) {
                sb.append(visit(stmt));
            }
            indentLevel--;
            sb.append(indent()).append("}\n");
        }
        return sb.toString();
    }

    @Override
    public String visitWhileLoop(KodekParser.WhileLoopContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append(indent())
          .append("while (").append(visit(ctx.condition())).append(") ");
        sb.append(visitBlock(ctx.block()));
        return sb.toString();
    }

    // =========================================================
    //  BLOK  { instrukcje }
    // =========================================================

    @Override
    public String visitBlock(KodekParser.BlockContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        indentLevel++;
        for (KodekParser.StatementContext stmt : ctx.statement()) {
            sb.append(visit(stmt));
        }
        indentLevel--;
        sb.append(indent()).append("}\n");
        return sb.toString();
    }

    // =========================================================
    //  FUNKCJE
    // =========================================================

    @Override
    public String visitFunctionDef(KodekParser.FunctionDefContext ctx) {
        StringBuilder sb = new StringBuilder();

        // typ zwracany
        String returnType = "void";
        if (ctx.typeName() != null) {
            returnType = toCType(ctx.typeName().getText());
        }

        String name = ctx.ID().getText();
        sb.append(returnType).append(" ").append(name).append("(");

        if (ctx.paramList() != null) {
            sb.append(visit(ctx.paramList()));
        }
        sb.append(") ");

        sb.append(visitBlock(ctx.block()));
        return sb.toString();
    }

    @Override
    public String visitParamList(KodekParser.ParamListContext ctx) {
        StringBuilder sb = new StringBuilder();
        // paramList: typeName ID (',' typeName ID)*
        // dzieci: type0 id0 ',' type1 id1 ...
        int paramCount = ctx.typeName().size();
        for (int i = 0; i < paramCount; i++) {
            if (i > 0) sb.append(", ");
            String type = ctx.typeName(i).getText();
            String pname = ctx.ID(i).getText();
            symbolTable.put(pname, type);
            sb.append(toCType(type)).append(" ").append(pname);
        }
        return sb.toString();
    }

    @Override
    public String visitFunctionCall(KodekParser.FunctionCallContext ctx) {
        String name = ctx.ID().getText();

        // Wbudowane funkcje Kodek -> odpowiedniki w C
        switch (name) {
            case "pierwiastek":         name = "sqrt";  break;
            case "wartość_bezwzględna": name = "abs";   break;
            case "zaokrąglij":          name = "round"; break;
            case "losowa_liczba":
                // losowa_liczba(min, max) -> (rand() % (max-min+1) + min)
                if (ctx.argumentList() != null) {
                    String min = visit(ctx.argumentList().expression(0));
                    String max = visit(ctx.argumentList().expression(1));
                    return "(rand() % (" + max + " - " + min + " + 1) + " + min + ")";
                }
                break;
            case "długość":
                if (ctx.argumentList() != null) {
                    return "strlen(" + visit(ctx.argumentList().expression(0)) + ")";
                }
                break;
            case "góra":
                // brak prostego odpowiednika w C - wymagałoby pętli; zostawiamy jako TODO
                break;
            case "dół":
                break;
            case "dodaj":
                // dodaj(lista, element) - uproszczenie: nie obsługujemy dynamicznych list
                // TODO: zaimplementować z realloc
                return "/* dodaj - TODO */";
            case "rozmiar":
                if (ctx.argumentList() != null) {
                    String listName = visit(ctx.argumentList().expression(0));
                    return listName + "_len";
                }
                break;
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
            sb.append(visit(ctx.expression(i)));
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
        String expr = visit(ctx.expression());
        String type = guessType(expr);
        String fmt  = printfFormat(type) + (newline ? "\\n" : "");
        return "printf(\"" + fmt + "\", " + expr + ")";
    }

    @Override
    public String visitReadStmt(KodekParser.ReadStmtContext ctx) {
        String varName = ctx.ID().getText();
        String type    = symbolTable.getOrDefault(varName, "liczba");
        String fmt     = printfFormat(type);
        // dla tekstu potrzebny bufor - uproszczenie
        if (type.equals("tekst")) {
            return "scanf(\"" + fmt + "\", " + varName + ")";
        }
        return "scanf(\"" + fmt + "\", &" + varName + ")";
    }

    // =========================================================
    //  PLIK
    // =========================================================

    @Override
    public String visitFileStmt(KodekParser.FileStmtContext ctx) {
        if (ctx.getChild(0).getText().equals("otwórz")) {
            String path   = visit(ctx.expression());
            String handle = ctx.ID().getText();
            symbolTable.put(handle, "plik");
            return "FILE *" + handle + " = fopen(" + path + ", \"r\")";
        } else {
            // zamknij
            return "fclose(" + ctx.ID().getText() + ")";
        }
    }

    // =========================================================
    //  BREAK / CONTINUE
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
