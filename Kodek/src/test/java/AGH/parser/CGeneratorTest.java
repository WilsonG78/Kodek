package AGH.parser;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testy jednostkowe CGenerator – sprawdzają wygenerowany kod C.
 *
 * Każdy test parsuje fragment kodu Kodek i weryfikuje, że wygenerowany
 * kod C zawiera oczekiwane fragmenty (substring matching).
 */
class CGeneratorTest {

    // =========================================================
    //  POMOCNICZE
    // =========================================================

    /** Parsuje kod Kodek i zwraca wygenerowany kod C. */
    private String generate(String kodekCode) {
        CharStream input = CharStreams.fromString(kodekCode);
        KodekLexer lexer = new KodekLexer(input);
        lexer.removeErrorListeners();
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        KodekParser parser = new KodekParser(tokens);
        parser.removeErrorListeners();
        ParseTree tree = parser.program();

        CGenerator gen = new CGenerator();
        return gen.generate(tree);
    }

    /** Sprawdza, że kod C zawiera fragment (ignorując białe znaki na początku/końcu linii). */
    private void assertContains(String cCode, String fragment) {
        assertTrue(cCode.contains(fragment),
            "Oczekiwano fragmentu: [" + fragment + "]\n\nWygenerowany kod:\n" + cCode);
    }

    // =========================================================
    //  STRUKTURA PLIKU
    // =========================================================

    @Test
    @DisplayName("Wygenerowany kod zawiera wymagane nagłówki C")
    void testHeaders() {
        String code = generate("piszln(42)");
        assertContains(code, "#include <stdio.h>");
        assertContains(code, "#include <math.h>");
        assertContains(code, "#include <string.h>");
        assertContains(code, "#include <time.h>");
    }

    @Test
    @DisplayName("Wygenerowany kod zawiera funkcję main()")
    void testMainFunction() {
        String code = generate("piszln(1)");
        assertContains(code, "int main()");
        assertContains(code, "return 0;");
    }

    // =========================================================
    //  DEKLARACJE ZMIENNYCH
    // =========================================================

    @Test
    @DisplayName("liczba → int")
    void testVarDeclLiczba() {
        String code = generate("zmienna liczba x = 42");
        assertContains(code, "int x = 42");
    }

    @Test
    @DisplayName("ułamek → double")
    void testVarDeclUlamek() {
        String code = generate("zmienna ułamek pi = 3.14");
        assertContains(code, "double pi = 3.14");
    }

    @Test
    @DisplayName("logiczny → int (prawda=1, fałsz=0)")
    void testVarDeclLogiczny() {
        String code = generate("zmienna logiczny flaga = prawda");
        assertContains(code, "int flaga = 1");
    }

    @Test
    @DisplayName("tekst → char[256] (nie char*)")
    void testVarDeclTekst() {
        String code = generate("zmienna tekst imie = \"Ala\"");
        assertContains(code, "char imie[256]");
        assertContains(code, "\"Ala\"");
    }

    @Test
    @DisplayName("lista ze stałym rozmiarem (bez split(','))")
    void testVarDeclLista() {
        String code = generate("zmienna lista oceny = [5, 4, 3]");
        assertContains(code, "int oceny[]");
        assertContains(code, "{5, 4, 3}");
        assertContains(code, "int oceny_len = 3");
    }

    @Test
    @DisplayName("lista bez inicjalizatora → tablica o stałym rozmiarze")
    void testVarDeclListaEmpty() {
        String code = generate("zmienna lista buf");
        assertContains(code, "int buf[");
        assertContains(code, "int buf_len = 0");
    }

    // =========================================================
    //  PRZYPISANIE
    // =========================================================

    @Test
    @DisplayName("przypisanie do liczby → =")
    void testAssignLiczba() {
        String code = generate("zmienna liczba x\nx = 10");
        assertContains(code, "x = 10");
    }

    @Test
    @DisplayName("przypisanie do tekstu → strcpy")
    void testAssignTekst() {
        String code = generate("zmienna tekst s = \"a\"\ns = \"b\"");
        assertContains(code, "strcpy(s, \"b\")");
    }

    @Test
    @DisplayName("przypisanie do listy indeksowane → []")
    void testAssignListIndex() {
        String code = generate("zmienna lista tab = [1,2,3]\ntab[0] = 99");
        assertContains(code, "tab[0] = 99");
    }

    // =========================================================
    //  PISZ / PISZLN
    // =========================================================

    @Test
    @DisplayName("piszln liczba → printf(\"%d\\n\",...)")
    void testPiszlnLiczba() {
        String code = generate("zmienna liczba n = 5\npiszln(n)");
        assertContains(code, "printf(\"%d\\n\", n)");
    }

    @Test
    @DisplayName("piszln tekst → printf(\"%s\\n\",...)")
    void testPiszlnTekst() {
        String code = generate("piszln(\"witaj\")");
        assertContains(code, "printf(\"%s\\n\", \"witaj\")");
    }

