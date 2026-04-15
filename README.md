# Kodek - Programming Language for Children

**Kodek** is a modern programming language designed specifically for Polish children to learn fundamental computer science concepts through an intuitive, Polish syntax that compiles to C. This combines readability with the performance of native code.

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
| `dla` | for | Loop with counter |
| `dopóki` | while | Condition-based loop |
| `funkcja` | function | Define a function |
| `zwróć` | return | Return value from function |
| `i` | and | Logical AND operator |
| `lub` | or | Logical OR operator |
| `nie` | not | Logical NOT operator |
| `prawda` | true | Boolean true value |
| `fałsz` | false | Boolean false value |
| `od` | from | Start of range (for loops) |
| `do` | to | End of range (for loops) |
| `otworz` | open | Open file |
| `zamknij` | close | Close file |

### 2. Operators

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
| `i` | Logical | AND (word keyword) | `x > 0 i x < 10` |
| `lub` | Logical | OR (word keyword) | `x < 0 lub x > 10` |
| `nie` | Logical | NOT (word keyword) | `nie (x == 0)` |

### 3. Literals

| Type | Format | Examples |
|------|--------|----------|
| **Integer Number** | Sequence of digits | `42`, `0`, `9999` |
| **Float Number** | Digits with decimal point | `3.14`, `2.5`, `0.001` |
| **Text String** | Double-quoted | `"Hello"`, `"Ala has a cat"` |
| **Boolean** | `prawda` or `fałsz` | `prawda`, `fałsz` |

### 4. Separators & Delimiters

| Symbol | Name | Purpose |
|--------|------|---------|
| `:` | Colon | Marks beginning of indented block |
| `(` `)` | Parentheses | Group expressions, function parameters |
| `[` `]` | Brackets | Array/list indexing |
| `.` | Dot | Member access (list methods) |
| `,` | Comma | Separate arguments/list elements |
| `#` | Hash | Start of comment (extends to end of line) |
| Whitespace (indent) | Indentation | Define block scope (like Python) |
| Newline | Line break | Separate statements |

### 5. Built-in Functions

| Function | Parameters | Returns | Purpose |
|----------|-----------|---------|---------|
| `pierwiastek` | (number) | number | Square root |
| `wartość_bezwzględna` | (number) | number | Absolute value |
| `zaokrąglij` | (number) | number | Round to nearest integer |
| `losowa_liczba` | (min, max) | number | Random integer in range |
| `dlugość` | (text) | number | String length |
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
                   | ReadStmt | WriteStmt ) NewLine

(* Variable Declaration and Assignment *)
VarDecl          = "zmienna" Identifier [ "=" Expression ]
Assignment       = Identifier "=" Expression

(* Expressions *)
Expression       = LogicalOr
LogicalOr        = LogicalAnd { "lub" LogicalAnd }
LogicalAnd       = Comparison { "i" Comparison }
Comparison       = Arithmetic { CompOp Arithmetic }
CompOp           = "==" | "!=" | "<" | ">" | "<=" | ">="
Arithmetic       = Term { ("+"|"-") Term }
Term             = Factor { ("*"|"/"|"%") Factor }
Factor           = Base [ "^" Factor ]
Base             = [ "nie" ] ( Atom | "(" Expression ")" )
Atom             = Number | String | Boolean | Identifier 
                 | FunctionCall | ListAccess

(* Literals and Identifiers *)
Identifier       = Letter { Letter | Digit | "_" }
Number           = Digit { Digit } [ "." Digit { Digit } ]
String           = '"' { AnyChar Except('"') } '"'
Boolean          = "prawda" | "fałsz"
Digit            = "0" | "1" | ... | "9"
Letter           = "a".."z" | "A".."Z" | "ą".."ż"

(* Control Flow *)
IfStmt           = "jeśli" "(" Expression ")" ":" Block
                   [ "inaczej" ":" Block ]

ForLoop          = "dla" Identifier "od" Expression "do" Expression ":" Block

WhileLoop        = "dopóki" "(" Expression ")" ":" Block

Block            = NewLine Indent { Statement } Dedent

(* Functions *)
FunctionDef      = "funkcja" Identifier "(" [ ParamList ] ")" ":" Block
ParamList        = Identifier { "," Identifier }

FunctionCall     = Identifier "(" [ ArgumentList ] ")"
ArgumentList     = Expression { "," Expression }

ReturnStmt       = "zwróć" Expression

(* I/O Operations *)
ReadStmt         = "czytaj" "(" Identifier ")"
WriteStmt        = ( "pisz" | "piszln" ) "(" Expression ")"

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
zmienna liczba
liczba = 42

zmienna imie = "Ala"

zmienna lista = [1, 2, 3]
```

**Compiles to C:**
```c
int liczba;
liczba = 42;

char *imie = "Ala";

int lista[] = {1, 2, 3};
```

---

### 2. Conditionals

```
jeśli (wiek >= 18):
    pisz("Adult")
inaczej:
    pisz("Child")
```

**Compiles to C:**
```c
if (wiek >= 18) {
    printf("Adult\n");
} else {
    printf("Child\n");
}
```

---

### 3. For Loop (Fixed Range)

```
dla i od 1 do 10:
    pisz(i)
    pisz(" ")
```

**Compiles to C:**
```c
for (int i = 1; i <= 10; i++) {
    printf("%d ", i);
}
```

---

### 4. While Loop (Condition-based)

```
zmienna x = 0
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
