# Kodek — język programowania dla dzieci

## Dane studentów

| Imię i nazwisko |
|-----------------|
| Filip Latawiec |
| Bartosz Lech |

## Dane kontaktowe

| E-mail |
|---------|
| flatawiec@student.agh.edu.pl |
| blech@student.agh.edu.pl |
---

## 📋 Założenia projektu

### Ogólne cele programu

Kodek to język programowania stworzony z myślą o polskich dzieciach, które stawiają pierwsze kroki w programowaniu. Celem jest umożliwienie nauki podstawowych pojęć informatycznych (zmienne, pętle, warunki, funkcje, struktury danych) w ojczystym języku, z minimalną ilością znaków interpunkcyjnych i intuicyjną składnią.

### Rodzaj translatora

**Kompilator** — program źródłowy zapisany w języku Kodek jest tłumaczony do kodu źródłowego w języku C, który następnie jest kompilowany przez `gcc` do natywnego pliku wykonywalnego. Wynik działania programu Kodek to działający binarny plik `.out` / `output`.

```
plik .kodek  →  [Kodek kompilator]  →  plik .c  →  [gcc]  →  plik wykonywalny  →  uruchomienie
```

### Planowany wynik działania programu

> **Kompilator języka Kodek do kodu C**

Program wejściowy (plik `.kodek`) jest analizowany i przekształcany w poprawny plik `.c`, który następnie jest automatycznie kompilowany i uruchamiany przez narzędzie.

### Język implementacji

**Java 17** — cały kompilator (lekser, parser, generator kodu) jest napisany w Javie. ANTLR4 generuje klasy leksera i parsera na podstawie gramatyki; ręcznie napisany Visitor (`CGenerator.java`) produkuje kod C.

### Sposób realizacji skanera i parsera

Skaner i parser są generowane automatycznie przez **ANTLR4** (Another Tool for Language Recognition, wersja 4.13.2) na podstawie pliku gramatyki `Kodek.g4`. ANTLR4 produkuje:
- `KodekLexer` — tokenizuje wejście (rozpoznaje słowa kluczowe, liczby, stringi, operatory)
- `KodekParser` — buduje drzewo parsowania (ParseTree / AST)
- `KodekBaseVisitor<T>` — interfejs wzorca Visitor do przechodzenia drzewa

Generacja kodu C odbywa się przez klasę `CGenerator extends KodekBaseVisitor<String>`, gdzie każda metoda `visitXxx()` zwraca fragment kodu C. Drzewo jest przechodzane rekurencyjnie metodą `visit()`.

---

## 📖 Opis tokenów

### 1. Słowa kluczowe (zarezerwowane)

| Token Kodek | Znaczenie | Zastosowanie |
|-------------|-----------|--------------|
| `zmienna` | variable | Deklaracja zmiennej |
| `pisz` | write | Wypisanie bez nowej linii |
| `piszln` | writeln | Wypisanie z nową linią |
| `czytaj` | read | Odczyt z klawiatury |
| `jeśli` | if | Instrukcja warunkowa |
| `inaczej` | else | Gałąź alternatywna |
| `inaczej jeśli` | else if | Kolejny warunek |
| `dla` | for | Pętla z licznikiem lub for-each |
| `dopóki` | while | Pętla warunkowa |
| `funkcja` | function | Definicja funkcji |
| `zwróć` | return | Wartość zwracana |
| `zwraca` | returns | Adnotacja typu zwracanego |
| `oraz` | and | Logiczne AND |
| `lub` | or | Logiczne OR |
| `nie` | not | Logiczne NOT |
| `prawda` | true | Wartość logiczna prawda |
| `fałsz` | false | Wartość logiczna fałsz |
| `od` | from | Początek zakresu pętli `dla` |
| `do` | to | Koniec zakresu pętli `dla` |
| `w` | in | Iteracja for-each |
| `przerwij` | break | Wyjście z pętli |
| `kontynuuj` | continue | Następna iteracja pętli |
| `otwórz` | open | Otwarcie pliku |
| `zamknij` | close | Zamknięcie pliku |

### 2. Typy danych