    @Test
    @DisplayName("piszln ułamek → printf(\"%f\\n\",...)")
    void testPiszlnUlamek() {
        String code = generate("piszln(3.14)");
        assertContains(code, "printf(\"%f\\n\", 3.14)");
    }

    @Test
    @DisplayName("pisz (bez nowej linii) → printf bez \\n")
    void testPiszBezNowejLinii() {
        String code = generate("pisz(42)");
        assertContains(code, "printf(\"%d\", 42)");
        assertFalse(code.contains("printf(\"%d\\n\""), "pisz nie powinno dodawać \\n");
    }

    // =========================================================
    //  CZYTAJ
    // =========================================================

    @Test
    @DisplayName("czytaj liczba → scanf z &")
    void testCzytajLiczba() {
        String code = generate("zmienna liczba x\nczytaj(x)");
        assertContains(code, "scanf(\"%d\", &x)");
    }

    @Test
    @DisplayName("czytaj tekst → scanf bez & (char[])")
    void testCzytajTekst() {
        String code = generate("zmienna tekst s\nczytaj(s)");
        // Powinno być scanf z "%255s" bez & (tablica się nie potrzebuje &)
        assertContains(code, "scanf(\"%255s\", s)");
        assertFalse(code.contains("scanf(\"%255s\", &s)"), "tekst nie powinien mieć & w scanf");
    }

    // =========================================================
    //  INSTRUKCJE WARUNKOWE
    // =========================================================

    @Test
    @DisplayName("jeśli-inaczej → if-else")
    void testIfElse() {
        String code = generate("""
                zmienna liczba x = 5
                jeśli (x > 0) {
                    piszln(x)
                } inaczej {
                    piszln(0)
                }
                """);
        assertContains(code, "if (x > 0)");
        assertContains(code, "else");
    }

    @Test
    @DisplayName("jeśli-inaczej jeśli → if-else if")
    void testElseIf() {
        String code = generate("""
                zmienna liczba x = 5
                jeśli (x > 10) {
                    piszln(1)
                } inaczej jeśli (x > 0) {
                    piszln(2)
                } inaczej {
                    piszln(3)
                }
                """);
        assertContains(code, "if (x > 10)");
        assertContains(code, "else if (x > 0)");
        assertContains(code, "else");
    }

    // =========================================================
    //  PĘTLE
    // =========================================================

    @Test
    @DisplayName("dla od-do → for z <= i inkrementacją")
    void testForOdDo() {
        String code = generate("""
                dla k od 1 do 5 {
                    pisz(k)
                }
                """);
        assertContains(code, "for (int k = 1; k <= 5; k++)");
    }

    @Test
    @DisplayName("dla w (for-each) → for z _i i elementem")
    void testForEach() {
        String code = generate("""
                zmienna lista oceny = [5, 4, 3]
                dla ocena w oceny {
                    pisz(ocena)
                }
                """);
        assertContains(code, "for (int _i = 0; _i < oceny_len; _i++)");
        assertContains(code, "int ocena = oceny[_i]");
    }

    @Test
    @DisplayName("dopóki → while")
    void testWhile() {
        String code = generate("""
                zmienna liczba x = 0
                dopóki (x < 5) {
                    x = x + 1
                }
                """);
        assertContains(code, "while (x < 5)");
    }

    @Test
    @DisplayName("przerwij → break, kontynuuj → continue")
    void testBreakContinue() {
        String code = generate("""
                dla k od 1 do 10 {
                    jeśli (k == 5) {
                        przerwij
                    }
                    jeśli (k == 3) {
                        kontynuuj
                    }
                }
                """);
        assertContains(code, "break");
        assertContains(code, "continue");
    }

    // =========================================================
    //  FUNKCJE – KRYTYCZNA NAPRAWA: poza main()
    // =========================================================

    @Test
    @DisplayName("definicja funkcji jest PRZED main() [kluczowa naprawa]")
    void testFunctionBeforeMain() {
        String code = generate("""
                funkcja dodaj(liczba a, liczba b) zwraca liczba {
                    zwróć a + b
                }
                piszln(dodaj(3, 7))
                """);

        int funcIdx = code.indexOf("int dodaj(");
        int mainIdx = code.indexOf("int main()");
        assertTrue(funcIdx >= 0, "Definicja funkcji dodaj nie znaleziona w kodzie C");
        assertTrue(mainIdx >= 0, "int main() nie znaleziony");
        assertTrue(funcIdx < mainIdx,
            "Funkcja powinna być PRZED main()!\nFunkcja na pozycji: " + funcIdx
            + ", main na pozycji: " + mainIdx + "\n\n" + code);
    }

