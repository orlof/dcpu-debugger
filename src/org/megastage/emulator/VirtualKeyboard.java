package org.megastage.emulator;

public class VirtualKeyboard extends DCPUHardware {
    private char[] keyBuffer = new char[64];
    private int krp;
    private int kwp;
    private boolean[] isDown = new boolean[256];
    private char interruptMessage;
    private boolean doInterrupt;

    public VirtualKeyboard() {
        super(0x30cf7406, 0x1337, 0x1EB37E91);
    }

/*
    public void keyTyped(int keyCode, char keyChar) {
        if (keyChar < 0x20 || keyChar >= 127) return;
        if (keyBuffer[kwp & 0x3F] == 0) {
            keyBuffer[kwp++ & 0x3F] = keyChar;
            doInterrupt = true;
        }
    }
*/
    public void keyPressed(int keyCode, char keyChar) {
        char c = dcpuChar(keyCode, keyChar);
        if (c == 0) return;

        isDown[c] = true;

        if(keyBuffer[kwp & 0x3F] == 0) {
            keyBuffer[kwp++ & 0x3F] = c;
        }

        doInterrupt = true;
    }

    public void keyReleased(int keyCode, char keyChar) {
        char c = dcpuChar(keyCode, keyChar);
        if (c == 0) return;
        isDown[c] = false;
        doInterrupt = true;
    }

    public void interrupt() {
        int a = dcpu.registers[0];
        if (a == 0) {
            for (int i = 0; i < keyBuffer.length; i++) {
                keyBuffer[i] = 0;
            }
            krp = 0;
            kwp = 0;
        } else if (a == 1) {
            if ((dcpu.registers[2] = keyBuffer[(krp & 0x3F)]) != 0) {
                keyBuffer[(krp++ & 0x3F)] = 0;
            }
        }
        else if (a == 2) {
            int key = dcpu.registers[1];
            if ((key >= 0) && (key < 256))
                dcpu.registers[2] = (char)(isDown[key] ? 1 : 0);
            else
                dcpu.registers[2] = 0;
        }
        else if (a == 3) {
            interruptMessage = dcpu.registers[1];
        }
    }

    public void tick60hz() {
        if (doInterrupt) {
            if (interruptMessage != 0) dcpu.interrupt(interruptMessage);
            doInterrupt = false;
        }
    }

    private char dcpuChar(int keyCode, char keyChar) {
        if(keyChar >= 0x20 && keyChar < 0x81) {
            return keyChar;
        }
        if(keyChar == '{' || keyChar == '}') {
            return keyChar;
        }

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