| Token | Znaczenie | Odpowiednik w C | Przykład |
|-------|-----------|-----------------|---------|
| `liczba` | liczba całkowita | `int` | `zmienna liczba wiek = 10` |
| `ułamek` | liczba zmiennoprzecinkowa | `double` | `zmienna ułamek pi = 3.14` |
| `tekst` | łańcuch znaków | `char[256]` (lokalnie) / `char*` (parametr) | `zmienna tekst imie = "Ala"` |
| `logiczny` | wartość logiczna | `int` (0/1) | `zmienna logiczny dorosly = fałsz` |
| `lista` | dynamiczna lista liczb całkowitych | `KodekLista` (struct) | `zmienna lista oceny = [4, 5, 3]` |
| `lista <typ>` | dynamiczna lista elementów danego typu | `KodekLista` / `KodekLista_u` / `KodekLista_t` | `zmienna lista tekst imiona = ["Ala", "Ola"]` |

> **Listy typowane.** Po słowie `lista` można podać typ elementu: `lista liczba`, `lista ułamek`,
> `lista tekst`, `lista logiczny`. Samo `lista` (bez typu) oznacza listę liczb całkowitych
> (zgodność wsteczna). Typ elementu jest weryfikowany semantycznie – np. dodanie tekstu do
> `lista liczba` lub przypisanie `lista tekst = [1, 2]` zgłasza błąd.
>
> ```
> zmienna lista liczba   oceny  = [5, 4, 3]
> zmienna lista ułamek   ceny   = [1.5, 2.25]
> zmienna lista tekst    imiona = ["Ala", "Ola"]
> zmienna lista logiczny flagi  = [prawda, fałsz]
> ```

### 3. Operatory

| Operator | Rodzaj | Opis | Przykład |
|----------|--------|------|---------|
| `=` | przypisanie | Przypisanie wartości | `x = 5` |
| `+` | arytmetyczny | Dodawanie | `a + b` |
| `-` | arytmetyczny | Odejmowanie | `a - b` |
| `*` | arytmetyczny | Mnożenie | `a * b` |
| `/` | arytmetyczny | Dzielenie | `a / b` |
| `%` | arytmetyczny | Reszta z dzielenia | `a % b` |
| `^` | arytmetyczny | Potęgowanie (→ `pow()`) | `a ^ b` |
| `==` | porównanie | Równość | `a == b` |
| `!=` | porównanie | Różność | `a != b` |
| `<` | porównanie | Mniejszy | `a < b` |
| `>` | porównanie | Większy | `a > b` |
| `<=` | porównanie | Mniejszy lub równy | `a <= b` |
| `>=` | porównanie | Większy lub równy | `a >= b` |
| `oraz` | logiczny | AND (słowo kluczowe) | `x > 0 oraz x < 10` |
| `lub` | logiczny | OR (słowo kluczowe) | `x < 0 lub x > 10` |
| `nie` | logiczny | NOT (słowo kluczowe) | `nie (x == 0)` |

### 4. Literały

| Rodzaj | Format tokenu | Przykłady |
|--------|--------------|-----------|
| Liczba całkowita | `DIGIT+` | `42`, `0`, `9999` |
| Liczba zmiennoprzecinkowa | `DIGIT+ '.' DIGIT+` | `3.14`, `2.5`, `0.001` |
| Łańcuch znaków | `'"' ~["\r\n]* '"'` | `"Ala"`, `"witaj świecie"` |
| Wartość logiczna | `prawda` \| `fałsz` | `prawda`, `fałsz` |
| Lista | `'[' expr (',' expr)* ']'` | `[1, 2, 3]`, `[]` |

### 5. Separatory i ograniczniki

| Symbol | Nazwa | Zastosowanie |
|--------|-------|--------------|
| `(` `)` | nawiasy okrągłe | Grupowanie wyrażeń, parametry funkcji |
| `[` `]` | nawiasy kwadratowe | Literał listy, indeksowanie |
| `{` `}` | nawiasy klamrowe | Blok instrukcji (funkcje, pętle, warunki) |
| `,` | przecinek | Separator argumentów / elementów listy |
| `#` | hash | Komentarz (do końca linii) |

### 6. Funkcje wbudowane

| Funkcja | Parametry | Zwraca | Opis |
|---------|-----------|--------|------|
| `pierwiastek` | `(liczba)` | `ułamek` | Pierwiastek kwadratowy (`sqrt`) |
| `wartość_bezwzględna` | `(liczba)` | `liczba` | Wartość bezwzględna (`abs`) |
| `zaokrąglij` | `(ułamek)` | `liczba` | Zaokrąglenie do całkowitej (`round`) |
| `losowa_liczba` | `(min, max)` | `liczba` | Losowa liczba całkowita z zakresu |
| `długość` | `(tekst)` | `liczba` | Długość łańcucha znaków (`strlen`) |
| `góra` | `(tekst)` | `tekst` | Zamiana na wielkie litery |
| `dół` | `(tekst)` | `tekst` | Zamiana na małe litery |
| `dodaj` | `(lista, elem)` | `void` | Dodanie elementu na koniec listy |
| `rozmiar` | `(lista)` | `liczba` | Liczba elementów listy |

