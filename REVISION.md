# JPPC — revisión técnica y estado de la entrega

## Estado de esta ronda

**17/17 comprobaciones de regresión OK** con OpenJDK 21. Se mantienen las demos que ya funcionaban en el ZIP inicial y se avanzó XeLL hasta mostrar salida en el framebuffer real Xenos.

### Milestones

| ID | Entrega | Estado |
|---|---|---|
| M0 | Inventario y build reproducible | OK |
| M1 | CPU PPC32/PPC64, FP y AltiVec | OK según autotests incluidos |
| M2 | `fb_demo.bin` y `triangle_fixed.bin` | OK |
| M3 | `lk.elf`, shell UART y `gfx rgb_bars` | OK |
| M4 | XeLL: seis hilos, UART, SMC y AVPACK VGA | OK |
| M5 | Xenos: fase de vídeo, ANA/LUT y modo VGA 1024×768 | OK |
| M6 | SFCX + lector de imagen NAND Xenon 16 MiB RAW | Parcial: lectura de página física/lógica directa, solo lectura; ECC y remapeo pendientes |
| M7 | Escritura tiled y captura framebuffer XeLL | OK |
| M8 | Enumeración básica USB EHCI/OHCI | Parcial: dispositivos virtuales mínimos |
| M9 | NAND real, red, almacenamiento, menú y carga de ELF | Pendiente |

## XeLL: cambios implementados

1. **Time base:** incremento predeterminado para XeLL subido a 1000 ticks/instrucción. Evita que los retardos de firmware consuman una proporción excesiva de la cuota de ejecución. `--tb N` permite sobrescribirlo.
2. **SMC / AVPACK:** respuesta SMC para `xenon_smc_read_avpack()` con adaptador VGA virtual `0x5B`.
3. **ANA Xenos:** la escritura en `0x7954` completa la transacción y limpia el registro de dirección `0x7950`, evitando los bucles de timeout de `xenos_ana_write`.
4. **LUT:** `DC_LUT_AUTOFILL (0x64A0)` devuelve el bit de finalización esperado.
5. **SFCX/NAND:** `NandImage` carga imágenes Xenon small-block RAW (512+16) de 17,301,504 bytes o datos sin OOB de 16,777,216 bytes. `XenonSfcx` ejecuta `LOG_PAGE_TO_BUF`, `PHY_PAGE_TO_BUF` y transferencias `PAGE_BUF_TO_REG` desde la imagen cargada. No modifica el archivo. ECC, remapeo lógico de bloques defectuosos y escritura/borrado físico siguen pendientes.
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
- `capturas/xell_xenos_1024x768.png`: imagen del framebuffer Xenos, no una composición de UART.
- `capturas/xell_uart_1024x768.png`: registro serial del mismo arranque.

## Límites restantes

- **NAND:** carga RAW y expone lecturas físicas de páginas con OOB; la lectura lógica actual extrae los 512 bytes directos. Aún no corrige ECC ni omite/remapea bloques malos, por lo que no puede garantizar lecturas equivalentes al SFCX real para una NAND completa. No implementa filesystem ni escrituras.
- **USB:** EHCI/OHCI solo están modelados lo suficiente para la enumeración observada. No hay almacenamiento masivo ni controladores USB completos.
- **Red:** no hay MAC/PHY, lwIP ni TFTP funcionales.
- **GPU:** modo de display, superficie y tiling permiten la salida de XeLL; no existe un pipeline completo de comandos 3D Xenos ni shaders Xbox 360.
- **VMX128:** no implementado (opcodes 5/6 y registros vectoriales extendidos).
- **MMU/BAT e interrupciones:** cobertura parcial.
- **Otros dispositivos:** SATA, DVD, mandos, PIC, PCI y SMC avanzado pendientes. La NAND física se consume como imagen de solo lectura; no existe acceso directo a un chip físico.

## Verificación

- `./run.sh compile`: OK.
- `./run.sh test`: 17/17 OK (incluye geometría RAW, OOB, transferencia SFCX y protección de solo lectura).
- `ant clean jar`: BUILD SUCCESSFUL.
- Ejecución desde `dist/PCP.jar` mediante `java -cp dist/PCP.jar pcp.PCP`: OK (XeLL hasta EHCI/OHCI).
- ZIP extraído en una carpeta limpia: pendiente de verificación de esta entrega.

## Cobertura AltiVec existente

Los autotests incluidos verifican las operaciones implementadas de AltiVec/VMX clásico: cargas/almacenamientos vectoriales, permutaciones, operaciones lógicas y aritméticas, comparación y VSCR. No deben extrapolarse a VMX128.

## Cambios históricos preservados

CPU PPC64, punto flotante, ELF64 y `.opd`, UART, GPU Xenos con tiling usado por LK, consola interactiva, framebuffer y GPU de juguete, ensamblador `ppcasm.py`, y los arreglos previos de memoria/bus, branch, LR, XER, excepciones y ELF.