    @Test
    @DisplayName("funkcja void bez zwraca")
    void testFunctionVoid() {
        String code = generate("""
                funkcja przywitaj(tekst imie) {
                    piszln(imie)
                }
                """);
        assertContains(code, "void przywitaj(char* imie)");
    }

    @Test
    @DisplayName("funkcja ze zwraca → typ C")
    void testFunctionWithReturn() {
        String code = generate("""
                funkcja kwadrat(liczba n) zwraca liczba {
                    zwróć n * n
                }
                """);
        assertContains(code, "int kwadrat(int n)");
        assertContains(code, "return n * n");
    }

    @Test
    @DisplayName("wywołanie funkcji wewnątrz wyrażenia")
    void testFunctionCallInExpr() {
        String code = generate("""
                funkcja dwa() zwraca liczba {
                    zwróć 2
                }
                zmienna liczba x = dwa()
                """);
        assertContains(code, "int x = dwa()");
    }

    // =========================================================
    //  WBUDOWANE FUNKCJE
    // =========================================================

    @Test
    @DisplayName("pierwiastek → sqrt()")
    void testPierwiastek() {
        String code = generate("piszln(pierwiastek(16))");
        assertContains(code, "sqrt(16)");
    }

    @Test
    @DisplayName("zaokrąglij → round()")
    void testZaokraglij() {
        String code = generate("piszln(zaokrąglij(3.7))");
        assertContains(code, "round(3.7)");
    }

    @Test
    @DisplayName("losowa_liczba → rand() % (...)")
    void testLosowaLiczba() {
        String code = generate("zmienna liczba r = losowa_liczba(1, 10)");
        assertContains(code, "rand()");
    }

    @Test
    @DisplayName("długość → strlen()")
    void testDlugosc() {
        String code = generate("piszln(długość(\"abc\"))");
        assertContains(code, "strlen(\"abc\")");
    }

    @Test
    @DisplayName("góra → _kodek_gora() (nie null/break)")
    void testGora() {
        String code = generate("piszln(góra(\"test\"))");
        assertContains(code, "_kodek_gora(\"test\")");
        // Pomocnicza funkcja powinna być wygenerowana
        assertContains(code, "char* _kodek_gora");
    }

    @Test
    @DisplayName("dół → _kodek_dol() (nie null/break)")
    void testDol() {
        String code = generate("piszln(dół(\"TEST\"))");
        assertContains(code, "_kodek_dol(\"TEST\")");
        assertContains(code, "char* _kodek_dol");
    }

    @Test
    @DisplayName("rozmiar → listName_len")
    void testRozmiar() {
        String code = generate("zmienna lista tab = [1,2,3]\npiszln(rozmiar(tab))");
        assertContains(code, "tab_len");
    }

    @Test
    @DisplayName("dodaj → listName[listName_len++] = elem")
    void testDodaj() {
        String code = generate("""
                zmienna lista buf
                dodaj(buf, 42)
                """);
        assertContains(code, "buf[buf_len++] = 42");
    }

    // =========================================================
    //  DZIAŁANIA ARYTMETYCZNE
    // =========================================================

    @Test
    @DisplayName("potęgowanie → pow()")
    void testPow() {
        String code = generate("piszln(2 ^ 8)");
        assertContains(code, "pow(2, 8)");
    }

    @Test
    @DisplayName("modulo → %")
    void testModulo() {
        String code = generate("zmienna liczba r = 10 % 3");
        assertContains(code, "10 % 3");
    }

    // =========================================================
    //  OPERATORY LOGICZNE W WYRAŻENIACH
    // =========================================================

    @Test
    @DisplayName("lub w wyrażeniu → ||")
    void testLogicalOrExpr() {
        String code = generate("zmienna logiczny b = prawda lub fałsz");
        assertContains(code, "||");
    }

    @Test
    @DisplayName("oraz w wyrażeniu → &&")
    void testLogicalAndExpr() {
        String code = generate("zmienna logiczny b = prawda oraz fałsz");
        assertContains(code, "&&");
    }

    // =========================================================
    //  INICJALIZACJA RAND
    // =========================================================

    @Test
    @DisplayName("main() zawiera srand() dla losowych liczb")
    void testSrand() {
        String code = generate("piszln(1)");
        assertContains(code, "srand(");
    }

    // =========================================================
    //  TYPY NIEINFERENCYJNE
    // =========================================================

    @Test
    @DisplayName("inferencja typu: ułamek w wyrażeniu arytmetycznym")
    void testTypeInferUlamek() {
        String code = generate("piszln(1.5 + 2.5)");
        // Typ = ułamek → format %f
        assertContains(code, "printf(\"%f");
    }

    @Test
    @DisplayName("inferencja typu: logiczny (porównanie)")
    void testTypeInferLogiczny() {
        String code = generate("zmienna liczba x = 5\npiszln(x == 5)");
        assertContains(code, "printf(\"%d");
        assertContains(code, "x == 5");
    }
}