---


## 📐 Gramatyka języka Kodek

### Gramatyka w notacji ANTLR4 (plik `Kodek.g4`)

```antlr
grammar Kodek;

program
    : statement* EOF
    ;

statement
    : simpleStmt
    | blockStmt
    ;

simpleStmt
    : varDecl
    | assignment
    | functionCall
    | returnStmt
    | readStmt
    | writeStmt
    | fileStmt
    ;

blockStmt
    : ifStmt
    | forLoop
    | whileLoop
    | functionDef
    ;

loopStatement
    : loopSimpleStmt
    | loopBlockStmt
    ;

loopSimpleStmt
    : varDecl
    | assignment
    | functionCall
    | returnStmt
    | readStmt
    | writeStmt
    | fileStmt
    | breakStmt
    | continueStmt
    ;

loopBlockStmt
    : loopIfStmt
    | forLoop
    | whileLoop
    | functionDef
    ;

typeName
    : scalarType
    | 'lista' scalarType?      // 'lista' = lista liczb; 'lista <typ>' np. 'lista tekst'
    ;

scalarType
    : 'liczba'
    | 'ułamek'
    | 'tekst'
    | 'logiczny'
    ;

varDecl
    : 'zmienna' typeName ID ('=' expression)?
    ;

assignment
    : ID '=' expression
    | ID '[' expression ']' '=' expression
    ;

expression : logicalOr ;

logicalOr  : logicalAnd ('lub'  logicalAnd)* ;
logicalAnd : negation   ('oraz' negation)*   ;

negation
    : 'nie' negation
    | comparison
    ;

comparison : arithmetic (compOp arithmetic)* ;
strictComparison : arithmetic compOp arithmetic ;

compOp : '==' | '!=' | '<' | '>' | '<=' | '>=' ;

arithmetic : term     (('+' | '-') term)*           ;
term       : factor   (('*' | '/' | '%') factor)*  ;
factor     : base     ('^' factor)?                 ;

base
    : atom
    | '(' expression ')'
    ;

atom
    : NUMBER
    | STRING
    | BOOLEAN
    | listLiteral
    | functionCall
    | listAccess
    | ID
    ;

listLiteral : '[' (expression (',' expression)*)? ']' ;
listAccess  : ID '[' expression ']'                   ;


condition
    : condAnd ('lub' condAnd)*
    ;

condAnd
    : condNeg ('oraz' condNeg)*
    ;

condNeg
    : 'nie' condNeg
    | BOOLEAN
    | ID
    | '(' condition ')'
    | strictComparison
    ;


ifStmt
    : 'jeśli' '(' condition ')' block
      ('inaczej' 'jeśli' '(' condition ')' block)*
      ('inaczej' block)?
    ;

loopIfStmt
    : 'jeśli' '(' condition ')' loopBlock
      ('inaczej' 'jeśli' '(' condition ')' loopBlock)*
      ('inaczej' loopBlock)?
    ;

forLoop
    : 'dla' ID 'od' expression 'do' expression loopBlock
    | 'dla' ID 'w' expression loopBlock
    ;

whileLoop : 'dopóki' '(' condition ')' loopBlock ;

block     : '{' statement*     '}' ;
loopBlock : '{' loopStatement* '}' ;

breakStmt    : 'przerwij'  ;
continueStmt : 'kontynuuj' ;


functionDef
    : 'funkcja' ID '(' paramList? ')' ('zwraca' typeName)? block
    ;

paramList
    : typeName ID (',' typeName ID)*
    ;

functionCall : ID '(' argumentList? ')' ;
argumentList : expression (',' expression)* ;

returnStmt : 'zwróć' expression ;


readStmt  : 'czytaj' '(' ID ')' ;
writeStmt : ('pisz' | 'piszln') '(' expression ')' ;


fileStmt
    : 'otwórz'  '(' expression ',' ID ')'
    | 'zamknij' '(' ID ')'
    ;


BOOLEAN : 'prawda' | 'fałsz' ;

ID     : LETTER (LETTER | DIGIT | '_')* ;
NUMBER : DIGIT+ ('.' DIGIT+)? ;
STRING : '"' ~["\r\n]* '"' ;

COMMENT : '#' ~[\r\n]* -> skip ;
NEWLINE : '\r'? '\n' -> skip ;

WS : [ \t]+ -> skip ;

fragment DIGIT  : [0-9] ;

fragment LETTER
    : [a-zA-Z]
    | [Ąą]
    | [Ćć]
    | [Ęę]
    | [Łł]
    | [Ńń]
    | [Óó]
    | [Śś]
    | [Źź]
    | [Żż]
    ;
```

