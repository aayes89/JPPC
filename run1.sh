#!/bin/sh
# Compila y ejecuta. Requiere JDK 17+ (en un JRE sin javac se usa el módulo jdk.compiler).
set -e
mkdir -p out
javac -encoding UTF-8 -d out $(find src -name '*.java') 2>/dev/null || \
  java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d out $(find src -name '*.java')
case "$1" in
  test)  java -cp out pcp.PCP src/pcp/TESTS/cpu_selftest.bin --headless --png out/selftest.png ;;
  png)   java -cp out pcp.PCP src/pcp/TESTS/fb_demo.bin --headless --png out/fb_demo.png ;;
  *)     java -cp out pcp.PCP src/pcp/TESTS/fb_demo.bin "$@" ;;   # ventana Swing
esac
