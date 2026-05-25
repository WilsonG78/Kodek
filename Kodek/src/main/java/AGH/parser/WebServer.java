package AGH.parser;

import com.sun.net.httpserver.*;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class WebServer {

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", WebServer::handleIndex);
        server.createContext("/run", WebServer::handleRun);
        server.start();
        System.out.println("Serwer działa na http://localhost:8080");
    }

    private static void handleIndex(HttpExchange ex) throws IOException {
        String html = """
<!DOCTYPE html>
<html lang="pl">
<head>
  <meta charset="UTF-8">
  <title>Kodek - nauka programowania</title>
  <style>
    body { font-family: Arial, sans-serif; max-width: 900px; margin: 40px auto; padding: 0 20px; background: #f0f4f8; }
    h1 { color: #2c3e50; }
    textarea { width: 100%; height: 300px; font-family: monospace; font-size: 14px; padding: 10px; border-radius: 8px; border: 2px solid #3498db; box-sizing: border-box; }
    button { margin-top: 10px; padding: 12px 30px; background: #3498db; color: white; border: none; border-radius: 8px; font-size: 16px; cursor: pointer; }
    button:hover { background: #2980b9; }
    pre { background: #2c3e50; color: #ecf0f1; padding: 15px; border-radius: 8px; min-height: 80px; white-space: pre-wrap; }
    .label { font-weight: bold; margin-top: 15px; color: #2c3e50; }
    #ccode { display: none; }
    .toggle { background: #27ae60; margin-left: 10px; padding: 8px 16px; font-size: 13px; }
  </style>
</head>
<body>
  <h1>🧒 Kodek - nauka programowania</h1>
  <div class="label">Napisz swój program:</div>
  <textarea id="code" placeholder="zmienna liczba x = 5&#10;piszln(x)"></textarea>
  <br>
  <button onclick="run()">▶ Uruchom</button>
  <button class="toggle" onclick="toggleC()">Pokaż kod C</button>
  <div class="label">Wynik:</div>
  <pre id="output">Tu pojawi się wynik...</pre>
  <div id="ccode">
    <div class="label">Wygenerowany kod C:</div>
    <pre id="coutput"></pre>
  </div>
  <script>
    async function run() {
      const code = document.getElementById('code').value;
      document.getElementById('output').textContent = 'Uruchamiam...';
      const resp = await fetch('/run', { method: 'POST', body: code });
      const data = await resp.json();
      document.getElementById('output').textContent = data.output || '(brak wyniku)';
      document.getElementById('coutput').textContent = data.ccode || '';
    }
    function toggleC() {
      const el = document.getElementById('ccode');
      el.style.display = el.style.display === 'none' ? 'block' : 'none';
    }
  </script>
</body>
</html>
""";
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }

    private static void handleRun(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            return;
        }

        String kodekCode = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        String output = "";
        String cCode = "";
        try {
            // Parsowanie
            CharStream input = CharStreams.fromString(kodekCode);
            KodekLexer lexer = new KodekLexer(input);
            lexer.removeErrorListeners();
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            KodekParser parser = new KodekParser(tokens);
            parser.removeErrorListeners();

            StringBuilder parseErrors = new StringBuilder();
            parser.addErrorListener(new BaseErrorListener() {
                @Override
                public void syntaxError(Recognizer<?,?> r, Object sym, int line, int col,
                                        String msg, RecognitionException e) {
                    parseErrors.append("Błąd składni linia ").append(line).append(": ").append(msg).append("\n");
                }
            });

            ParseTree tree = parser.program();

            if (parseErrors.length() > 0) {
                output = parseErrors.toString();
            } else {
                // Generowanie C
                CGenerator gen = new CGenerator();
                cCode = gen.generate(tree);

                // Zapis do pliku
                Path cFile = Path.of("/tmp/kodek_output.c");
                Path binary = Path.of("/tmp/kodek_output");
                Files.writeString(cFile, cCode);

                // Kompilacja
                Process compile = new ProcessBuilder("gcc", cFile.toString(), "-o", binary.toString(), "-lm")
                        .redirectErrorStream(true).start();
                String compileOut = new String(compile.getInputStream().readAllBytes());
                compile.waitFor();

                if (!compileOut.isEmpty()) {
                    output = "Błąd kompilacji:\n" + compileOut;
                } else {
                    // Uruchomienie
                    Process run = new ProcessBuilder(binary.toString())
                            .redirectErrorStream(true).start();
                    run.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                    output = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    if (output.isEmpty()) output = "(program nie wypisał nic)";
                }
            }
        } catch (Exception e) {
            output = "Błąd: " + e.getMessage();
        }

        String json = "{\"output\":" + jsonString(output) + ",\"ccode\":" + jsonString(cCode) + "}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }
}