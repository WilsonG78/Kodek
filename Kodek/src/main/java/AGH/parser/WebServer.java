package AGH.parser;

import com.sun.net.httpserver.*;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Prosty serwer HTTP dla Kodek – pozwala pisać i uruchamiać programy w przeglądarce.
 *
 * Endpointy:
 *   GET  /       – edytor kodu (HTML)
 *   POST /run    – przyjmuje kod Kodek, zwraca JSON z wynikiem i wygenerowanym C
 *
 * Poprawki v2:
 *   - UUID per request: pliki tymczasowe nie kolidują przy równoczesnych żądaniach
 *   - Timeout 10s na kompilację gcc (poprzednio: brak)
 *   - Timeout 5s na uruchomienie + destroyForcibly() po przekroczeniu
 *   - Czyszczenie plików tymczasowych po każdym żądaniu
 *   - Pełne zgłaszanie błędów parsowania do klienta
 */
public class WebServer {

    /** Maksymalny czas kompilacji (ms). */
    private static final long COMPILE_TIMEOUT_MS = 10_000;

    /** Maksymalny czas wykonania programu (ms). */
    private static final long RUN_TIMEOUT_MS = 5_000;

    /** Maksymalny rozmiar kodu przesyłanego przez użytkownika (bajty). */
    private static final int MAX_CODE_BYTES = 64_000;

    public static void main(String[] args) throws Exception {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", WebServer::handleIndex);
        server.createContext("/run", WebServer::handleRun);
        server.start();
        System.out.printf("Serwer Kodek działa na http://localhost:%d%n", port);
        System.out.println("Naciśnij Ctrl+C aby zatrzymać.");
    }

    // =========================================================
    //  STRONA GŁÓWNA
    // =========================================================

