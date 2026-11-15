package pcp.TESTS;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import pcp.PPCSimulator;
import pcp.cpu.CPU;
import pcp.xenon.TKernel;

/** Regression test for PPC32 xboxkrnl import thunks and syscall status values. */
public final class TKernelDispatchSelfTest {
    private static final long STATUS_NOT_IMPLEMENTED = 0xC0000002L;

    private TKernelDispatchSelfTest() { }

    public static void main(String[] args) throws Exception {
        PPCSimulator simulator = new PPCSimulator(64, 64);
        TKernel kernel = new TKernel(simulator);
        CPU cpu = simulator.getCpu();

        require(kernel.invokeKernelExport(0x7FFF, cpu) == STATUS_NOT_IMPLEMENTED,
                "unknown ordinal must return STATUS_NOT_IMPLEMENTED");
        require(kernel.invokeKernelExport(90, cpu) == 0,
                "KeDelayExecutionThread must return STATUS_SUCCESS in cooperative mode");
        require(kernel.invokeKernelExport(678, cpu) == STATUS_NOT_IMPLEMENTED,
                "AniStartBootAnimation must reject calls before bootanim registration");
        require(kernel.invokeKernelExport(210, cpu) == STATUS_NOT_IMPLEMENTED,
                "NtCreateFile must not report false success before filesystem ABI exists");
        require(kernel.invokeKernelExport(240, cpu) == STATUS_NOT_IMPLEMENTED,
                "NtReadFile must not report false success before filesystem ABI exists");

        Path manifest = Files.createTempFile("jppc-imports-", ".csv");
        try {
            String csv = "module,library,library_id,ordinal,type_flag,thunk_va,thunk_rva,raw_word\n"
                    + "xam.xex,xboxkrnl.exe,1,01E6,01,00012000,00002000,00000000\n"
                    + "xam.xex,xboxkrnl.exe,1,01E6,00,00013000,00003000,00000000\n"
                    + "dash.xex,xboxkrnl.exe,1,00D2,01,00014000,00004000,00000000\n";
            Files.write(manifest, csv.getBytes(StandardCharsets.UTF_8));
            int installed = kernel.installImports(manifest, "xam.xex");
            require(installed == 1, "only matching executable XAM import thunk must be installed");
            require(simulator.getMemory().readMemoryWord(0x12000L) == 0x380001E6,
                    "thunk must load ordinal into r0");
            require(simulator.getMemory().readMemoryWord(0x12004L) == 0x44000002,
                    "thunk must execute sc");
            require(kernel.getImportThunk("xam.xex", "xboxkrnl.exe", 0x01E6) == 0x12000L,
                    "installed thunk address must be discoverable");
        } finally {
            Files.deleteIfExists(manifest);
        }

        System.out.println("TKernel PPC32 import/syscall dispatch: ALL OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
