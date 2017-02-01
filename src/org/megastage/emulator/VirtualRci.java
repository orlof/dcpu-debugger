package org.megastage.emulator;

public class VirtualRci extends DCPUHardware {

    char channel=0, power = 7;
    char[] buf;

    public VirtualRci() {
        super(0xD00590A5, 0x0010, 0xA87C900E);
    }

    public void interrupt() {
        int a = dcpu.registers[0];

        switch(a) {
            case 0: {
                dcpu.registers[0] = channel;
                dcpu.registers[1] = power;
                dcpu.registers[2] = (char) ((buf==null) ? 0: 1);

                break;
            }
            case 1: {
                // Receive data
                if(buf==null) {
                    dcpu.registers[1] = 0x0000;
                    dcpu.registers[2] = 0x0001;
                } else {
                    System.arraycopy(buf, 0, dcpu.ram, dcpu.registers[1], buf.length);
                    dcpu.registers[1] = (char) buf.length;
                    dcpu.registers[2] = 0x0000;
                    buf = null;
                }
                break;
            }
            case 2: {
                // Transmit data
                buf = new char[dcpu.registers[2]];
                System.arraycopy(dcpu.ram, dcpu.registers[1], buf, 0, buf.length);
                dcpu.registers[2] = 0x0000;
                break;
            }
            case 3: {
                // Configure radio
                if(dcpu.registers[1] <= 0xff && dcpu.registers[2] <= 7) {
                    channel = dcpu.registers[1];
                    power = dcpu.registers[2];
                    dcpu.registers[2] = 0;
                } else {
                    dcpu.registers[2] = 1;
                }
                break;
            }
            case 4: {
                // Configure interrupts
                break;
            }
        }
    }

    public void tick60hz() {
    }
}