    private static void handleIndex(HttpExchange ex) throws IOException {
        String html = buildHtml();
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    // =========================================================
    //  OBSŁUGA /run
    // =========================================================

    private static void handleRun(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            return;
        }

        // Ogranicz rozmiar wejścia
        byte[] rawBody = ex.getRequestBody().readNBytes(MAX_CODE_BYTES + 1);
        if (rawBody.length > MAX_CODE_BYTES) {
            sendJson(ex, 400, jsonObject("error", "Kod jest za długi (max 64 KB)."));
            return;
        }
        String body = new String(rawBody, StandardCharsets.UTF_8);

        // Wyciągnij kod i stdin z JSON {"code":..., "stdin":...}
        String kodekCode = parseJsonField(body, "code");
        String stdinData = parseJsonField(body, "stdin");

        // UUID dla tej sesji – zapobiega kolizji plików przy równoczesnych żądaniach
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Path cFile  = Path.of("/tmp/kodek_" + uid + ".c");
        Path binary = Path.of("/tmp/kodek_" + uid);

        String output = "";
        String cCode  = "";
        boolean timedOut = false;

        try {
            // ── Parsowanie ──────────────────────────────────────────
            CharStream input = CharStreams.fromString(kodekCode);
            KodekLexer lexer = new KodekLexer(input);
            lexer.removeErrorListeners();
            CommonTokenStream tokens = new CommonTokenStream(lexer);

            KodekParser parser = new KodekParser(tokens);
            parser.removeErrorListeners();

            StringBuilder parseErrors = new StringBuilder();
            parser.addErrorListener(new BaseErrorListener() {
                @Override
                public void syntaxError(Recognizer<?, ?> r, Object sym, int line, int col,
                                        String msg, RecognitionException e) {
                    parseErrors.append("Błąd składni linia ").append(line)
                            .append(", kolumna ").append(col)
                            .append(": ").append(msg).append("\n");
                }
            });

            ParseTree tree = parser.program();

            if (parseErrors.length() > 0) {
                sendJson(ex, 200, buildResultJson(parseErrors.toString(), ""));
                return;
            }

            // ── Analiza semantyczna ──────────────────────────────────
            KodekErrorHandler errorHandler = new KodekErrorHandler();
            errorHandler.check(tree);
            if (errorHandler.hasErrors()) {
                StringBuilder semErrors = new StringBuilder();
                for (KodekErrorHandler.SemanticError e : errorHandler.getErrors()) {
                    semErrors.append(e.toString()).append("\n");
                }
                sendJson(ex, 200, buildResultJson(semErrors.toString(), ""));
                return;
            }

            // ── Generowanie C ────────────────────────────────────────
            CGenerator gen = new CGenerator();
            cCode = gen.generate(tree);
            Files.writeString(cFile, cCode, StandardCharsets.UTF_8);

            // ── Kompilacja (z timeoutem) ─────────────────────────────
            Process compile = new ProcessBuilder("gcc",
                    cFile.toString(), "-o", binary.toString(), "-lm", "-Wall")
                    .redirectErrorStream(true)
                    .start();

            boolean compileFinished = compile.waitFor(COMPILE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!compileFinished) {
                compile.destroyForcibly();
                sendJson(ex, 200, buildResultJson("Błąd: kompilacja przekroczyła limit czasu.", cCode));
                return;
            }

            String compileOut = new String(compile.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (compile.exitValue() != 0) {
                output = "Błąd kompilacji:\n" + compileOut;
                sendJson(ex, 200, buildResultJson(output, cCode));
                return;
            }

            // ── Uruchomienie (z timeoutem) ───────────────────────────
            Process run = new ProcessBuilder(binary.toString())
                    .redirectErrorStream(true)
                    .start();

            // Przekaż dane wejściowe do stdin procesu
            try (OutputStream processStdin = run.getOutputStream()) {
                processStdin.write(stdinData.getBytes(StandardCharsets.UTF_8));
            }

            boolean runFinished = run.waitFor(RUN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!runFinished) {
                run.destroyForcibly();
                timedOut = true;
                output = "(program przekroczył limit czasu " + RUN_TIMEOUT_MS / 1000 + "s)";
            } else {
                output = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (output.isEmpty()) output = "(program nie wypisał żadnego wyniku)";
            }

        } catch (Exception e) {
            output = "Błąd wewnętrzny: " + e.getMessage();
        } finally {
            // Zawsze czyść pliki tymczasowe
            silentDelete(cFile);
            silentDelete(binary);
        }

        sendJson(ex, 200, buildResultJson(output, cCode));
    }

    // =========================================================
    //  POMOCNICZE HTTP / JSON
    // =========================================================

    private static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String buildResultJson(String output, String cCode) {
        return "{\"output\":" + jsonString(output) + ",\"ccode\":" + jsonString(cCode) + "}";
    }

    private static String jsonObject(String key, String value) {
        return "{\"" + key + "\":" + jsonString(value) + "}";
    }

    /** Bezpieczna serializacja stringa do JSON (obsługuje wszystkie znaki specjalne). */
    private static String jsonString(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    /** Wyciąga wartość pola z prostego JSON {"key":"value",...} */
    private static String parseJsonField(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return "";
        start += search.length();
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(++i);
                switch (next) {
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    default:   sb.append(next); break;
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void silentDelete(Path p) {
        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
    }

    // =========================================================
    //  HTML EDYTORA
    // =========================================================

    private static String buildHtml() {
        return """
<!DOCTYPE html>
<html lang="pl">
<head>
  <meta charset="UTF-8">
  <title>🧒 Kodek – nauka programowania</title>
  <style>
    *, *::before, *::after { box-sizing: border-box; }
    body {
      font-family: 'Segoe UI', Arial, sans-serif;
      max-width: 960px; margin: 32px auto; padding: 0 20px;
      background: #f0f4f8; color: #2c3e50;
    }
    h1 { margin-bottom: 4px; }
    .subtitle { color: #7f8c8d; margin-top: 0; margin-bottom: 16px; font-size: 14px; }
    textarea {
      width: 100%; height: 320px;
      font-family: 'Cascadia Code', 'Fira Code', monospace; font-size: 14px;
      padding: 12px; border-radius: 8px; border: 2px solid #3498db;
      background: #1e2d3d; color: #e8e8e8; resize: vertical;
    }
    .toolbar { display: flex; gap: 8px; margin-top: 10px; flex-wrap: wrap; }
    button {
      padding: 10px 24px; border: none; border-radius: 8px;
      font-size: 14px; font-weight: 600; cursor: pointer; transition: opacity .15s;
    }
    button:hover { opacity: .85; }
    .btn-run  { background: #27ae60; color: #fff; }
    .btn-c    { background: #2980b9; color: #fff; }
    .btn-clear{ background: #e74c3c; color: #fff; }
    .label { font-weight: 600; margin-top: 16px; margin-bottom: 4px; }
    pre {
      background: #1e2d3d; color: #ecf0f1;
      padding: 14px; border-radius: 8px; min-height: 60px;
      white-space: pre-wrap; word-break: break-word;
      font-family: 'Cascadia Code', 'Fira Code', monospace; font-size: 13px;
    }
    #ccode-section { display: none; }
    .spinner { display: none; width: 18px; height: 18px; border: 3px solid #fff; border-top-color: transparent; border-radius: 50%; animation: spin .7s linear infinite; }
    @keyframes spin { to { transform: rotate(360deg); } }
    .examples { margin-top: 24px; }
    .examples summary { cursor: pointer; font-weight: 600; color: #2980b9; }
    .examples pre { background: #ecf0f1; color: #2c3e50; cursor: pointer; }
    .examples pre:hover { background: #d5e8f5; }
    #stdin { height: 80px; border-color: #e67e22; }
  </style>
</head>
<body>
  <h1>🧒 Kodek</h1>
  <p class="subtitle">Język programowania dla dzieci – kod w języku polskim, wynik błyskawiczny</p>

  <div class="label">✏️ Napisz swój program:</div>
  <textarea id="code" placeholder="zmienna liczba x = 5&#10;piszln(x)"></textarea>

  <div class="label">⌨️ Dane wejściowe (dla czytaj):</div>
  <textarea id="stdin" placeholder="wpisz dane wejściowe, każda wartość w nowej linii..."></textarea>

  <div class="toolbar">
    <button class="btn-run" onclick="runCode()">
      ▶ Uruchom <span class="spinner" id="spin"></span>
    </button>
    <button class="btn-c" onclick="toggleC()">📄 Pokaż kod C</button>
    <button class="btn-clear" onclick="clearOutput()">🗑 Wyczyść</button>
  </div>

  <div class="label">📤 Wynik:</div>
  <pre id="output">Tu pojawi się wynik...</pre>

  <div id="ccode-section">
    <div class="label">🔧 Wygenerowany kod C:</div>
    <pre id="coutput"></pre>
  </div>

  <details class="examples">
    <summary>📖 Przykłady (kliknij, aby wczytać)</summary>

    <div class="label">Hello world:</div>
    <pre onclick="load(this)">piszln("Witaj, świecie!")</pre>

    <div class="label">Pętla for:</div>
    <pre onclick="load(this)">dla k od 1 do 5 {
    pisz(k)
    pisz(" ")
}
piszln("")</pre>

    <div class="label">Funkcja:</div>
    <pre onclick="load(this)">funkcja dodaj(liczba a, liczba b) zwraca liczba {
    zwróć a + b
}
zmienna liczba wynik = dodaj(3, 7)
piszln(wynik)</pre>

    <div class="label">Lista:</div>
    <pre onclick="load(this)">zmienna lista oceny = [5, 4, 3, 5, 2]
dla ocena w oceny {
    pisz(ocena)
    pisz(" ")
}
piszln("")
piszln(rozmiar(oceny))</pre>
  </details>

  <script>
    async function runCode() {
      const code = document.getElementById('code').value.trim();
      if (!code) { setOutput('(brak kodu)'); return; }

      document.getElementById('spin').style.display = 'inline-block';
      setOutput('⏳ Uruchamiam...');

      try {
        const resp = await fetch('/run', {
          method: 'POST',
          body: JSON.stringify({ code: code, stdin: document.getElementById('stdin').value }),
          headers: { 'Content-Type': 'application/json; charset=UTF-8' }
        });
        const data = await resp.json();
        setOutput(data.output || data.error || '(brak wyniku)');
        document.getElementById('coutput').textContent = data.ccode || '';
      } catch (err) {
        setOutput('Błąd połączenia: ' + err.message);
      } finally {
        document.getElementById('spin').style.display = 'none';
      }
    }

    function toggleC() {
      const el = document.getElementById('ccode-section');
      el.style.display = el.style.display === 'none' ? 'block' : 'none';
    }

    function clearOutput() {
      setOutput('Tu pojawi się wynik...');
      document.getElementById('coutput').textContent = '';
    }

    function setOutput(text) {
      document.getElementById('output').textContent = text;
    }

    function load(el) {
      document.getElementById('code').value = el.textContent;
      setOutput('Tu pojawi się wynik...');
    }

    // Ctrl+Enter uruchamia program
    document.addEventListener('keydown', e => {
      if (e.ctrlKey && e.key === 'Enter') runCode();
    });
  </script>
</body>
</html>
""";
    }
}