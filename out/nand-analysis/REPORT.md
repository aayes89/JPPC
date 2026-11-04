# JPPC — Milestone M6.2: NAND Xenon y descifrado inicial de CB_A

## Alcance

- Lectura estrictamente read-only de NAND Xenon RAW small-block (512 bytes de datos + 16 bytes OOB).
- Validación EDC de 26 bits por página.
- Lectura de los offsets CB/CF del encabezado de NAND.
- Derivación HMAC-SHA1 con la clave 1BL y el valor aleatorio del encabezado CB_A; descifrado RC4 del cuerpo CB_A.
- La CPU key se recibe de forma protegida para las etapas posteriores, pero este milestone todavía no la utiliza para descifrar CB_B ni KeyVault.

## Resultado sobre el dump proporcionado

| Métrica | Resultado |
|---|---:|
| Tamaño RAW | 17,301,504 bytes (`0x1080000`) |
| Geometría | 32,768 páginas; 1,024 bloques |
| Datos/OOB por página | 512 / 16 bytes |
| Páginas EDC válidas | 31,483 |
| Páginas EDC inválidas | 0 |
| Páginas borradas (`0xFF`) | 1,285 |
| Offset CB leído del header | `0x00008000` |
| Offset CF leído del header | `0x00070000` |
| CB_A | versión `0x0788`, tamaño `0x1800` |

El resultado EDC confirma consistencia de los datos/OOB según el algoritmo implementado. No demuestra por sí solo ausencia de bloques defectuosos, corrección ECC, ni validez criptográfica RSA de CB_A.

## Artefacto extraído

`CB_A.decrypted.bin` es el resultado de descifrar el bloque CB_A con HMAC-SHA1 + RC4. El milestone no afirma que la firma RSA ni el hash interno hayan sido verificados: esa validación queda pendiente. La NAND original no se modifica ni se incluye en el ZIP.

## Límites pendientes

1. Verificación RotSumSHA / firma RSA del bootloader.
2. Identificación y tratamiento de bad blocks y remapeo lógico/físico.
3. Descifrado de CB_B con la CPU key y derivación correcta desde CB_A.
4. Descifrado/validación de KeyVault y SMC.
5. Integración de los bootloaders descifrados en el flujo de arranque del emulador.
6. USB MMIO y splash/menú XeLL siguen siendo hitos separados.

## Uso

Compilar con `ant jar` y ejecutar desde una terminal interactiva:

```sh
java -cp dist/PCP.jar pcp.gpu.XenonNandBootInspector /ruta/nand.raw out/nand-analysis
```

El programa solicita ambas claves sin eco, no las acepta como argumentos de proceso y no las escribe en el informe. La salida se genera en un directorio independiente; el dump fuente se abre solo para lectura.
