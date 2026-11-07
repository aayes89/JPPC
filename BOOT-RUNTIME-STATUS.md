# JPPC — estado de arranque Xenon

## Base conservada

Esta iteración mantiene el código Java de `jppc_latest`, las imágenes PE extraídas (`xam.xex.pe`, `bootanim.xex.pe`, `dash.xex.pe`), los recursos XUI/XUIZ extraídos, la NAND original, los XEX originales recuperados, los manifiestos de imports, `fuses.txt` y `keys.txt`.

Los fusesets y claves se conservan en los archivos locales del proyecto. No se copian sus valores a este informe.

## Resultado reproducible de la iteración

Compilación:

```sh
find . -name '*.java' -not -path './nandDump/*' -print0 | xargs -0 javac -encoding UTF-8 -d build
```

Prueba de alias de pila:

```sh
./tests/run-stack-smoke.sh
```

Resultado comprobado:

```text
STACK_ALIAS_OK EA=80FFFFE8 PA=00FFFFE8 VALUE=81696B20
```

Ejecución observada de XAM:

```sh
java -cp build pcp.PCP --xam --headless --trace --max 150 --png xam.png
```

XAM carga en `BASE=0x815F0000`, `ENTRY=0x81696AF0`, con `r1=0x9FFFFFF0`. La CPU ejecuta 120 instrucciones y termina con excepción de programa al llegar a `PC=0`; el framebuffer permanece negro (`VRAM palabras no nulas: 0`). Por tanto, esta base aún no presenta imagen de arranque.

## Hallazgos del análisis de imágenes

- `xam.xex.pe`: entry point `0x81696AF0`; importa 906 ordinals de `xboxkrnl.exe`.
- `bootanim.xex.pe`: entry point PE `0`; directorio PE de exports vacío; importa 218 ordinals de `xboxkrnl.exe`. No es un ejecutable autónomo que pueda iniciarse asignándole un PC arbitrario.
- `dash.xex.pe`: entry point `0x92196610`; depende de imports de `xam.xex` y debe cargarse después de establecer XAM y su tabla de exports.
- El manifiesto `nandDump/manifests/boot_api_imports.csv` identifica, entre otros, `AniStartBootAnimation` (ordinal 678) como import de `xam.xex` desde `xboxkrnl.exe`.
- `XenosGpu.java` modela registros MMIO y la selección de superficie; todavía no implementa el command processor PM4 ni la ejecución de shaders/draw calls de Xenos.

## Trabajo necesario para el siguiente milestone visual real

1. Implementar el loader de módulos XEX y resolver imports: los thunks de importación deben quedar enlazados a exports reales o a shims de kernel con ABI PPC; no basta con cargar secciones PE.
2. Implementar el contexto de hilo/loader requerido por la entrada de XAM. La entrada actual usa estado no inicializado del contexto de kernel y retorna sin producir framebuffer.
3. Implementar los servicios de kernel requeridos por `AniStartBootAnimation`, incluida la carga/ejecución del módulo de boot animation y los servicios de vídeo/audio que éste invoca.
4. Implementar el command processor Xenos suficiente para procesar los comandos de GPU de la animación.
5. Cargar y enlazar `dash.xex` contra los exports de XAM y los recursos XUI; ejecutar el dashboard real después de la fase de arranque.

## Referencia de arquitectura

Xenia documenta el mismo requisito general de emulación: al cargar un módulo, resolver imports de kernel y enlazarlos a shims del host; las llamadas del guest se transfieren a las implementaciones de esos exports. Véase `docs/kernel.md` del proyecto Xenia.
