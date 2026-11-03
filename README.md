# JPPC — Emulador PowerPC / Xenon en Java

Emulador experimental Java para binarios PowerPC planos y ELF32/ELF64. Incluye CPU, memoria/bus, MMU parcial, caché, UART, SMC, SFCX con lector de imagen NAND Xenon (solo lectura), GPU Xenos parcial, framebuffer tiled y GPU de juguete para demos.

## Requisitos

- JDK 21 o superior.
- Sin dependencias externas.
- NetBeans/Ant usa source/target Java 21.

## Compilar y probar

```sh
./run.sh compile       # compilar en ./out
./run.sh test          # compilar y ejecutar 17 comprobaciones
ant clean jar          # generar dist/PCP.jar
```

La regresión cubre CPU PPC32, PPC64/FP, AltiVec, framebuffer MMIO, GPU de juguete, LK (shell y RGB bars) y XeLL (SMC/AVPACK, inicialización Xenos, SFCX/NAND, USB y framebuffer).

## Ejecutar

```sh
./run.sh png
./run.sh lk
./run.sh xell
```

- `./run.sh lk`: ventana de Little Kernel, superficie 1280×720.
- `./run.sh xell`: ventana XeLL, VGA 1024×768 y timebase acelerado para los retardos de firmware.

Invocación general:

```sh
java -Xmx1g -cp out pcp.PCP archivo [opciones]
```

Opciones: `--elf`, `--load 0xADDR`, `--size WxH`, `--headless`, `--png archivo.png`, `--console-png archivo.png`, `--max N`, `--input "comando\n"`, `--threads N`, `--tb N`, `--bus`, `--trace`, `--watch 0xADDR`, `--fb 0xADDR`, `--vram` y `--nand archivo.bin`.

Captura headless de XeLL:

```sh
java -Xmx1g -cp out pcp.PCP src/pcp/TESTS/xenon.elf \
  --elf --size 1024x768 --tb 1000 --headless --max 300000000 \
  --png out/xell.png --console-png out/xell_uart.png
```

`--png` guarda la imagen que produce el framebuffer Xenos. `--console-png` guarda, por separado, el texto del canal UART como monitor de diagnóstico.

## Capturas

- `capturas/xell_xenos_1024x768.png`: salida real del framebuffer Xenos durante la inicialización XeLL.
- `capturas/xell_uart_1024x768.png`: salida UART del mismo arranque.

![XeLL en framebuffer Xenos](capturas/xell_xenos_1024x768.png)

- El resto de capturas corresponde a las pruebas de framebuffer y GPU incluidas.

## Estructura

- `src/pcp/cpu/CPU.java`: núcleo PowerPC.
- `src/pcp/cpu/Instruction/`: operaciones y decodificación.
- `src/pcp/system/`: bus, memoria, MMU, caché, excepciones y depurador.
- `src/pcp/gpu/`: framebuffer, Xenos, UART, SMC, SFCX, consola y GPU de juguete.
- `src/pcp/utils/`: ELF y utilidades.
- `src/pcp/TESTS/`: binarios, ELF y fuentes de prueba.
- `tools/`: desensamblador y utilidades de diagnóstico.

## Límites conocidos

No es una Xbox 360 completa. VMX128 sigue sin implementarse. SFCX admite cargar imágenes NAND Xenon de solo lectura; la lectura física de páginas 512+16 está implementada, pero ECC y remapeo de bloques defectuosos siguen pendientes. Sin `--nand` se conserva el comportamiento de NAND vacía virtual. USB EHCI/OHCI es parcial y solo presenta dispositivos virtuales mínimos. Ethernet, SATA, mandos, PIC, PCI y varios periféricos Xenon siguen pendientes. La MMU/BAT e interrupciones también tienen cobertura parcial.

La captura XeLL confirma que el modo VGA Xenos y la escritura tiled del framebuffer funcionan. La ejecución llega a la inicialización y enumeración USB; no se garantiza el menú completo ni la carga de un ELF desde medios/red.

## Diagnóstico

- `--trace`: traza de instrucciones.
- `--bus`: accesos a hardware no mapeado y SPRs.
- `--watch 0xADDR`: observa ejecución en una dirección.
- `tools/dis.py`: mini-desensamblador.
- `tools/ProbeTiling.java`: herramienta experimental de tiling.

## Licencia

MIT. Ver `LICENSE`.

```sh
java -Xmx1g -cp dist/PCP.jar pcp.PCP src/pcp/TESTS/fb_demo.bin --headless --png out/fb_demo.png
```

### NAND Xenon real (solo lectura)

Para cargar una extracción RAW small-block de 16 MiB con OOB (0x1080000 bytes):

```sh
java -Xmx1g -cp dist/PCP.jar pcp.PCP src/pcp/TESTS/xenon.elf \\
  --elf --nand /ruta/nand-xenon.bin --size 1024x768 --tb 1000 \\
  --headless --max 300000000 --png out/xell-nand.png
```

`--nand` admite RAW de 17,301,504 bytes (512+16 por página) y datos sin OOB de
16,777,216 bytes. La imagen se abre en modo solo lectura. La lectura de páginas RAW está implementada y probada con una imagen sintética; la lectura lógica todavía no aplica ECC ni remapeo de bloques defectuosos. Las escrituras y borrados son deliberadamente no persistentes.
No usar este milestone como herramienta de flasheo ni como validación de arranque real.
