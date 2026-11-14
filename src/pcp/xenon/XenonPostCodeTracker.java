package pcp.xenon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Observador pasivo del bus POST de Xbox 360; nunca sintetiza códigos. */
public final class XenonPostCodeTracker {
    public static final class Entry {
        public final long sequence;
        public final int code;
        public final String description;
        public final String orderViolation;

        private Entry(long sequence, int code, String description, String orderViolation) {
            this.sequence = sequence;
            this.code = code;
            this.description = description;
            this.orderViolation = orderViolation;
        }

        public boolean hasViolation() { return orderViolation != null; }
    }

    private static final String[] KERNEL_PHASES = {
        "INIT_KERNEL", "INIT_HAL_PHASE_0", "INIT_PROCESS_OBJECTS", "INIT_KERNEL_DEBUGGER",
        "INIT_MEMORY_MANAGER", "INIT_STACKS", "INIT_OBJECT_SYSTEM", "INIT_PHASE1_THREAD",
        "INIT_PROCESSORS", "INIT_KEY_VAULT", "INIT_HAL_PHASE_1", "INIT_SFC_DRIVER",
        "INIT_SECURITY", "INIT_KEY_EX_VAULT", "INIT_SETTINGS", "INIT_POWER_MODE",
        "INIT_VIDEO_DRIVER", "INIT_AUDIO_DRIVER", "INIT_BOOT_ANIMATION (bootanim.xex, XMADecoder, XAudioRender)", "INIT_SATA_DRIVER",
        "INIT_SHADOWBOOT", "INIT_DUMP_SYSTEM", "INIT_SYSTEM_ROOT", "INIT_OTHER_DRIVERS",
        "INIT_STFS_DRIVER", "LOAD_XAM"
    };

    private final List<Entry> history = new ArrayList<>();
    private final Set<Integer> seen = new LinkedHashSet<>();
    private long sequence;
    private int lastKernelCode = -1;

    public synchronized Entry record(int rawCode) {
        int code = rawCode & 0xFF;
        String violation = null;
        String description = describe(code);

        if (code >= 0x60 && code <= 0x79) {
            if (lastKernelCode >= 0 && code < lastKernelCode) {
                violation = String.format("regresión kernel POST 0x%02X después de 0x%02X", code, lastKernelCode);
            }
            if (code == 0x72 && (!seen.contains(0x70) || !seen.contains(0x71))) {
                violation = append(violation,
                        "INIT_BOOT_ANIMATION observado antes de INIT_VIDEO_DRIVER e INIT_AUDIO_DRIVER");
            }
            if (code == 0x79) {
                int[] required = {0x72, 0x73, 0x76, 0x77, 0x78};
                for (int prerequisite : required) {
                    if (!seen.contains(prerequisite)) {
                        violation = append(violation, String.format("LOAD_XAM sin POST previo 0x%02X", prerequisite));
                    }
                }
            }
            if (lastKernelCode < code) lastKernelCode = code;
        }

        Entry entry = new Entry(++sequence, code, description, violation);
        history.add(entry);
        seen.add(code);
        return entry;
    }

    private static String append(String current, String next) {
        return current == null ? next : current + "; " + next;
    }