### Gramatyka w notacji EBNF (poglądowo)

```ebnf
Program          = { Statement }

Statement        = VarDecl | Assignment | IfStmt | ForLoop | WhileLoop
                 | FunctionDef | FunctionCall | ReturnStmt
                 | ReadStmt | WriteStmt | FileStmt

Type             = ScalarType | "lista" [ ScalarType ]
ScalarType       = "liczba" | "ułamek" | "tekst" | "logiczny"

VarDecl          = "zmienna" Type Identifier [ "=" Expression ]
Assignment       = Identifier "=" Expression
                 | Identifier "[" Expression "]" "=" Expression

Condition        = CondAnd { "lub" CondAnd }
CondAnd          = CondNeg { "oraz" CondNeg }
CondNeg          = "nie" CondNeg | Boolean | Identifier
                 | "(" Condition ")" | StrictComparison
StrictComparison = Arithmetic CompOp Arithmetic

Expression       = LogicalOr
LogicalOr        = LogicalAnd { "lub" LogicalAnd }
LogicalAnd       = Negation { "oraz" Negation }
Negation         = "nie" Negation | Comparison
Comparison       = Arithmetic { CompOp Arithmetic }
CompOp           = "==" | "!=" | "<" | ">" | "<=" | ">="
Arithmetic       = Term { ("+"|"-") Term }
Term             = Factor { ("*"|"/"|"%") Factor }
Factor           = Base [ "^" Factor ]
Base             = Atom | "(" Expression ")"
Atom             = Number | String | Boolean | ListLiteral
                 | FunctionCall | ListAccess | Identifier

ListLiteral      = "[" [ Expression { "," Expression } ] "]"
ListAccess       = Identifier "[" Expression "]"

IfStmt           = "jeśli" "(" Condition ")" Block
                   { "inaczej" "jeśli" "(" Condition ")" Block }
                   [ "inaczej" Block ]

ForLoop          = "dla" Identifier "od" Expression "do" Expression LoopBlock
                 | "dla" Identifier "w" Expression LoopBlock

WhileLoop        = "dopóki" "(" Condition ")" LoopBlock

Block            = "{" { Statement } "}"

LoopBlock        = "{" { LoopStatement } "}"

LoopStatement    = Statement | BreakStmt | ContinueStmt | LoopIfStmt
                 | ForLoop | WhileLoop

LoopIfStmt       = "jeśli" "(" Condition ")" LoopBlock
                   { "inaczej" "jeśli" "(" Condition ")" LoopBlock }
                   [ "inaczej" LoopBlock ]

BreakStmt        = "przerwij"
ContinueStmt     = "kontynuuj"

FunctionDef      = "funkcja" Identifier "(" [ ParamList ] ")" [ "zwraca" Type ] Block
ParamList        = Type Identifier { "," Type Identifier }
FunctionCall     = Identifier "(" [ ArgumentList ] ")"
ArgumentList     = Expression { "," Expression }
ReturnStmt       = "zwróć" Expression

ReadStmt         = "czytaj" "(" Identifier ")"
WriteStmt        = ( "pisz" | "piszln" ) "(" Expression ")"
```

---

## 🛠 Narzędzia i zależności zewnętrzne

| Narzędzie | Wersja | Rola |
|-----------|--------|------|
| Java (JDK) | ≥ 17 | Język implementacji kompilatora |
| Maven | ≥ 3.8 | System budowania projektu |
| ANTLR4 runtime | 4.13.2 | Biblioteka wykonawcza parsera (zależność Maven) |
| ANTLR4 Maven Plugin | 4.13.2 | Generuje `KodekLexer`/`KodekParser` z `Kodek.g4` podczas `mvn compile` |
| gcc | dowolna | Kompiluje wygenerowany plik `.c` do pliku wykonywalnego |
| libm (`-lm`) | systemowa | Biblioteka matematyczna C (dla `sqrt`, `pow`, `round`) |

