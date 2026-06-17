package AGH.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Testy end-to-end: plik .kodek → analiza semantyczna → kod C → gcc → uruchomienie.
 */
class KodekE2ETest {

    private static final long COMPILE_TIMEOUT_SEC = 30;
    private static final long RUN_TIMEOUT_SEC = 10;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "test.kodek",
            "funkcja.kodek",
            "lista.kodek",
            "binary_search.kodek",
            "bfs.kodek",
            "dfs.kodek",
            "gasienicowy.kodek",
            "heap_sort.kodek",
            "merge_sort.kodek",
            "quick_sort.kodek",
            "kruskal.kodek",
            "prim.kodek"
    })
    @DisplayName("plik .kodek kompiluje się i uruchamia bez błędu")
    void compileAndRunExample(String resourceName, @TempDir Path tempDir) throws Exception {
        assumeTrue(isGccAvailable(), "gcc nie jest dostępny w PATH – pomijam test E2E");

        String source = loadResource(resourceName);

        List<KodekErrorHandler.SemanticError> semanticErrors = KodekTestSupport.analyze(source);
        assertTrue(semanticErrors.isEmpty(),
                () -> "Błędy semantyczne w " + resourceName + ":\n"
                        + semanticErrors.stream()
                        .map(KodekErrorHandler.SemanticError::toString)
                        .collect(Collectors.joining("\n")));

        String cCode = new CGenerator().generate(KodekTestSupport.parse(source));

        String baseName = resourceName.replace(".kodek", "");
        Path cFile = tempDir.resolve(baseName + ".c");
        Path binary = tempDir.resolve(baseName);

        Files.writeString(cFile, cCode, StandardCharsets.UTF_8);

        int compileExit = runProcess(
                new ProcessBuilder("gcc", cFile.toString(), "-o", binary.toString(), "-lm", "-Wall"),
                COMPILE_TIMEOUT_SEC);
        assertEquals(0, compileExit,
                () -> "gcc zakończył się błędem dla " + resourceName);

        int runExit = runProcess(new ProcessBuilder(binary.toString()), RUN_TIMEOUT_SEC);
        assertEquals(0, runExit,
                () -> "Program " + resourceName + " zakończył się kodem " + runExit);
    }

    private static String loadResource(String name) throws IOException {
        try (InputStream in = KodekE2ETest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, "Brak zasobu testowego: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static boolean isGccAvailable() {
        try {
            Process p = new ProcessBuilder("gcc", "--version")
                    .redirectErrorStream(true)
                    .start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static int runProcess(ProcessBuilder pb, long timeoutSec) throws Exception {
        Process process = pb.redirectErrorStream(true).start();
        boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            fail("Proces przekroczył limit " + timeoutSec + "s: " + pb.command());
        }
        return process.exitValue();
    }
}
