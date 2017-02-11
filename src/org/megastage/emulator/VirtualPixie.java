package org.megastage.emulator;

public class VirtualPixie extends VirtualMonitor {
    private static final int BITPLANE_SIZE = 768;
    private int screenMode = 0;

    public VirtualPixie() {
        super(0x734df615, 0x1802, 0x1c6c8b36);
    }

    public void interrupt() {
        super.interrupt();

        int a = dcpu.registers[0];

        if (a == 16) {
            // graphics mode
            screenMode = dcpu.registers[1];
        }
    }

    public void render() {
        try {
            synchronized (this) {
                if (pixels != null) {
                    if(screenMode == 0) {
                        super.render();
                    } else {
                        if (paletteRam == 0) {
                            resetPalette();
                        } else {
                            loadPalette(dcpu.ram, paletteRam);
                        }

                        for(int idx = 0, addr = videoRam; addr < videoRam + BITPLANE_SIZE; addr++) {
                            // one byte from each bitplane is copied to integer
                            int x = dcpu.ram[videoRam + (addr & 0x1ffff)];
                            int mask = 0x1;
                            if(screenMode > 1) {
                                x |= dcpu.ram[videoRam + (addr + BITPLANE_SIZE & 0x1ffff)] << 8;
                                mask = 0x3;
                                if(screenMode > 2) {
                                    x |= dcpu.ram[videoRam + (addr + 2 * BITPLANE_SIZE & 0x1ffff)] << 16;
                                    mask = 0x7;
                                    if(screenMode > 3) {
                                        x |= dcpu.ram[videoRam + (addr + 3 * BITPLANE_SIZE & 0x1ffff)] << 24;
                                        mask = 0xf;
                                    }
                                }
                            }

                            // bits from 4 bytes are shuffled to 8 nibbles
                            x = (x & 0x00f000f0) << 4 | ((x >> 4) & 0x00f000f0) | (x & 0xf00ff00f);
                            x = (x & 0x0000ff00) << 8 | ((x >> 8) & 0x0000ff00) | (x & 0xff0000ff);
                            x = (x & 0x0c0c0c0c) << 2 | ((x >> 2) & 0x0c0c0c0c) | (x & 0xc3c3c3c3);
                            x = (x & 0x00f000f0) << 4 | ((x >> 4) & 0x00f000f0) | (x & 0xf00ff00f);
                            x = (x & 0x22222222) << 1 | ((x >> 1) & 0x22222222) | (x & 0x99999999);
                            x = (x & 0x0c0c0c0c) << 2 | ((x >> 2) & 0x0c0c0c0c) | (x & 0xc3c3c3c3);

                            // output rgb values
                            pixels[idx++] = palette[x >> 28 & mask];
                            pixels[idx++] = palette[x >> 24 & mask];
                            pixels[idx++] = palette[x >> 20 & mask];
                            pixels[idx++] = palette[x >> 16 & mask];
                            pixels[idx++] = palette[x >> 12 & mask];
                            pixels[idx++] = palette[x >> 8 & mask];
                            pixels[idx++] = palette[x >> 4 & mask];
                            pixels[idx++] = palette[x >> 0 & mask];
                        }

                        int color = palette[borderColor];
                        pixels[12288] = color;

                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}