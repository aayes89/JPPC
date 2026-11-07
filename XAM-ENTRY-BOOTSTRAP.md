# XAM PE entry-point bootstrap smoke test

This patch fixes the immediate XAM entry-point failure observed in the previous
runtime snapshot. The XAM PE entry point at `0x81696AF0` restores the nonvolatile
PowerPC register frame and LR from the caller's thread stack. Starting it with
only `r1 = stackTop` left the saved LR slot zero, so its normal return fell into
`PC=0` and raised a program exception.

`XenonPeLoader.LoadedImage.start()` now prepares the observed Xenon PPC thread
frame: saved `r14-r31`, saved LR, and the 112-byte initial frame adjustment.
The LR points to a guest `b .` thread-exit trampoline, which the existing CPU
self-branch halt recognizes. No CPU instruction semantics were changed.

## Verify

From the project root:

```sh
./tests/run-xam-entry-smoke.sh
```

Expected final line:

```text
XAM_ENTRY_SMOKE_OK: PE mapped, Xenon thread frame initialized, entry point returned normally.
```

This is a bounded XAM entry-point execution milestone, **not** a complete Xbox
boot. The framebuffer remains empty because the kernel/XAM import dispatcher,
`Ani*` services, boot animation module startup, and Xenos command processing are
not yet implemented.
