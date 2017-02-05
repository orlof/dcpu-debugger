package org.megastage.emulator;

public class VirtualAsciiKeyboard extends DCPUHardware {
    private char[] keyBuffer = new char[8];
    private int krp;
    private int kwp;
    private boolean[] isDown = new boolean[256];
    private char interruptMessage;
    private boolean doInterrupt;
    private int mode = MODE_SMART_TEXT;

    private static final int MODE_SMART_TEXT = 0;
    private static final int MODE_GENERIC_KEYCODE = 1;

    public VirtualAsciiKeyboard() {
        super(0x30C17406, 0x1337, 0x1EB37E91);
    }

/*
    public void keyTyped(int keyCode, char keyChar) {
        if (keyChar < 0x20 || keyChar >= 127) return;
        if (keyBuffer[kwp & 0x7] == 0) {
            keyBuffer[kwp++ & 0x7] = keyChar;
            doInterrupt = true;
        }
    }
*/
    public void keyPressed(int keyCode, char keyChar) {
        if(mode == MODE_SMART_TEXT) {

            char c = dcpuChar(keyCode, keyChar);

            if(c == 0x90 || c == 0x91 || c == 0x92) {
                isDown[c] = true;
            } else if(c != 0) {
                if (keyBuffer[kwp & 0x7] == 0) {
                    keyBuffer[kwp++ & 0x7] = c;
                }

                doInterrupt = true;
            }
        } else {
            char c = dcpuCharByKeycode(keyCode);

            isDown[c] = true;

            if (keyBuffer[kwp & 0x7] == 0) {
                keyBuffer[kwp++ & 0x7] = c;
            }

            doInterrupt = true;

        }
    }

    public void keyReleased(int keyCode, char keyChar) {
        if(mode == MODE_SMART_TEXT) {
            char c = dcpuChar(keyCode, keyChar);

            if (c == 0x90 || c == 0x91 || c == 0x92) {
                isDown[c] = false;
            }
        } else {
            char c = dcpuCharByKeycode(keyCode);

            isDown[c] = false;

            if (keyBuffer[kwp & 0x7] == 0) {
                keyBuffer[kwp++ & 0x7] = (char) (c | 0x8000);
            }

            doInterrupt = true;
        }
    }

    public void interrupt() {
        int a = dcpu.registers[0];
        if (a == 0) {
            clear();
        } else if (a == 1) {
            if ((dcpu.registers[2] = keyBuffer[(krp & 0x7)]) != 0) {
                keyBuffer[(krp++ & 0x7)] = 0;
            }
        } else if (a == 2) {
            int key = dcpu.registers[1];
            if ((key >= 0) && (key < 256))
                dcpu.registers[2] = (char)(isDown[key] ? 1 : 0);
            else
                dcpu.registers[2] = 0;
        } else if (a == 3) {
            interruptMessage = dcpu.registers[1];
        } else if (a == 4) {
            int b = dcpu.registers[1];
            switch(b) {
                case MODE_SMART_TEXT:
                case MODE_GENERIC_KEYCODE:
                    mode = b;
            }
            clear();
        }
    }

    private void clear() {
        for (int i = 0; i < keyBuffer.length; i++) {
            keyBuffer[i] = 0;
        }
        krp = 0;
        kwp = 0;

        for (int i = 0; i < isDown.length; i++) {
            isDown[i] = false;
        }
    }

    public void tick60hz() {
        if (doInterrupt) {
            if (interruptMessage != 0) dcpu.interrupt(interruptMessage);
            doInterrupt = false;
        }
    }

    private char dcpuChar(int keyCode, char keyChar) {
        if (keyChar >= 0x20 && keyChar < 0x81) {
            return keyChar;
        }
        if (keyChar == '{' || keyChar == '}') {
            return keyChar;
        }

        return dcpuCharByKeycode(keyCode);
    }

    private char dcpuCharByKeycode(int keyCode) {
        switch(keyCode) {
            case 8:
                // BACKSPACE
                return 0x10;
            case 10:
                // RETURN
                return 0x11;
            case 155:
                // INSERT
                return 0x12;
            case 127:
                // DELETE
                return 0x13;
            case 38:
                // UP
                return 0x80;
            case 40:
                // DOWN
                return 0x81;
            case 37:
                // LEFT
                return 0x82;
            case 39:
                // RIGHT
                return 0x83;
            case 16:
                // SHIFT
                return 0x90;
            case 17:
                // CTRL
                return 0x91;
            case 18:
                // ALT
                return 0x92;
            case 27:
                // ESC
                return 27;
        }

        if(keyCode >= 0x20 && keyCode < 0x79) {
            return (char) keyCode;
        }

        return 0x00;
    }
}
