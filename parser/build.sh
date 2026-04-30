#!/bin/bash
set -e

ANTLR_JAR="antlr-4.13.2-complete.jar"
ANTLR_URL="https://www.antlr.org/download/$ANTLR_JAR"
GEN="gen"

cd "$(dirname "$0")"

# 1. Pobierz ANTLR jeśli nie ma
if [ ! -f "$ANTLR_JAR" ]; then
    echo "→ Pobieranie ANTLR 4.13.2..."
    curl -L -o "$ANTLR_JAR" "$ANTLR_URL"
fi

# 2. Generuj parser z gramatyki
echo "→ Generowanie parsera z Kodek.g4..."
mkdir -p "$GEN"
java -jar "$ANTLR_JAR" -Dlanguage=Java -encoding UTF-8 -o "$GEN" Kodek.g4

# 3. Kompiluj
echo "→ Kompilacja..."
javac -encoding UTF-8 -cp "$ANTLR_JAR" -d "$GEN" "$GEN"/*.java Main.java

# 4. Parsuj plik testowy
echo "→ Parsowanie test.kodek..."
echo ""
java -Dfile.encoding=UTF-8 -cp "$ANTLR_JAR:$GEN" Main "${1:-test.kodek}"
