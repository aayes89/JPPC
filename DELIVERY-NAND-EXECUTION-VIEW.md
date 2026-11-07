# JPPC-Xenon — visualización de ejecución real de código NAND

## Qué se ejecuta

`PCP --xam` carga `nandDump/image/xam.xex.pe` en la RAM emulada, establece el contexto PPC32 y ejecuta el entry point `0x81696AF0` con la CPU PowerPC. La CPU registra cada instrucción obtenida mediante fetch desde la memoria invitada.

## Qué se ve

- En modo gráfico, el panel lateral **XENON GUEST EXECUTION — NAND CODE** muestra PC, contador, estado y las últimas instrucciones realmente ejecutadas (dirección + palabra PPC big-endian).
- El framebuffer queda reservado a la salida gráfica del invitado. No se dibuja `BootSplash` ni se inyectan gráficos falsos.
- En modo headless se genera `xam-execution-trace.png` con el mismo historial de ejecución.

## Ejecución

Desde la raíz del proyecto:

```bash
java pcp.PCP --xam
```

Para generar evidencia sin ventana:

```bash
java -Djava.awt.headless=true pcp.PCP --xam --headless --max 100000 --png xam-framebuffer.png
```

La ejecución verificada de esta entrega obtuvo 119 instrucciones desde el entry point de XAM y alcanzó el trampoline `0x9FFC0010`. Esto demuestra fetch/ejecución real de instrucciones de la imagen XAM extraída de NAND; todavía no demuestra inicialización completa de XAM, bootanim ni dashboard. El framebuffer GPU permanece sin píxeles escritos por el invitado.