Wszystkie zależności Maven są pobierane automatycznie z Maven Central. Jedyną ręcznie instalowaną zależnością zewnętrzną jest `gcc` (dostępny jako pakiet systemowy: `apt install gcc` / `brew install gcc`).

---

## 🚀 Krótka instrukcja obsługi

Wszystkie polecenia wykonywane z katalogu `Kodek/` (korzeń modułu Maven):

```bash
# 1. Zbuduj projekt (generuje parser ANTLR4, kompiluje Javę)
cd Kodek && mvn package -DskipTests

# 2. Skompiluj i uruchom plik .kodek
mvn exec:java@cli -Dexec.args="src/test/resources/test.kodek"

# 3. Skompiluj z podglądem tokenów i drzewa parsowania (tryb debug)
mvn exec:java@cli -Dexec.args="src/test/resources/test.kodek --debug"

# 4. Uruchom przeglądarkowe środowisko (playground) pod http://localhost:8080
mvn exec:java@web

# 5. Uruchom wszystkie testy
mvn test

# 6. Uruchom pojedynczą klasę testową
mvn test -Dtest=CGeneratorTest

# 7. Uruchom pojedynczą metodę testową
mvn test -Dtest=CGeneratorTest#testForOdDo
```

**Potok wykonania CLI:** plik `.kodek` → lekser ANTLR4 → parser ANTLR4 → `CGenerator` (Visitor) → plik `output.c` → `gcc -lm` → `output` (uruchomiony natychmiast).

Pliki `output.c` i `output` są zapisywane obok pliku źródłowego `.kodek` i są wykluczone przez `.gitignore`.

---

## 🎨 Przykłady użycia

### 1. Zmienne i przypisanie

```
zmienna liczba wiek = 42
zmienna tekst imie = "Ala"
zmienna ułamek pi = 3.14
zmienna logiczny dorosly = fałsz
zmienna lista oceny = [4, 5, 3, 2, 5]
```

Kompiluje się do C:
```c
int wiek = 42;
char imie[256] = "Ala";
double pi = 3.14;
int dorosly = 0;
KodekLista oceny; lista_init(&oceny);
lista_dodaj(&oceny, 4); lista_dodaj(&oceny, 5); lista_dodaj(&oceny, 3);
lista_dodaj(&oceny, 2); lista_dodaj(&oceny, 5);
```

---

### 2. Instrukcja warunkowa

```
zmienna liczba wiek = 17

jeśli (wiek >= 18) {
    pisz("Dorosły")
} inaczej jeśli (wiek >= 13) {
    pisz("Nastolatek")
} inaczej {
    pisz("Dziecko")
}
```

Kompiluje się do C:
```c
int wiek = 17;
if (wiek >= 18) {
    printf("%s", "Dorosły");
} else if (wiek >= 13) {
    printf("%s", "Nastolatek");
} else {
    printf("%s", "Dziecko");
}
```

---

### 3. Pętla `dla` z zakresem

```
dla k od 1 do 10 {
    pisz(k)
    pisz(" ")
}
```

Kompiluje się do C:
```c
for (int k = 1; k <= 10; k++) {
    printf("%d", k);
    printf("%s", " ");
}
```

---

### 4. Pętla `dla` — for-each po liście

```
zmienna lista oceny = [5, 4, 3, 5, 2]
dla ocena w oceny {
    piszln(ocena)
}
```

Kompiluje się do C:
```c
KodekLista oceny; lista_init(&oceny);
lista_dodaj(&oceny, 5); lista_dodaj(&oceny, 4); lista_dodaj(&oceny, 3);
lista_dodaj(&oceny, 5); lista_dodaj(&oceny, 2);
for (int _i = 0; _i < lista_len(&oceny); _i++) {
    int ocena = lista_get(&oceny, _i);
    printf("%d\n", ocena);
}
```

---

### 5. Pętla `dopóki`

```
zmienna liczba x = 0
dopóki (x < 5) {
    piszln(x)
    x = x + 1
}
```

Kompiluje się do C:
```c
int x = 0;
while (x < 5) {
    printf("%d\n", x);
    x = x + 1;
}
```

---

### 6. Przerwij i kontynuuj

```
dla k od 1 do 10 {
    jeśli (k == 5) {
        przerwij
    }
    jeśli (k % 2 == 0) {
        kontynuuj
    }
    piszln(k)
}
```

Kompiluje się do C:
```c
for (int k = 1; k <= 10; k++) {
    if (k == 5) { break; }
    if (k % 2 == 0) { continue; }
    printf("%d\n", k);
}
```