    public synchronized boolean hasSeen(int code) { return seen.contains(code & 0xFF); }
    public synchronized int lastCode() { return history.isEmpty() ? -1 : history.get(history.size() - 1).code; }
    public synchronized List<Entry> history() { return Collections.unmodifiableList(new ArrayList<>(history)); }
    public synchronized List<Entry> violations() {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : history) if (entry.hasViolation()) result.add(entry);
        return Collections.unmodifiableList(result);
    }

    public synchronized boolean enteredBootAnimationStage() { return seen.contains(0x72); }
    public synchronized boolean enteredXamStage() { return seen.contains(0x79); }

    public synchronized void reset() {
        history.clear();
        seen.clear();
        sequence = 0;
        lastKernelCode = -1;
    }

    public static String describe(int rawCode) {
        int code = rawCode & 0xFF;
        if (code >= 0x60 && code <= 0x79) {
            return KERNEL_PHASES[code - 0x60];
        }
        switch (code) {
            case 0x10: return "1BL_STARTED_OR_PAYLOAD_STARTED";
            case 0x11: return "1BL_FSB_CONFIG_PHY_CONTROL";
            case 0x12: return "1BL_FSB_CONFIG_RX_STATE";
            case 0x13: return "1BL_FSB_CONFIG_TX_STATE";
            case 0x14: return "1BL_FSB_CONFIG_TX_CREDITS";
            case 0x15: return "1BL_FETCH_OFFSET";
            case 0x16: return "1BL_FETCH_HEADER";
            case 0x17: return "1BL_VERIFY_HEADER";
            case 0x18: return "1BL_FETCH_CONTENTS";
            case 0x19: return "1BL_HMACSHA_COMPUTE";
            case 0x1A: return "1BL_RC4_INITIALIZE";
            case 0x1B: return "1BL_RC4_DECRYPT";
            case 0x1C: return "1BL_SHA_COMPUTE";
            case 0x1D: return "1BL_SIGNATURE_VERIFY";
            case 0x1E: return "1BL_BRANCH";
            case 0x58: return "INIT_HYPERVISOR";
            case 0x59: return "INIT_SOC_MMIO";
            case 0x5A: return "INIT_XEX_TRAINING";
            case 0x5B: return "INIT_KEYRING";
            case 0x5C: return "INIT_KEYS";
            case 0x5D: return "INIT_SOC_INTERRUPTS";
            case 0x5E: return "INIT_SOC_INTERRUPTS_COMPLETE";
            case 0xD0: return "CB_A_ENTRY";
            case 0xD1: return "CB_A_READ_FUSES";
            case 0xD2: return "CB_A_VERIFY_CB_B_OFFSET";
            case 0xD3: return "CB_A_FETCH_CB_B_HEADER";
            case 0xD4: return "CB_A_VERIFY_CB_B_HEADER";
            case 0xD5: return "CB_A_FETCH_CB_B_CONTENTS";
            case 0xD6: return "CB_A_HMACSHA_CB_B";
            case 0xD7: return "CB_A_RC4_INITIALIZE_CB_B";
            case 0xD8: return "CB_A_RC4_DECRYPT_CB_B";
            case 0xD9: return "CB_A_SHA_COMPUTE_CB_B";
            case 0xDA: return "CB_A_SHA_VERIFY_CB_B";
            case 0xDB: return "CB_A_BRANCH_CB_B";
            case 0x20: return "CB_ENTRY_INIT_SOC";
            case 0x21: return "CB_INIT_SECOTP";
            case 0x22: return "CB_INIT_SECENG";
            case 0x23: return "CB_INIT_SYSRAM";
            case 0x24: return "CB_VERIFY_OFFSET_3BL";
            case 0x25: return "CB_LOCATE_3BL";
            case 0x26: return "CB_FETCH_3BL_HEADER";
            case 0x27: return "CB_VERIFY_3BL_HEADER";
            case 0x28: return "CB_FETCH_3BL_CONTENTS";
            case 0x29: return "CB_HMACSHA_3BL";
            case 0x2A: return "CB_RC4_INITIALIZE_3BL";
            case 0x2B: return "CB_RC4_DECRYPT_3BL";
            case 0x2C: return "CB_SHA_COMPUTE_3BL";
            case 0x2D: return "CB_SIGNATURE_VERIFY_3BL";
            case 0x2E: return "CB_HARDWARE_INITIALIZATION";
            case 0x2F: return "CB_RELOCATE";
            case 0x30: return "CB_VERIFY_OFFSET_CD";
            case 0x31: return "CB_FETCH_CD_HEADER";
            case 0x32: return "CB_VERIFY_CD_HEADER";
            case 0x33: return "CB_FETCH_CD_CONTENTS";
            case 0x34: return "CB_HMACSHA_CD";
            case 0x35: return "CB_RC4_INITIALIZE_CD";
            case 0x36: return "CB_RC4_DECRYPT_CD";
            case 0x37: return "CB_SHA_COMPUTE_CD";
            case 0x38: return "CB_SIGNATURE_VERIFY_CD";
            case 0x39: return "CB_SHA_VERIFY_CD";
            case 0x3A: return "CB_BRANCH_CD";
            case 0x3B: return "CB_PCI_INIT";
            case 0x40: return "CD_ENTRY_SETUP_PAGING";
            case 0x41: return "CD_VERIFY_OFFSET_CE";
            case 0x42: return "CD_FETCH_CE_HEADER";
            case 0x43: return "CD_VERIFY_CE_HEADER";
            case 0x44: return "CD_FETCH_CE_CONTENTS";
            case 0x45: return "CD_HMACSHA_CE";
            case 0x46: return "CD_RC4_INITIALIZE_CE";
            case 0x47: return "CD_RC4_DECRYPT_CE";
            case 0x48: return "CD_SHA_COMPUTE_CE";
            case 0x49: return "CD_SHA_VERIFY_CE";
            case 0x4A: return "CD_LOAD_CF";
            case 0x4B: return "CD_LZX_EXPAND_CE";
            case 0x4C: return "CD_SWEEP_CACHES";
            case 0x4D: return "CD_DECODE_FUSES";
            case 0x4E: return "CD_FETCH_CF_OFFSET";
            case 0x4F: return "CD_VERIFY_CF_OFFSET";
            case 0x50: return "CD_LOAD_UPDATE_1";
            case 0x51: return "CD_LOAD_UPDATE_2";
            case 0x52: return "CD_BRANCH_HV_KERNEL";
            case 0x53: return "CD_DECRYPT_VERIFY_HV_CERT";
            case 0xFF: return "PANIC_FATAL";
            default: return "UNKNOWN";
        }
    }
}
