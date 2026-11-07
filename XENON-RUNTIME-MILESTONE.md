# Xenon runtime milestone — current integration

## Integrated in this revision

- PE32 Xenon loader reads `StackReserve` and `StackCommit` from the optional header.
- `LoadedImage.start(CPU, memorySize)` initializes an aligned PPC32 stack in the 512 MiB real-mode RAM mirror and transfers execution to the PE entry point.
- `PCP --xam` loads `nandDump/image/xam.xex.pe` and starts that PE directly.
- Extracted module images are included under `nandDump/image/`: XAM, bootanim and dashboard.
- The complete extracted `images/`, `resources/` and import manifests are preserved under `nandDump/extracted/`.

## Run XAM entry-point test

From the project root, after compiling the Java sources:

```sh
java pcp.PCP --xam --size 1280x720
```

For a bounded diagnostic run:

```sh
java pcp.PCP --xam --headless --max 100000 --png xam-boot.png --trace
```

The loader reports the entry point, stack interval, reserve/commit values and MSR.

## Runtime work still required for the stock animation/dashboard

The extracted NAND metadata identifies the actual call path: XAM calls the kernel `Ani*` APIs; the kernel-managed `bootanim.xex` renderer uses `Vd*` display/ring-buffer APIs and XMA; dashboard `dash.xex` depends on hundreds of XAM/kernel imports and XUI resources. The current Java project does not yet provide the Xbox kernel export dispatcher, XAM import binding, XUI runtime, or the `Ani*`/`Vd*`/XMA service implementations. Therefore `--xam` now enters the PE with a valid stack, but it is not yet a complete stock animation/dashboard boot. The module images, resources and verified import manifests are included so the next implementation stage uses the extracted firmware rather than guessed addresses or fabricated media.

See `nandDump/extracted/manifests/boot_api_imports.csv` for verified boot-animation imports and `xex_manifest.csv` for module entry points and dependencies.
