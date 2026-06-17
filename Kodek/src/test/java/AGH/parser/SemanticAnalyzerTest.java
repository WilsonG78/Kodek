package AGH.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testy analizy semantycznej ({@link KodekErrorHandler}).
 */
class SemanticAnalyzerTest {

    private static boolean hasErrorMatching(String code, String fragment) {
        return KodekTestSupport.analyze(code).stream()
                .anyMatch(e -> e.message.contains(fragment));
    }

    private static boolean hasWarningMatching(String code, String fragment) {
        return KodekTestSupport.analyzeAll(code).warnings.stream()
                .anyMatch(w -> w.message.contains(fragment));
    }

    private static List<KodekErrorHandler.SemanticError> errors(String code) {
        return KodekTestSupport.analyze(code);
    }

    @Test
    @DisplayName("poprawny kod nie zgłasza błędów semantycznych")
    void validCodeHasNoErrors() {
        assertTrue(errors("""
                zmienna liczba x = 5
                piszln(x)
                """).isEmpty());
    }

    @Test
    @DisplayName("niezadeklarowana zmienna")
    void undeclaredVariable() {
        assertTrue(hasErrorMatching("piszln(x)", "niezadeklarowanej zmiennej 'x'"));
    }

    @Test
    @DisplayName("ponowna deklaracja w tym samym zakresie")
    void redeclaredVariable() {
        assertTrue(hasErrorMatching("""
                zmienna liczba x = 1
                zmienna liczba x = 2
                """, "już zadeklarowana w tym zakresie"));
    }

    @Test
    @DisplayName("wywołanie niezdefiniowanej funkcji")
    void undefinedFunction() {
        assertTrue(hasErrorMatching("nieistniejaca()", "niezdefiniowanej funkcji 'nieistniejaca'"));
    }

    @Test
    @DisplayName("zła liczba argumentów")
    void wrongArgumentCount() {
        assertTrue(hasErrorMatching("""
                funkcja f(liczba a) zwraca liczba {
                    zwróć a
                }
                piszln(f(1, 2))
                """, "oczekuje 1 argument(ów), podano 2"));
    }

    @Test
    @DisplayName("niezgodność typów przy deklaracji")
    void typeMismatchOnDecl() {
        assertTrue(hasErrorMatching(
                "zmienna liczba x = \"tekst\"",
                "niezgodność typów"));
    }

    @Test
    @DisplayName("niezgodność typów przy przypisaniu")
    void typeMismatchOnAssign() {
        assertTrue(hasErrorMatching("""
                zmienna liczba x = 1
                x = \"tekst\"
                """, "niezgodność typów przy przypisaniu"));
    }

    @Test
    @DisplayName("indeksowanie zmiennej niebędącej listą")
    void indexOnNonList() {
        assertTrue(hasErrorMatching("""
                zmienna liczba x = 1
                piszln(x[0])
                """, "nie jest listą"));
    }

    @Test
    @DisplayName("niezgodność typu elementu listy")
    void listElementTypeMismatch() {
        assertTrue(hasErrorMatching(
                "zmienna lista liczba t = [\"a\"]",
                "niezgodność typów"));
    }

    @Test
    @DisplayName("zwróć poza ciałem funkcji")
    void returnOutsideFunction() {
        assertTrue(hasErrorMatching("zwróć 1", "poza ciałem funkcji"));
    }

    @Test
    @DisplayName("funkcja void zwraca wartość")
    void voidFunctionReturnsValue() {
        assertTrue(hasErrorMatching("""
                funkcja f() {
                    zwróć 1
                }
                """, "jest void – nie powinna zwracać wartości"));
    }

    @Test
    @DisplayName("funkcja z typem zwracanym bez instrukcji zwróć")
    void nonVoidFunctionMissingReturn() {
        assertTrue(hasErrorMatching("""
                funkcja f() zwraca liczba {
                    piszln(1)
                }
                """, "brakuje instrukcji 'zwróć'"));
    }

    @Test
    @DisplayName("redeklaracja wbudowanej funkcji")
    void redeclareBuiltin() {
        assertTrue(hasErrorMatching("""
                funkcja rozmiar(liczba x) zwraca liczba {
                    zwróć x
                }
                """, "redeklaracja wbudowanej funkcji 'rozmiar'"));
    }

