# JPPC Xenon — entrega verificada: carga PE y entrada XAM

Fecha de validación: 2026-11-07

## Incluye

- Proyecto Java completo y recursos de NAND incluidos en esta distribución.
- `xenon/XenonPeLoader.java`: loader PE32 PowerPC y preparación del frame inicial de hilo Xenon.
- `tests/run-xam-entry-smoke.sh`: compilación limpia y prueba de transferencia/retorno del entry point de XAM.
- `XAM-ENTRY-BOOTSTRAP.md`: explicación del contexto de hilo implementado.
- `BOOT-RUNTIME-STATUS.md`: estado técnico y limitaciones restantes.

## Validación reproducida

Ejecutado desde la raíz del proyecto:

```sh
sh tests/run-xam-entry-smoke.sh
```

Resultado:

```text
[XEX-PE] Loaded BASE=815F0000 ENTRY=81696AF0 SIZE=005A0200 SECTIONS=8
[XEX-PE] CONTEXT PC=81696AF0 r1=9FFFFF80 STACK=[9FFBFFF0..9FFFFFF0]
[CPU] Halt: 'b .' en PC=0x9ffc0010
[headless] 119 instrucciones, halted=true
XAM_ENTRY_SMOKE_OK: PE mapped, Xenon thread frame initialized, entry point returned normally.
```

## Alcance exacto

La imagen PE de XAM se mapea en su ImageBase y se transfiere control a su entry point PowerPC. El prólogo/epílogo de hilo vuelve al trampoline de salida sin excepción de CPU.

Esto **no** es todavía un arranque funcional de la consola: XAM retorna antes de iniciar servicios visibles; el framebuffer queda vacío. Siguen pendientes el parser/enlazador de imports XEX, dispatcher de exports del kernel, inicialización real del loader/kernel, `AniStartBootAnimation`, carga del módulo bootanim y command processor Xenos.

No se declara dashboard, animación ni boot completo.
