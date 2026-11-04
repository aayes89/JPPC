# JPPC — revisión técnica y estado de la entrega

## Estado de esta ronda

**La batería de regresión M7.7 pasó con OpenJDK 21, incluyendo el autotest PPC32 y la prueba opcional SFCX sobre NAND RAW.** Se mantienen las demos que ya funcionaban en el ZIP inicial y se avanzó XeLL hasta mostrar salida en el framebuffer real Xenos.

### Milestones

| ID | Entrega | Estado |
|---|---|---|
| M0 | Inventario y build reproducible | OK |
| M1 | CPU PPC32/PPC64, FP y AltiVec | OK según autotests incluidos |
| M2 | `fb_demo.bin` y `triangle_fixed.bin` | OK |
| M3 | `lk.elf`, shell UART y `gfx rgb_bars` | OK |
| M4 | XeLL: seis hilos, UART, SMC y AVPACK VGA | OK |
| M5 | Xenos: fase de vídeo, ANA/LUT y modo VGA 1024×768 | OK |
| M6 | SFCX + lector de imagen NAND Xenon 16 MiB RAW | Parcial: lectura física/lógica directa; EDC 26-bit calculado/validado, sin corrección ni remapeo |
| M7 | Escritura tiled y captura framebuffer XeLL | Parcial: framebuffer Xenos real; banner final del Auto-Dumper aún no demostrado |
| M8 | USB EHCI/OHCI y SATA/ATAPI | Parcial: USB con hubs virtuales; SATA/ATAPI mínimo y DVD virtual sin medio |
| M9 | NAND real completa, red, almacenamiento USB, menú y carga de ELF | Pendiente |

## XeLL: cambios implementados

1. **Time base:** incremento predeterminado para XeLL subido a 1000 ticks/instrucción. Evita que los retardos de firmware consuman una proporción excesiva de la cuota de ejecución. `--tb N` permite sobrescribirlo.
2. **SMC / AVPACK:** respuesta SMC para `xenon_smc_read_avpack()` con adaptador VGA virtual `0x5B`.
3. **ANA Xenos:** la escritura en `0x7954` completa la transacción y limpia el registro de dirección `0x7950`, evitando los bucles de timeout de `xenos_ana_write`.
4. **LUT:** `DC_LUT_AUTOFILL (0x64A0)` devuelve el bit de finalización esperado.
5. **SFCX/NAND:** `NandImage` carga imágenes Xenon small-block RAW (512+16) de 17,301,504 bytes o datos sin OOB de 16,777,216 bytes. `XenonSfcx` ejecuta `LOG_PAGE_TO_BUF`, `PHY_PAGE_TO_BUF` y transferencias `PAGE_BUF_TO_REG` desde la imagen cargada. Se añadió cálculo y validación del EDC Xbox 360 de 26 bits y extracción del BlockID small-block. Es detección, no corrección de bits; el SFCX aún no integra la validación en el camino de lectura lógica ni remapea bloques malos. No modifica el archivo.
6. **Framebuffer:** `D1GRPH_PRIMARY_SURFACE_ADDRESS` puede notificar cambios de superficie. El framebuffer se presenta en modo Xenos tiled.
7. **Mapa ELF:** el registro de framebuffer de extensión del emulador se mueve fuera de `0xC8000000` para no interceptar el espacio físico que firmware puede usar.
8. **Captura UART:** `--console-png` genera un monitor independiente sin dependencias gráficas externas, válido en headless.

### Resultado XeLL verificado

Con:

```sh
java -Xmx1g -cp out pcp.PCP src/pcp/TESTS/xenon.elf \
  --elf --size 1024x768 --tb 1000 --headless --max 300000000 \
  --png out/xell.png --console-png out/xell_uart.png
```

La ejecución:
- Detecta el modelo Xenon.
- Lee frecuencias GPU/MEM/EDRAM/FSB.
- Detecta `AVPACK 0x5B`.
- Configura el modo VGA 1024×768.
- Ejecuta `f1` y `f2` de configuración de vídeo.
- Inicializa SFCX; sin `--nand` usa buffer vacío virtual.
- Inicializa EHCI y OHCI.
- Enumera dos hubs USB virtuales.
- Escribe 786432 palabras no nulas en el framebuffer y genera PNG.

Capturas:
- `capturas/xell_xenos_text_console_1024x768.png`: captura del framebuffer Xenos del proyecto; muestra mensajes de inicialización USB, no el splash azul de XeLL Reloaded.
- `capturas/xell_uart_1024x768.png`: registro serial del mismo arranque.

## Límites restantes