    @Test
    @DisplayName("zmienna kolidująca z wbudowaną funkcją")
    void variableShadowsBuiltin() {
        assertTrue(hasErrorMatching(
                "zmienna liczba rozmiar = 1",
                "koliduje z wbudowaną funkcją"));
    }

    @Test
    @DisplayName("dzielenie przez zero (literał)")
    void divisionByZeroLiteral() {
        assertTrue(hasErrorMatching("zmienna liczba x = 10 / 0", "dzielenie przez zero"));
    }

    @Test
    @DisplayName("operator arytmetyczny na tekście")
    void arithmeticOnText() {
        assertTrue(hasErrorMatching("""
                zmienna tekst s = \"a\"
                piszln(s + s)
                """, "operator arytmetyczny (+/-) użyty na wartości typu 'tekst'"));
    }

    @Test
    @DisplayName("poprawny kod z listą typowaną nie zgłasza błędów")
    void typedListValid() {
        assertTrue(errors("""
                zmienna lista tekst imiona = [\"Ala\", \"Ola\"]
                piszln(imiona[0])
                """).isEmpty());
    }

    @Test
    @DisplayName("redeklaracja funkcji użytkownika")
    void redeclaredUserFunction() {
        assertTrue(hasErrorMatching("""
                funkcja f() zwraca liczba {
                    zwróć 1
                }
                funkcja f() zwraca liczba {
                    zwróć 2
                }
                """, "jest już zdefiniowana"));
    }

    @Test
    @DisplayName("dodaj() z elementem złego typu")
    void dodajWrongElementType() {
        assertTrue(hasErrorMatching("""
                zmienna lista tekst t = [\"a\"]
                dodaj(t, 42)
                """, "dodaj(): lista przechowuje"));
    }

    @Test
    @DisplayName("rozmiar() na skalarze")
    void rozmiarOnScalar() {
        assertTrue(hasErrorMatching("""
                zmienna liczba x = 5
                piszln(rozmiar(x))
                """, "rozmiar() oczekuje listy"));
    }

    @Test
    @DisplayName("losowa_liczba() z min > max")
    void losowaLiczbaInvalidRange() {
        assertTrue(hasErrorMatching(
                "zmienna liczba r = losowa_liczba(10, 5)",
                "minimum (10) jest większe niż maksimum"));
    }

    @Test
    @DisplayName("czytaj() na liście")
    void czytajOnList() {
        assertTrue(hasErrorMatching("""
                zmienna lista liczba t
                czytaj(t)
                """, "dozwolone są tylko: liczba, ułamek, tekst"));
    }

    @Test
    @DisplayName("porównanie tekst z liczbą")
    void compareTextAndNumber() {
        assertTrue(hasErrorMatching(
                "jeśli (\"abc\" == 5) { piszln(1) }",
                "nie ma sensu"));
    }

    @Test
    @DisplayName("zamknij() niezadeklarowanego uchwytu")
    void closeUndeclaredFile() {
        assertTrue(hasErrorMatching("zamknij(plik)", "niezadeklarowany uchwyt pliku"));
    }

    @Test
    @DisplayName("otwórz() ze ścieżką nie-tekstową")
    void openWithNonTextPath() {
        assertTrue(hasErrorMatching(
                "otwórz(42, f)",
                "ścieżka pliku musi być typu 'tekst'"));
    }

    @Test
    @DisplayName("pusty zakres pętli dla – ostrzeżenie")
    void emptyForRangeWarning() {
        assertTrue(hasWarningMatching("""
                dla k od 10 do 5 {
                    piszln(k)
                }
                """, "pusty zakres"));
    }

    @Test
    @DisplayName("ignorowany wynik funkcji – ostrzeżenie")
    void ignoredReturnWarning() {
        assertTrue(hasWarningMatching("""
                funkcja f() zwraca liczba {
                    zwróć 1
                }
                f()
                """, "jest ignorowany"));
    }

    @Test
    @DisplayName("indeks poza zakresem listy – ostrzeżenie")
    void indexOutOfBoundsWarning() {
        assertTrue(hasWarningMatching("""
                zmienna lista liczba t = [1, 2, 3]
                piszln(t[5])
                """, "poza zakresem"));
    }
}
