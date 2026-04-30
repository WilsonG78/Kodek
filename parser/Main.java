import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        String file = args.length > 0 ? args[0] : "test.kodek";

        CharStream input = CharStreams.fromFileName(file, StandardCharsets.UTF_8);
        KodekLexer lexer = new KodekLexer(input);
        lexer.removeErrorListeners();
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?,?> r, Object sym, int line, int col,
                                    String msg, RecognitionException e) {
                System.err.printf("  [błąd leksera] %d:%d — %s%n", line, col, msg);
            }
        });

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();

        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║           Lista tokenów              ║");
        System.out.println("╚══════════════════════════════════════╝");
        List<Token> tokenList = tokens.getTokens();
        for (Token t : tokenList) {
            if (t.getType() == Token.EOF) break;
            String name = KodekParser.VOCABULARY.getDisplayName(t.getType());
            String text = t.getText()
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                .replace("\r", "\\r");
            System.out.printf("  %-20s '%s'  (linia %d)%n", name, text, t.getLine());
        }

        System.out.println();
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║         Drzewo parsowania            ║");
        System.out.println("╚══════════════════════════════════════╝");

        tokens.reset();
        KodekParser parser = new KodekParser(tokens);
        parser.removeErrorListeners();
        final int[] errors = {0};
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?,?> r, Object sym, int line, int col,
                                    String msg, RecognitionException e) {
                errors[0]++;
                System.err.printf("  [błąd składni] %d:%d — %s%n", line, col, msg);
            }
        });

        ParseTree tree = parser.program();
        System.out.println(prettyTree(tree, parser, 0));

        System.out.println();
        if (errors[0] == 0) {
            System.out.println("Parsowanie zakończone pomyślnie.");
        } else {
            System.out.printf("Znaleziono %d błąd(ów) składniowych.%n", errors[0]);
        }
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