- **NAND:** carga RAW y expone lecturas físicas de páginas con OOB; la lectura lógica actual extrae los 512 bytes directos. El EDC 26-bit se calcula y valida como API auxiliar, sin corrección de bits ni integración en lecturas SFCX lógicas. No se omiten/remapean bloques malos; no puede garantizar lecturas equivalentes al SFCX real para una NAND completa. No implementa filesystem ni escrituras.
- **USB:** EHCI/OHCI solo están modelados lo suficiente para la enumeración observada. No hay almacenamiento masivo ni controladores USB completos.
- **Red:** no hay MAC/PHY, lwIP ni TFTP funcionales.
- **GPU:** modo de display, superficie y tiling permiten mostrar el texto de inicialización; el banner final del 360 Usb Auto-Dumper no está demostrado; el ELF no es XeLL Reloaded. No existe un pipeline completo de comandos 3D Xenos ni shaders Xbox 360.
- **VMX128:** no implementado (opcodes 5/6 y registros vectoriales extendidos).
- **MMU/BAT e interrupciones:** cobertura parcial.
- **Otros dispositivos:** SATA/ATAPI tiene solo el modelo mínimo documentado en M7.7; mandos, PIC, PCI y SMC avanzado siguen pendientes. La NAND física se consume como imagen de solo lectura; no existe acceso directo a un chip físico.

## Verificación

- `ant clean jar`: BUILD SUCCESSFUL.
- Compilación directa de los 43 fuentes Java: OK.
- `NandImageSelfTest`: OK (geometría RAW, OOB, EDC 26-bit, metadata, transferencia SFCX y protección de solo lectura).
- Autotests CPU PPC32, PPC64/FP y VMX: OK.
- `fb_demo`: ejecuta y genera PNG; el binario continúa corriendo al límite de instrucciones configurado.
- La regresión completa `./run.sh test` excedió el límite de ejecución del entorno durante las pruebas de LK. No se declara aprobada en esta ronda.
- El ZIP se validó estructuralmente con `zip -T`; la extracción limpia y regresión completa quedan pendientes.

## Cobertura AltiVec existente

Los autotests incluidos verifican las operaciones implementadas de AltiVec/VMX clásico: cargas/almacenamientos vectoriales, permutaciones, operaciones lógicas y aritméticas, comparación y VSCR. No deben extrapolarse a VMX128.

## Cambios históricos preservados

CPU PPC64, punto flotante, ELF64 y `.opd`, UART, GPU Xenos con tiling usado por LK, consola interactiva, framebuffer y GPU de juguete, ensamblador `ppcasm.py`, y los arreglos previos de memoria/bus, branch, LR, XER, excepciones y ELF.


## M7.2 — 2026-11-04
- Añadido auditor de identidad ELF XeLL y SHA-256.
- Añadido filtro inclusivo `--bus-range` para reducir ruido de trazas MMIO.
- Confirmado acceso no mapeado a `0xEA001307` durante el bucle de espera en `PC=0x80008A70`.
- El ELF incluido se identifica como XeLL second stage 2012-10-26 / Patch 1.035; no contiene como cadena ASCII literal el marcador Xenos FB aportado por el usuario.
- No se declara splash XeLL Reloaded ni USB funcional.

## M7.6 — USB MMIO identification (histórico; hipótesis reemplazada por M7.7)

- Added `M7.6-REPORT.md` and `out/m76-probe.log` with the register access sequence and controlled experiment.
- Tested a standard OHCI register model at `0xEA001300`; it prevented XeLL from reaching OHCI init (PC `0x8000BA08`). The model was removed rather than keeping an unsupported hardware assumption.
- Current XeLL path remains unchanged: EHCI/OHCI init, two virtual hubs, then wait loop at `0x80008A70` for `(base[7] & 0xC0) == 0x40`.
- Existing CPU32 `W-` regression remains open.


## M7.7 — SATA/ATAPI y lectura SFCX

- Añadido `XenonSata` en `0xEA001300` con status `0x40` para el sondeo ATA, HDD ausente y ATAPI DVD virtual sin medio.
- `IDENTIFY PACKET DEVICE`, `INQUIRY`, `REQUEST SENSE`, `TEST UNIT READY` y `READ(10/12)` tienen pruebas unitarias.
- `NandSfcxReadSelfTest` valida el camino de registros SFCX hasta el offset lógico `0x4200` contra una NAND RAW externa.
- El autotest PPC32 se fija explícitamente a 800x600 para mantener el contrato del test aunque el default del emulador sea 1280x720.
- La prueba de NAND se hizo en solo lectura; no se guardan ni distribuyen el dump ni claves.
- No se afirma todavía que XeLL alcance el banner final: una ejecución de diagnóstico a 150 millones de instrucciones terminó antes de observar accesos a SATA.
