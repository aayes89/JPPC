# JPPC Xenon NAND extraction milestone

## Verified result

The raw NAND contains multiple filesystem-root generations. The previous `XenonFlashFs.open()` selected the root with the largest directory count. That selected root `0x7B8000` (33 entries), whose `.xex` directory entries do not begin with XEX2 headers. A content-based scan selects root `0x6A4000` instead:

- root: `0x6A4000`
- entries: 30
- valid FAT chains: 30
- exact chain lengths: 30
- XEX2/XEXP headers at file start: 20
- score from the proposed selector: 2420

The updated selector uses FAT-chain integrity, exact file lengths, and structurally valid XEX2 headers. It does not hardcode `0x6A4000`; `openAt()` is also available to inspect another root. The other roots are retained in the NAND and may represent another update generation/slot. Their active status has not yet been established.

## Decoded modules

The extraction pipeline decoded 15 images with zero errors. Every normal-compression XEX block was checked against its SHA-1 chain before LZX decompression.

Key modules:

| Module | Image size | Load/image base | Entry point | Notes |
|---|---:|---:|---:|---|
| `bootanim.xex` | `0x9C000` | `0x98000000` | `0` | `bootanim.dll`, 218 imports from `xboxkrnl.exe`, one `audio` resource |
| `xam.xex` | `0x610000` | `0x815F0000` | `0x81696AF0` | `xamc.dll`, 906 kernel imports, 11 resources |
| `dash.xex` | `0x1028000` | `0x92000000` | `0x92196610` | `dash.exe`, dashboard title ID `FFFE07D1`, two XAM import libraries, 36 resources |
| `hud.xex` | `0x49000` | `0x913E0000` | `0x913F8D00` | `hud.dll`, XAM + kernel imports |
| `mfgbootlauncher.xex` | `0x13000` | `0x9FEA0000` | `0x9FEA76F0` | Encrypted with the XEX1 key |
| `ximedic.xex` | `0xB0000` | `0x81720000` | `0` | Decompresses to XUIZ, encrypted with the XEX2 key |

Full per-module metadata and SHA-1 values are in `manifests/xex_manifest.csv`.

The pipeline result on this NAND was:

- 15 images extracted
- 5 XEXP delta patches identified but not applied
- 10 non-XEX files preserved
- 59 XEX resource records extracted
- 6,647 XUIZ entries expanded
- 0 decoding errors

## Dashboard resources

The extracted resource tree contains 6,705 files, including the 59 top-level XEX resource blobs:

- 5,049 `.xus` string/localization resources
- 812 `.png` images
- 477 `.xur` compiled XUI layouts
- 204 `.lub` Lua bytecode files
- 40 `.xma` audio files
- other XUI, avatar, and media assets

The `.xur` files start with `XUIB` and version `8`. The `.xus` files start with `XUIS` version `2`. XUIZ version 3 is an indexed archive (not zlib); `XuiZExtractor.java` expands its directory and preserves relative paths while preventing extraction outside the module resource root.

## XEX parser corrections

The original constants were wrong:

- import libraries: `0x000103FF` (not `0x000183FF`)
- original device path: `0x000080FF` (not `0x000100FF`)
- original PE name: `0x000183FF`

`XexFile.image()` must not require `MZ`; `huduiskin.xex` and `ximedic.xex` are valid XUIZ resources. Normal LZX blocks are now bounded by `blockEnd`, and each block's SHA-1 is verified. This also prevents the previous misleading `Chunk comprimido fuera de rango` error when a candidate key was wrong or when a valid image was not an MZ PE.

## Boot animation status

`bootanim.xex` is decoded and its `audio` resource is extracted as a RIFF/XMA WAV resource. It has no normal XEX entry point and its PE entry point is zero; its module flags are `0x9`. The NAND scan found no standalone `XMVF`/`XMV0` video container. Community reverse-engineering material describes the stock Xbox 360 startup animation as a real-time rendered animation rather than a standalone video. The next task is to trace the boot-time XBMOVIE/kernel path and the GPU command sequence; extracting the executable alone does not yet reproduce the animation.


## Import resolution and boot-animation call path

`imports.csv` contains 4,876 resolved import-thunk records: library, ordinal, type byte, thunk VA/RVA and raw word. The high byte is preserved as `type_flag`; the low 24 bits are the import ordinal.

Cross-checking those ordinals against the retail 17559 kernel export map gives a concrete boot-animation path:

- `xam.xex` imports kernel ordinals 610, 611, 613 and 678: `AniBlockOnAnimation`, `AniTerminateAnimation`, `AniSetLogo` and `AniStartBootAnimation`.
- `bootanim.xex` imports `VdInitializeEngines`, `VdInitializeRingBuffer`, `VdSetDisplayMode`, `VdSetDisplayModeOverride`, `VdShutdownEngines` and `VdTurnDisplayOff`, plus `XMACreateContext` and `XMAReleaseContext`.
- `dash.xex` imports XAM input, title-launch, dashboard-context and user-context APIs, including `XamInputGetState`, `XamLoaderLaunchTitleEx`, `XamSetDashContext`, `XamGetDashContext` and the user identity calls.

This is stronger than a guessed load order: XAM is the animation orchestrator through the kernel `Ani*` interface; `bootanim.dll` is the renderer/audio module using the video-display and XMA kernel APIs; the dashboard then uses XAM for input, user state and title launching. `bootanim.xex` having no ordinary PE entry point is consistent with this special kernel-managed path.

The source map is [`KernelExports_Retail_17559.asm`](https://github.com/mcpannett/ABadAvatar/blob/master/Common/KernelExports_Retail_17559.asm) in the public ABadAvatar repository. Its header explicitly identifies kernel 2.0.17559.0. The companion [`Xam_Retail_17559.asm`](https://github.com/mcpannett/ABadAvatar/blob/master/Common/Xam_Retail_17559.asm) is also in that repository, but its internal comment says 17599.0; selected addresses were cross-checked against executable code in this NAND's decoded XAM image, but the version discrepancy should be resolved before using every XAM address as authoritative. The selected mappings are in `manifests/boot_api_imports.csv`.

## Pending work toward the runtime milestone

1. Determine which filesystem/update generation the bootloader actually selects. Do not discard root `0x7B8000`; it contains a different set of entries and embedded XEX headers and must be analyzed alongside the verified base generation.
2. Decode/apply the five XEXP delta patches. `XexFile` deliberately reports compression type 3 as pending rather than treating it as ordinary LZX.
3. Resolve XEX import thunks against the actual kernel/XAM export tables and establish the boot module load order.
4. Parse XUR v8 and XUS v2 resources, then bind the extracted dashboard assets to the UI renderer.
5. Trace the special `bootanim`/XBMOVIE startup path and connect it to Xenos GPU execution.

## Build integration

Copy `src/pcp/xenon/*.java` into the project package. `XenonNandParser` is not included in this bundle; the sources use the existing project API `XenonNandParser.load(Path)`, `readData(int,int)`, and `getLogicalSize()`.

Run:

```text
java pcp.xenon.XenonXexTool nand.bin output keys.txt
```

`keys.txt` should contain the XEX1 and XEX2 AES keys in the existing `xex1:` / `xex2:` format. Do not commit that file or embed the keys in source control.
