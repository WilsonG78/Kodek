# Kodek - Programming Language for Children

**Kodek** is a modern programming language designed specifically for Polish children to learn fundamental computer science concepts through an intuitive, Polish syntax that compiles to C. This combines readability with the performance of native code.

## 📌 Authors
- Filip Latawiec
- Bartosz Lech

## 🎯 Project Goals

Kodek aims to:
- Introduce children to programming in their native language
- Teach basic algorithms and data structures
- Simplify learning through natural Polish grammar
- Enable written programs to run with the efficiency of C code
- Foster computational thinking in a child-friendly environment

## ✨ Key Features

- **Polish Syntax** - Instructions written in Polish for easy understanding
- **Simple Data Structures** - Lists, dictionaries, text, numbers, and booleans
- **Input/Output** - Reading from keyboard and writing to screen
- **File Operations** - Writing to and reading from files
- **Compilation to C** - Fast and efficient program execution
- **Core Algorithms** - Loops, conditionals, functions, recursion
- **Child-Friendly Grammar** - Minimal punctuation, indent-based blocks
- **ANTLR** - parser generator
- **C++** - implementation language

---

## 📋 Lexical Analysis - Token Specification

### 1. Keywords (Reserved Words)

| Polish | English Meaning | Purpose |
|--------|-----------------|---------|
| `zmienna` | variable | Declare a variable |
| `pisz` | write | Output without newline |
| `piszln` | writeln | Output with newline |
| `czytaj` | read | Read from keyboard |
| `jeśli` | if | Conditional statement |
| `inaczej` | else | Alternative branch |
| `inaczej jeśli` | else if | Chained conditional |
| `dla` | for | Loop with counter |
| `dopóki` | while | Condition-based loop |
| `funkcja` | function | Define a function |
| `zwróć` | return | Return value from function |
| `oraz` | and | Logical AND operator |
| `lub` | or | Logical OR operator |
| `nie` | not | Logical NOT operator |
| `prawda` | true | Boolean true value |
| `fałsz` | false | Boolean false value |
| `od` | from | Start of range (for loops) |
| `do` | to | End of range (for loops) |
| `w` | in | For-each loop iteration |
| `przerwij` | break | Exit loop immediately |
| `kontynuuj` | continue | Skip to next loop iteration |
| `otwórz` | open | Open file |
| `zamknij` | close | Close file |
| `zwraca` | returns | Function return type annotation |

### 2. Types

| Type | English Meaning | C equivalent | Example |
|------|-----------------|--------------|---------|
| `liczba` | integer number | `int` | `zmienna liczba wiek = 10` |
| `ułamek` | decimal number | `double` | `zmienna ułamek pi = 3.14` |
| `tekst` | text string | `char*` | `zmienna tekst imie = "Ala"` |
| `logiczny` | boolean | `int` (0/1) | `zmienna logiczny dorosly = fałsz` |
| `lista` | list / array | `int[]` etc. | `zmienna lista oceny = [4, 5, 3]` |

### 3. Operators

| Operator | Type | Purpose | Example |
|----------|------|---------|---------|
| `=` | Assignment | Assign value to variable | `x = 5` |
| `+` | Arithmetic | Addition / String concatenation | `a + b` |
| `-` | Arithmetic | Subtraction | `a - b` |
| `*` | Arithmetic | Multiplication | `a * b` |
| `/` | Arithmetic | Division | `a / b` |
| `%` | Arithmetic | Modulo (remainder) | `a % b` |
| `^` | Arithmetic | Exponentiation | `a ^ b` |
| `==` | Comparison | Equal to | `a == b` |
| `!=` | Comparison | Not equal to | `a != b` |
| `<` | Comparison | Less than | `a < b` |
| `>` | Comparison | Greater than | `a > b` |
| `<=` | Comparison | Less than or equal | `a <= b` |
| `>=` | Comparison | Greater than or equal | `a >= b` |
| `oraz` | Logical | AND (keyword) | `x > 0 oraz x < 10` |
| `lub` | Logical | OR (keyword) | `x < 0 lub x > 10` |
| `nie` | Logical | NOT (keyword) | `nie (x == 0)` |

### 4. Literals

| Type | Format | Examples |
|------|--------|----------|
| **Integer Number** | Sequence of digits | `42`, `0`, `9999` |
| **Float Number** | Digits with decimal point | `3.14`, `2.5`, `0.001` |
| **Text String** | Double-quoted | `"Hello"`, `"Ala has a cat"` |
| **Boolean** | `prawda` or `fałsz` | `prawda`, `fałsz` |
| **List** | Comma-separated values in brackets | `[1, 2, 3]`, `["a", "b"]` |

### 5. Separators & Delimiters

| Symbol | Name | Purpose |
|--------|------|---------|
| `:` | Colon | Marks beginning of indented block |
| `(` `)` | Parentheses | Group expressions, function parameters |
| `[` `]` | Brackets | List literals and indexing |
| `,` | Comma | Separate arguments/list elements |
| `#` | Hash | Start of comment (extends to end of line) |
| Whitespace (indent) | Indentation | Define block scope (like Python) |
| Newline | Line break | Separate statements |

### 6. Built-in Functions

| Function | Parameters | Returns | Purpose |
|----------|-----------|---------|---------|
| `pierwiastek` | (number) | number | Square root |
| `wartość_bezwzględna` | (number) | number | Absolute value |
| `zaokrąglij` | (number) | number | Round to nearest integer |
| `losowa_liczba` | (min, max) | number | Random integer in range |
| `długość` | (text) | number | String length |
| `góra` | (text) | text | Convert to uppercase |
| `dół` | (text) | text | Convert to lowercase |
| `dodaj` | (list, element) | void | Add element to list |
| `rozmiar` | (list) | number | Get list size |