---

### 7. Funkcje z typami

```
funkcja suma(liczba a, liczba b) zwraca liczba {
    zwróć a + b
}

funkcja przywitaj(tekst imie) {
    piszln(imie)
}

zmienna liczba wynik = suma(3, 7)
przywitaj("Ala")
```

Kompiluje się do C:
```c
int suma(int a, int b) {
    return a + b;
}

void przywitaj(char* imie) {
    printf("%s\n", imie);
}

int main() {
    int wynik = suma(3, 7);
    przywitaj("Ala");
    return 0;
}
```

---

## 🏗 Architektura projektu

```
plik .kodek
    │
    ▼
KodekLexer / KodekParser     ← generowane przez ANTLR4 z Kodek.g4
    │
    ▼  ParseTree (AST)
CGenerator                   ← ręcznie napisany Visitor (KodekBaseVisitor<String>)
    │
    ▼  String (kod C)
gcc -lm                      ← uruchamiany jako podproces
    │
    ▼  natywny plik wykonywalny → uruchomiony natychmiast
```

| Plik | Rola |
|------|------|
| `Kodek/src/main/antlr4/AGH/parser/Kodek.g4` | Gramatyka — jedyne źródło prawdy o składni języka |
| `Kodek/src/main/java/AGH/parser/CGenerator.java` | Visitor ANTLR4 — przechodzi AST i emituje kod C |
| `Kodek/src/main/java/AGH/parser/Main.java` | Punkt wejścia CLI |
| `Kodek/src/main/java/AGH/parser/WebServer.java` | Serwer HTTP — playground w przeglądarce (`POST /run` → JSON) |
| `Kodek/src/test/java/AGH/parser/CGeneratorTest.java` | Testy jednostkowe — asercje na fragmentach kodu C |
| `Kodek/src/test/java/AGH/parser/SemanticAnalyzerTest.java` | Testy analizy semantycznej (`KodekErrorHandler`) |
| `Kodek/src/test/java/AGH/parser/KodekE2ETest.java` | Testy E2E — kompilacja i uruchomienie plików `.kodek` przez gcc |
| `Kodek/src/test/java/AGH/parser/KodekTestSupport.java` | Wspólny pipeline testowy (parse → semantyka → generacja) |
| `Kodek/src/test/resources/*.kodek` | Przykładowe programy w języku Kodek |

Pliki generowane przez ANTLR4 (`KodekLexer`, `KodekParser`, Visitor, Listener) trafiają do `target/generated-sources/antlr4/` i **nie są** commitowane.

### Wewnętrzna architektura CGenerator

- **Generacja trójfazowa:** faza 0 skanuje typy zwracane funkcji; faza 1 emituje definicje funkcji przed `main()`; faza 2 emituje instrukcje najwyższego poziomu wewnątrz `main()`.
- **Stos zakresów** (`Deque<Map<String,String>>`): mapuje nazwy zmiennych → typy Kodek. Używany do inferencji typów w specyfikatorach formatu `printf`/`scanf`.
- **Śledzenie parametrów `lista`:** lokalne zmienne mają typ `"lista:<element>"` (C: `KodekLista` / `KodekLista_u` / `KodekLista_t`), a parametry funkcji — `"lista_ptr:<element>"` (wskaźnik). Helper `listRef(name)` zwraca `"&name"` dla lokalnych i `"name"` dla wskaźnikowych.
- **Środowisko uruchomieniowe list:** trzy warianty structów (`KodekLista` dla `liczba`/`logiczny`, `KodekLista_u` dla `ułamek`, `KodekLista_t` dla `tekst`) ze strategią podwajania pojemności, wstawiane do każdego wygenerowanego pliku C.

### Ograniczenia języka

| Ograniczenie | Obejście |
|-------------|----------|
| Brak ujemnych literałów (gramatyka: `NUMBER = DIGIT+`) | Pisz `0 - 1` zamiast `-1` |
| Słowa kluczowe nie mogą być nazwami zmiennych | Zob. tabela słów kluczowych |
| Listy są statycznie typowane (`lista liczba`, `lista tekst` itd.) | Użyj właściwego typu elementu; mieszanie typów w jednej liście zgłasza błąd semantyczny |
| Pętla `dla…od…do` tylko inkrementuje | Użyj `dopóki` z własnym licznikiem |
| Funkcje nie mogą być zagnieżdżone (ograniczenie C) | Definiuj wszystkie funkcje na poziomie globalnym |
