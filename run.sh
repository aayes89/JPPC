#!/bin/sh
# JPPC: compila y ejecuta. Requiere JDK 21+ (la CPU usa Math.unsignedMultiplyHigh).
#   ./run.sh compile     sólo compila          ./run.sh test    compila y pasa la regresión
#   ./run.sh lk          ventana con lk.elf (Xenon, PPC64): escribe en el shell de LK
#   ./run.sh xell        ventana con xenon.elf (XeLL, 6 hilos hardware)
#   ./run.sh png         fb_demo.bin -> out/fb_demo.png (sin ventana)
#   ./run.sh [args PCP]  ventana con fb_demo.bin (o el archivo/opciones que indiques)
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
  lk)      java -Xmx1g -cp out pcp.PCP $T/lk.elf --elf --size 1280x720 ;;
  xell)    java -Xmx1g -cp out pcp.PCP $T/xenon.elf --elf --size 1024x768 --tb 1000 ;;
  png)     java -cp out pcp.PCP $T/fb_demo_1280x720.bin --size 1280x720 --headless --png out/fb_demo.png ;;
  demo800) java -cp out pcp.PCP $T/fb_demo.bin --size 800x600 ;;
  demo720) java -cp out pcp.PCP $T/fb_demo_1280x720.bin --size 1280x720 ;;
  *)       java -cp out pcp.PCP ${1:-$T/fb_demo.bin} "$@" ;;
esac