---

## 🎨 Syntax (Grammar in EBNF)

### Extended Backus-Naur Form (EBNF) Notation

```ebnf
Program          = { Statement }

Statement        = ( VarDecl | Assignment | IfStmt | ForLoop | WhileLoop
                   | FunctionDef | FunctionCall | ReturnStmt
                   | ReadStmt | WriteStmt | FileStmt
                   | BreakStmt | ContinueStmt ) NewLine

(* Types *)
Type             = "liczba" | "ułamek" | "tekst" | "logiczny" | "lista"

(* Variable Declaration and Assignment *)
VarDecl          = "zmienna" Type Identifier [ "=" Expression ]
Assignment       = Identifier "=" Expression
                 | ListAccess "=" Expression

(* Expressions *)
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

(* Literals and Identifiers *)
Identifier       = Letter { Letter | Digit | "_" }
Number           = Digit { Digit } [ "." Digit { Digit } ]
String           = '"' { AnyChar Except('"') } '"'
Boolean          = "prawda" | "fałsz"
ListLiteral      = "[" [ Expression { "," Expression } ] "]"
Digit            = "0" | "1" | ... | "9"
Letter           = "a".."z" | "A".."Z" | "ą".."ż"

(* Control Flow *)
IfStmt           = "jeśli" "(" Expression ")" ":" Block
                   { "inaczej" "jeśli" "(" Expression ")" ":" Block }
                   [ "inaczej" ":" Block ]

ForLoop          = "dla" Identifier "od" Expression "do" Expression ":" Block
                 | "dla" Identifier "w" Expression ":" Block

WhileLoop        = "dopóki" "(" Expression ")" ":" Block

Block            = NewLine Indent { Statement } Dedent

(* Loop Control *)
BreakStmt        = "przerwij"
ContinueStmt     = "kontynuuj"

(* Functions *)
FunctionDef      = "funkcja" Identifier "(" [ ParamList ] ")" [ "zwraca" Type ] ":" Block
ParamList        = Type Identifier { "," Type Identifier }

FunctionCall     = Identifier "(" [ ArgumentList ] ")"
ArgumentList     = Expression { "," Expression }

ReturnStmt       = "zwróć" Expression

(* I/O Operations *)
ReadStmt         = "czytaj" "(" Identifier ")"
WriteStmt        = ( "pisz" | "piszln" ) "(" Expression ")"

(* File Operations *)
FileStmt         = "otwórz" "(" Expression "," Identifier ")"
                 | "zamknij" "(" Identifier ")"

(* List/Array Access *)
ListAccess       = Identifier "[" Expression "]"

(* Structural *)
NewLine          = "\n"
Indent           = Indentation increase
Dedent           = Indentation decrease
```

---

## 📖 Syntax Examples

### 1. Variables and Assignment

```
zmienna liczba wiek
wiek = 42

zmienna tekst imie = "Ala"

zmienna ułamek pi = 3.14

zmienna logiczny dorosly = fałsz

zmienna lista oceny = [4, 5, 3, 2, 5]
```

**Compiles to C:**
```c
int wiek;
wiek = 42;

char *imie = "Ala";

double pi = 3.14;

int dorosly = 0;

int oceny[] = {4, 5, 3, 2, 5};
```

---

### 2. Conditionals

```
zmienna liczba wiek = 17

jeśli (wiek >= 18):
    pisz("Dorosly")
inaczej jeśli (wiek >= 13):
    pisz("Nastolatek")
inaczej:
    pisz("Dziecko")
```

**Compiles to C:**
```c
int wiek = 17;

if (wiek >= 18) {
    printf("Dorosly");
} else if (wiek >= 13) {
    printf("Nastolatek");
} else {
    printf("Dziecko");
}
```

---

### 3. For Loop (Fixed Range)

```
dla k od 1 do 10:
    pisz(k)
    pisz(" ")
```

**Compiles to C:**
```c
for (int k = 1; k <= 10; k++) {
    printf("%d ", k);
}
```

---

### 4. While Loop (Condition-based)

```
zmienna liczba x = 0
dopóki (x < 5):
    piszln(x)
    x = x + 1
```

**Compiles to C:**
```c
int x = 0;
while (x < 5) {
    printf("%d\n", x);
    x = x + 1;
}
```

---

### 5. For-Each Loop

```
zmienna lista oceny = [5, 4, 3, 5, 2]
dla ocena w oceny:
    piszln(ocena)
```

**Compiles to C:**
```c
int oceny[] = {5, 4, 3, 5, 2};
int oceny_len = 5;
for (int _i = 0; _i < oceny_len; _i++) {
    int ocena = oceny[_i];
    printf("%d\n", ocena);
}
```

---

### 6. Break and Continue

```
dla k od 1 do 10:
    jeśli (k == 5):
        przerwij
    jeśli (k % 2 == 0):
        kontynuuj
    piszln(k)
```

**Compiles to C:**
```c
for (int k = 1; k <= 10; k++) {
    if (k == 5) { break; }
    if (k % 2 == 0) { continue; }
    printf("%d\n", k);
}
```

---

### 7. Functions with Types

```
funkcja dodaj(liczba a, liczba b) zwraca liczba:
    zwróć a + b

funkcja przywitaj(tekst imie):
    piszln(imie)

zmienna liczba wynik = dodaj(3, 7)
przywitaj("Ala")
```

**Compiles to C:**
```c
int dodaj(int a, int b) {
    return a + b;
}

void przywitaj(char *imie) {
    printf("%s\n", imie);
}

int wynik = dodaj(3, 7);
przywitaj("Ala");
```
