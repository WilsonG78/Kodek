package AGH.parser;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Wspólny pipeline testowy: leksowanie → parsowanie → analiza semantyczna → generacja C.
 * Odzwierciedla ścieżkę z {@link Main} (fazy 1–3).
 */
final class KodekTestSupport {

    /** Wynik analizy semantycznej (błędy + ostrzeżenia). */
    static final class AnalysisResult {
        final List<KodekErrorHandler.SemanticError> errors;
        final List<KodekErrorHandler.SemanticWarning> warnings;

        AnalysisResult(List<KodekErrorHandler.SemanticError> errors,
                       List<KodekErrorHandler.SemanticWarning> warnings) {
            this.errors = errors;
            this.warnings = warnings;
        }
    }

    private KodekTestSupport() {}

    /** Parsuje kod Kodek i zwraca drzewo składniowe (bez sprawdzania błędów). */
    static ParseTree parse(String kodekCode) {
        CharStream input = CharStreams.fromString(kodekCode);
        KodekLexer lexer = new KodekLexer(input);
        lexer.removeErrorListeners();
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        KodekParser parser = new KodekParser(tokens);
        parser.removeErrorListeners();
        return parser.program();
    }

    /** Uruchamia pełną analizę semantyczną. */
    static AnalysisResult analyzeAll(String kodekCode) {
        ParseTree tree = parse(kodekCode);
        KodekErrorHandler handler = new KodekErrorHandler();
        handler.check(tree);
        return new AnalysisResult(handler.getErrors(), handler.getWarnings());
    }

    /** Uruchamia analizę semantyczną i zwraca zebrane błędy. */
    static List<KodekErrorHandler.SemanticError> analyze(String kodekCode) {
        return analyzeAll(kodekCode).errors;
    }

    /**
     * Pełny pipeline produkcyjny: parse → semantyka → generacja C.
     * Rzuca {@link AssertionError} gdy analiza semantyczna wykryje błędy.
     */
    static String generate(String kodekCode) {
        AnalysisResult result = analyzeAll(kodekCode);
        if (!result.errors.isEmpty()) {
            String details = result.errors.stream()
                    .map(KodekErrorHandler.SemanticError::toString)
                    .collect(Collectors.joining("\n"));
            throw new AssertionError("Błędy semantyczne w kodzie testowym:\n" + details);
        }
        return new CGenerator().generate(parse(kodekCode));
    }
}
