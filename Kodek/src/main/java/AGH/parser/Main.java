package AGH.parser;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Punkt wejścia CLI dla języka Kodek.
 *
 * Użycie:
 *   mvn exec:java -Dexec.args="program.kodek"
 *
 * Fazy:
 *   1. Leksowanie  – tokenizacja pliku źródłowego
 *   2. Parsowanie  – budowa drzewa składniowego (AST)
 *   3. Generowanie – AST → kod C (przez CGenerator)
 *   4. Kompilacja  – gcc output.c -o output -lm
 *   5. Uruchomienie – ./output
 *
 * Poprawki v2:
 *   - Wczesne wyjście (exit 1) przy błędach leksykalnych lub składniowych
 *   - Sprawdzanie exit code gcc przed uruchomieniem
 *   - Generowanie pliku C obok pliku źródłowego (nie w CWD)
 */
public class Main {

    public static void main(String[] args) throws Exception {
        List<String> argList = Arrays.asList(args);
        boolean debug = argList.contains("--debug");
        // Pierwszy argument niebędący flagą to plik źródłowy
        String sourceFile = argList.stream()
                .filter(a -> !a.startsWith("--"))
                .findFirst()
                .orElse("test.kodek");

        // =================================================================
        // Faza 1: Leksowanie
        // =================================================================
        CharStream input = CharStreams.fromFileName(sourceFile, StandardCharsets.UTF_8);
        KodekLexer lexer = new KodekLexer(input);
        lexer.removeErrorListeners();

        final int[] lexErrors = {0};
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> r, Object sym, int line, int col,
                                    String msg, RecognitionException e) {
                lexErrors[0]++;
                System.err.printf("  [błąd leksera] %d:%d — %s%n", line, col, msg);
            }
        });

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();

        if (debug) {
            printBanner("Lista tokenów");
            List<Token> tokenList = tokens.getTokens();
            for (Token t : tokenList) {
                if (t.getType() == Token.EOF) break;
                String tName = KodekParser.VOCABULARY.getDisplayName(t.getType());
                String tText = t.getText()
                        .replace("\n", "\\n").replace("\t", "\\t").replace("\r", "\\r");
                System.out.printf("  %-20s '%s'  (linia %d)%n", tName, tText, t.getLine());
            }
        }

        if (lexErrors[0] > 0) {
            System.err.printf("%nZnaleziono %d błąd(ów) leksykalnych. Przerywam.%n", lexErrors[0]);
            System.exit(1);
        }

        // =================================================================
        // Faza 2: Parsowanie
        // =================================================================
        tokens.seek(0);
        KodekParser parser = new KodekParser(tokens);
        parser.removeErrorListeners();

        final int[] parseErrors = {0};
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> r, Object sym, int line, int col,
                                    String msg, RecognitionException e) {
                parseErrors[0]++;
                System.err.printf("  [błąd składni] %d:%d — %s%n", line, col, msg);
            }
        });

        ParseTree tree = parser.program();

        if (debug) {
            printBanner("Drzewo parsowania");
            System.out.println(prettyTree(tree, parser, 0));
        }

        if (parseErrors[0] > 0) {
            System.err.printf("Znaleziono %d błąd(ów) składniowych. Przerywam.%n", parseErrors[0]);
            System.exit(1);
        }

        // =================================================================
        // Faza 2.5: Analiza semantyczna
        // =================================================================

        KodekErrorHandler errorHandler = new KodekErrorHandler();
        errorHandler.check(tree);

        if (errorHandler.hasErrors()) {
            printBanner("Błędy semantyczne");
            errorHandler.printErrors(System.err);
            System.exit(1);
        }

        if (errorHandler.hasWarnings()) {
            printBanner("Ostrzeżenia semantyczne");
            errorHandler.printWarnings(System.err);
        }

        // =================================================================
        // Faza 3: Generowanie kodu C
        // =================================================================
        CGenerator generator = new CGenerator();
        String cCode = generator.generate(tree);

        // Zapisz obok pliku źródłowego
        Path sourceDir = Path.of(sourceFile).toAbsolutePath().getParent();
        Path cFile  = (sourceDir != null ? sourceDir : Path.of(".")).resolve("output.c");
        Path binary = (sourceDir != null ? sourceDir : Path.of(".")).resolve("output");

        java.nio.file.Files.writeString(cFile, cCode, StandardCharsets.UTF_8);
        System.out.printf("%nKod C zapisany do: %s%n", cFile);

        // =================================================================
        // Faza 4: Kompilacja GCC
        // =================================================================
        System.out.println("Kompilowanie...");
        int compileExit = new ProcessBuilder("gcc",
                cFile.toString(), "-o", binary.toString(), "-lm", "-Wall")
                .inheritIO()
                .start()
                .waitFor();

        if (compileExit != 0) {
            System.err.printf("Kompilacja GCC zakończona błędem (kod %d). Przerywam.%n", compileExit);
            System.exit(compileExit);
        }

        // =================================================================
        // Faza 5: Uruchomienie programu
        // =================================================================
        System.out.println("Uruchamianie programu:");
        System.out.println("─".repeat(40));

        int runExit = new ProcessBuilder(binary.toString())
                .inheritIO()
                .start()
                .waitFor();

        System.out.println("─".repeat(40));
        System.out.printf("Program zakończył się z kodem: %d%n", runExit);
    }

    // =========================================================
    //  POMOCNICZE
    // =========================================================

    private static void printBanner(String title) {
        System.out.printf("╔══════════════════════════════════════╗%n");
        System.out.printf("║  %-36s║%n", title);
        System.out.printf("╚══════════════════════════════════════╝%n");
    }

    private static String prettyTree(ParseTree node, KodekParser parser, int depth) {
        StringBuilder sb = new StringBuilder();
        String indent = "  ".repeat(depth);

        if (node instanceof TerminalNode) {
            Token t = ((TerminalNode) node).getSymbol();
            String name = KodekParser.VOCABULARY.getDisplayName(t.getType());
            String text = t.getText().replace("\n", "\\n").replace("\t", "\\t");
            sb.append(indent).append(name).append(" '").append(text).append("'\n");
        } else {
            String ruleName = parser.getRuleNames()[((RuleContext) node).getRuleIndex()];
            sb.append(indent).append("[").append(ruleName).append("]\n");
            for (int i = 0; i < node.getChildCount(); i++) {
                sb.append(prettyTree(node.getChild(i), parser, depth + 1));
            }
        }
        return sb.toString();
    }
}
