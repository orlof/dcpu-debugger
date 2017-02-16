package org.megastage.emulator;

public class VirtualPixie extends VirtualMonitor {
    private static final int BITPLANE_SIZE = 768;
    private int screenMode = 0;

    public VirtualPixie() {
        // These are the correct values
        super(0x774df615, 0x1802, 0x83610EC5);
        // These are LEM values - for compatibility testing
        // super(0x734df615, 0x1802, 0x1c6c8b36);
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

                        for(int idx = 0, addr = 0; addr < BITPLANE_SIZE; addr++) {
                            // one byte from each bitplane is copied to integer
                            long x = dcpu.ram[(videoRam + addr) & 0xffff];
                            int mask = 0x1;
                            if(screenMode > 1) {
                                x |= ((long) dcpu.ram[(videoRam + addr + BITPLANE_SIZE) & 0xffff]) << 16;
                                mask = 0x3;
                                if(screenMode > 2) {
                                    x |= ((long) dcpu.ram[(videoRam + addr + 2 * BITPLANE_SIZE) & 0xffff]) << 32;
                                    mask = 0x7;
                                    if(screenMode > 3) {
                                        x |= ((long) dcpu.ram[(videoRam + addr + 3 * BITPLANE_SIZE) & 0xffff]) << 48;
                                        mask = 0xf;
                                    }
                                }
                            }

                            // bits from 4 words are shuffled to 16 nibbles
                            x = (x & 0x0000ff000000ff00L) << 8 | ((x >> 8) & 0x0000ff000000ff00L) | (x & 0xff0000ffff0000ffL);
                            x = (x & 0x00000000ffff0000L) << 16 | ((x >> 16) & 0x00000000ffff0000L) | (x & 0xffff00000000ffffL);

                            x = (x & 0x00f000f000f000f0L) << 4 | ((x >> 4) & 0x00f000f000f000f0L) | (x & 0xf00ff00ff00ff00fL);
                            x = (x & 0x0000ff000000ff00L) << 8 | ((x >> 8) & 0x0000ff000000ff00L) | (x & 0xff0000ffff0000ffL);
                            x = (x & 0x0c0c0c0c0c0c0c0cL) << 2 | ((x >> 2) & 0x0c0c0c0c0c0c0c0cL) | (x & 0xc3c3c3c3c3c3c3c3L);
                            x = (x & 0x00f000f000f000f0L) << 4 | ((x >> 4) & 0x00f000f000f000f0L) | (x & 0xf00ff00ff00ff00fL);
                            x = (x & 0x2222222222222222L) << 1 | ((x >> 1) & 0x2222222222222222L) | (x & 0x9999999999999999L);
                            x = (x & 0x0c0c0c0c0c0c0c0cL) << 2 | ((x >> 2) & 0x0c0c0c0c0c0c0c0cL) | (x & 0xc3c3c3c3c3c3c3c3L);

                            // output rgb values
                            for(int shift = 60; shift >= 0; shift -= 4) {
                                pixels[idx++] = palette[(int) (x >> shift & mask)];
                            }
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