#!/bin/bash
set -e

ANTLR_JAR="antlr-4.13.2-complete.jar"
ANTLR_URL="https://www.antlr.org/download/$ANTLR_JAR"
GEN="gen"

cd "$(dirname "$0")"

if [ ! -f "$ANTLR_JAR" ]; then
    curl -L -o "$ANTLR_JAR" "$ANTLR_URL"
fi


mkdir -p "$GEN"
java -jar "$ANTLR_JAR" -Dlanguage=Java -encoding UTF-8 -o "$GEN" Kodek.g4



javac -encoding UTF-8 -cp "$ANTLR_JAR" -d "$GEN" "$GEN"/*.java Main.java

java -Dfile.encoding=UTF-8 -cp "$ANTLR_JAR:$GEN" Main "${1:-test.kodek}"
