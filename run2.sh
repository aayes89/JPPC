#!/bin/sh
# JPPC: compila y ejecuta. Requiere JDK 17+ (sirve también un JRE con el módulo jdk.compiler).
#   ./run.sh compile          sólo compila
#   ./run.sh test             compila y pasa la regresión (tests.sh)
#   ./run.sh lk               ventana con lk.elf (Xenon, PPC64): escribe en el shell de LK
#   ./run.sh png              renderiza fb_demo.bin a out/fb_demo.png (sin ventana)
#   ./run.sh [args de PCP]    ventana con fb_demo.bin (o el archivo/opciones que indiques)
set -e
cd "$(dirname "$0")"
mkdir -p out
if command -v javac >/dev/null 2>&1; then
  javac -encoding UTF-8 -d out $(find src -name '*.java')
else
  java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d out $(find src -name '*.java')
fi
T=src/pcp/TESTS
case "$1" in
  compile) ;;
  test)    ./tests.sh ;;
  lk)      java -cp out pcp.PCP $T/lk.elf --elf --size 1280x720 ;;
  png)     java -cp out pcp.PCP $T/fb_demo.bin --headless --png out/fb_demo.png ;;
  *)       java -cp out pcp.PCP ${1:-$T/fb_demo.bin} "$@" ;;
esac
