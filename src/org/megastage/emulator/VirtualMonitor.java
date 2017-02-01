package org.megastage.emulator;

import java.io.IOException;

import javax.imageio.ImageIO;

public class VirtualMonitor extends DCPUHardware
{
    private int[] palette = new int[16];
    private char[] font = new char[256];
    public int[] pixels = new int[12289];
    private int screenMemMap;
    private int fontMemMap;
    private int paletteMemMap;
    private int borderColor = 0;

    public VirtualMonitor() {
        super(0x7349f615, 0x1802, 0x1c6c8b36);
    }

    private void resetFont() {
        int[] pixels = new int[4096];
        try {
            ImageIO.read(VirtualMonitor.class.getResource("/font.png")).getRGB(0, 0, 128, 32, pixels, 0, 128);
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (int c = 0; c < 128; c++) {
            int ro = c * 2;
            int xo = c % 32 * 4;
            int yo = c / 32 * 8;
            for (int xx = 0; xx < 4; xx++) {
                int bb = 0;
                for (int yy = 0; yy < 8; yy++)
                    if ((pixels[(xo + xx + (yo + yy) * 128)] & 0xFF) > 128)
                        bb |= 1 << yy;
                font[ro + xx / 2] = (char)(font[ro + xx / 2] | bb << (xx + 1 & 0x1) * 8);
            }
        }
    }

    private void resetPalette() {
        for (int i = 0; i < 16; i++) {
            int b = (i >> 0 & 0x1) * 170;
            int g = (i >> 1 & 0x1) * 170;
            int r = (i >> 2 & 0x1) * 170;
            if (i == 6) {
                g -= 85;
            } else if (i >= 8) {
                r += 85;
                g += 85;
                b += 85;
            }
            palette[i] = (0xFF000000 | r << 16 | g << 8 | b);
        }
    }

    private void resetPixels() {
        for (int i = 0; i < 12289; i++) {
            pixels[i] = 0;
        }
    }

    private void loadPalette(char[] ram, int offset) {
        for (int i = 0; i < 16; i++) {
            char ch = ram[(offset + i)];
            int b = (ch >> '\000' & 0xF) * 17;
            int g = (ch >> '\004' & 0xF) * 17;
            int r = (ch >> '\b' & 0xF) * 17;
            palette[i] = (0xFF000000 | r << 16 | g << 8 | b);
        }
    }

    public void interrupt() {
        int a = dcpu.registers[0];
        if (a == 0) {
            screenMemMap = dcpu.registers[1];
        } else if (a == 1) {
            fontMemMap = dcpu.registers[1];
        } else if (a == 2) {
            paletteMemMap = dcpu.registers[1];
        } else if (a == 3) {
            borderColor = (dcpu.registers[1] & 0xF);
        } else if (a == 4) {
            // dump font
            int offs = dcpu.registers[1];
            for (int i = 0; i < font.length; i++) {
                dcpu.ram[(offs + i & 0xFFFF)] = font[i];
            }
            dcpu.cycles += 256;
        } else if (a == 5) {
            // dump palette
            int offs = dcpu.registers[1];
            for (int i = 0; i < 16; i++) {
                int b = (i >> 0 & 0x1) * 10;
                int g = (i >> 1 & 0x1) * 10;
                int r = (i >> 2 & 0x1) * 10;
                if (i == 6) {
                    g -= 5;
                } else if (i >= 8) {
                    r += 5;
                    g += 5;
                    b += 5;
                }
                dcpu.ram[(offs + i & 0xFFFF)] = (char)(r << 8 | g << 4 | b);
            }
            dcpu.cycles += 16;
        }
    }

    public void render() {
        try {
            synchronized (this) {
                if (pixels != null) {
                    if (screenMemMap == 0) {

                        for (int y = 0; y < 96; y++) {
                            for (int x = 0; x < 128; x++) {
                                pixels[(x + y * 128)] = palette[0];
                            }
                        }

                    } else {
                        long time = System.currentTimeMillis() / 16L;
                        boolean blink = time / 20L % 2L == 0L;

                        char[] fontRam = font;
                        int charOffset = 0;
                        if (fontMemMap > 0) {
                            fontRam = dcpu.ram;
                            charOffset = fontMemMap;
                        }
                        if (paletteMemMap == 0)
                            resetPalette();
                        else {
                            loadPalette(dcpu.ram, paletteMemMap);
                        }

                        for (int y = 0; y < 12; y++) {
                            for (int x = 0; x < 32; x++) {
                                char dat = dcpu.ram[screenMemMap + x + y * 32 & 0xFFFF];
                                int ch = dat & 0x7F;
                                int colorIndex = dat >> '\b' & 0xFF;
                                int co = charOffset + ch * 2;

                                int color = palette[(colorIndex & 0xF)];
                                int colorAdd = palette[(colorIndex >> 4 & 0xF)] - color;
                                if ((blink) && ((dat & 0x80) > 0)) colorAdd = 0;
                                int pixelOffs = x * 4 + y * 8 * 128;

                                for (int xx = 0; xx < 4; xx++) {
                                    int bits = fontRam[(co + (xx >> 1))] >> (xx + 1 & 0x1) * 8 & 0xFF;
                                    for (int yy = 0; yy < 8; yy++) {
                                        int col = color + colorAdd * (bits >> yy & 0x1);
                                        pixels[(pixelOffs + xx + yy * 128)] = col;
                                    }
                                }
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

    public void setPixels(int[] pixels)
    {
        synchronized (this) {
            this.pixels = pixels;
        }
    }

    @Override
    public void powerOff() {
        screenMemMap = 0;
        fontMemMap = 0;
        paletteMemMap = 0;
        borderColor = 0;
        resetPalette();
        resetFont();
        resetPixels();
    }

    @Override
    public void powerOn() {
        resetPalette();
        resetFont();
        resetPixels();
    }
}