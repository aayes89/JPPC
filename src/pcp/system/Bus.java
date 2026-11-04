/*
 * The MIT License
 *
 * Copyright 2025 Slam.
 */
package pcp.system;

import java.util.ArrayList;
import java.util.List;
import pcp.gpu.Device;

/**
 * Bus del sistema. Las direcciones se normalizan a 32 bits sin signo (antes,
 * 0x80000000+ llegaba como long negativo y nunca coincidía con un dispositivo
 * ni con la RAM). Los dispositivos tienen prioridad sobre la RAM.
 *
 * @author Slam
 */
public class Bus {

    private static final long ADDR_MASK = -1L;

    private static class DeviceMapping {

        final Device device;
        long startAddress;
        long endAddress;

        DeviceMapping(Device device, long startAddress, long endAddress) {
            this.device = device;
            this.startAddress = startAddress;
            this.endAddress = endAddress;
        }
    }

    private final List<DeviceMapping> devices = new ArrayList<>();
    private final Memory memory;
    private boolean traceUnmapped = false;
    private int unmappedLogged = 0;
    private long traceStart = 0;
    private long traceEnd = ADDR_MASK;
    private final java.util.Map<String, Integer> seen = new java.util.HashMap<>();

    /** Registra accesos a direcciones sin dispositivo fuera de la RAM (para descubrir el hardware que espera el firmware). */
    public void setTraceUnmapped(boolean t) {
        traceUnmapped = t;
    }

    /** Limita la traza MMIO a un rango inclusivo de direcciones físicas. */
    public void setTraceRange(long start, long end) {
        // El espacio físico se conserva completo (64 bits).
        if (Long.compareUnsigned(start, end) > 0) {
            throw new IllegalArgumentException("Rango de bus invertido");
        }
        traceStart = start;
        traceEnd = end;
    }

    private void logUnmapped(String kind, long address, long value) {
        if (!traceUnmapped || unmappedLogged >= 200
                || Long.compareUnsigned(address, traceStart) < 0
                || Long.compareUnsigned(address, traceEnd) > 0) {
            return;
        }
        if (seen.size() > 4096) {
            return;
        }
        String key = kind + address;
        int n = seen.merge(key, 1, Integer::sum);
        if (n > 2) {
            return; // no repetir el mismo acceso (bucles de espera)
        }
        // Las direcciones Xenon de boot/MMIO pueden tener el bit 63 activo.
        // Las comparaciones signed de Java las clasificaban como negativas y
        // ocultaban precisamente los accesos físicos de 64 bits que necesitamos
        // diagnosticar durante el arranque 1BL.
        boolean realModeMirror = Long.compareUnsigned(address, 0x80000000L) >= 0
                && Long.compareUnsigned(address, 0xA0000000L) < 0;
        boolean legacyWriteWindow = Long.compareUnsigned(address, 0x1C000000L) >= 0
                && Long.compareUnsigned(address, 0x1E000000L) < 0
                && kind.startsWith("escritura");
        boolean outsideRam = Long.compareUnsigned(address, Integer.toUnsignedLong(memory.getMemorySize())) >= 0;
        if ((outsideRam && !realModeMirror) || legacyWriteWindow) {
            unmappedLogged++;
            System.out.printf("[bus] %s no mapeado 0x%016x valor=0x%x%n", kind, address, value);
        }
    }

    public Bus(Memory memory) {
        this.memory = memory;
    }

    public void attachDevice(Device device, long startAddress, long endAddress) {
        devices.add(new DeviceMapping(device, startAddress, endAddress));
    }

    /** Cambia la ventana de un dispositivo ya conectado (p. ej. el framebuffer cuando la GPU cambia su superficie). */
    public void moveDevice(Device device, long startAddress, long endAddress) {
        for (DeviceMapping m : devices) {
            if (m.device == device) {
                m.startAddress = startAddress;
                m.endAddress = endAddress;
            }
        }
    }

    /** 0x80000000|dirección-física es el espejo "real mode" de la dirección física (< 512 MB). */
    private static long canon(long address) {
        return (address >= 0x80000000L && address < 0xA0000000L) ? (address & 0x1FFFFFFFL) : address;
    }

    private DeviceMapping find(long address) {
        for (int i = 0; i < devices.size(); i++) {
            DeviceMapping m = devices.get(i);
            if (Long.compareUnsigned(address, m.startAddress) >= 0
                    && Long.compareUnsigned(address, m.endAddress) <= 0) {
                return m;
            }
        }
        return null;
    }

    public byte read(long address) {
        address = canon(address);
        DeviceMapping m = find(address);
        if (m == null) {
            logUnmapped("lectura", address, 0);
            return address >= 0 && address < memory.getMemorySize() ? memory.read(address) : 0;
        }
        return m.device.read(address - m.startAddress);
    }

    public void write(long address, byte value) {
        address = canon(address);
        DeviceMapping m = find(address);
        if (m != null) {
            m.device.write(address - m.startAddress, value);
        } else {
            logUnmapped("escritura", address, value & 0xFF);
            if (address >= 0 && address < memory.getMemorySize()) {
                memory.write(address, value);
            }
        }
    }

    public int readWord(long address) {
        address = canon(address);
        DeviceMapping m = find(address);
        if (m == null) {
            logUnmapped("lectura32", address, 0);
            return address >= 0 && address < memory.getMemorySize() ? memory.readMemoryWord(address) : 0;
        }
        return m.device.readWord(address - m.startAddress);
    }

    public void writeWord(long address, long value) {
        address = canon(address);
        DeviceMapping m = find(address);
        if (m != null) {
            m.device.writeWord(address - m.startAddress, (int) value);
        } else {
            logUnmapped("escritura32", address, value & 0xFFFFFFFFL);
            if (address >= 0 && address < memory.getMemorySize()) {
                memory.writeMemoryWord(address, (int) value);
            }
        }
    }

    public int readHalf(long address) {
        return ((read(address) & 0xFF) << 8) | (read(address + 1) & 0xFF);
    }

    public void writeHalf(long address, int value) {
        write(address, (byte) (value >> 8));
        write(address + 1, (byte) value);
    }

    public int getMemorySize() {
        return memory.getMemorySize();
    }
}
