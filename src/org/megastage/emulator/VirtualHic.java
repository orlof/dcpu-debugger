package org.megastage.emulator;

import java.util.LinkedList;

public class VirtualHic extends DCPUHardware {

    private LinkedList[] data = new LinkedList[256];

    public VirtualHic() {
        super(0xe0239088, 0x02ff, 0xa87c900e);

        for(int i=0; i < data.length; i++) {
            data[i] = new LinkedList<>();
        }
    }

    private char getLowestPortWithData() {
        for(int i=0; i < data.length; i++) {
            if(!data[i].isEmpty()) return (char) i;
        }
        return (char) 0xffff;
    }

    public void interrupt() {
        int a = dcpu.registers[0];

        switch(a) {
            case 0: {
                int p = dcpu.registers[2];

                char val = 0;
                if (p % 2 == 0) {
                    val |= 8;
                }
                if (!data[p].isEmpty()) {
                    val |= 4;
                }

                dcpu.registers[0] = val;
                dcpu.registers[2] = getLowestPortWithData();
                break;
            }
            case 1: {
                // Receive data
                int p = dcpu.registers[2];

                if (data[p].isEmpty()) {
                    dcpu.registers[1] = 0x0000;
                    dcpu.registers[2] = 0x0003;
                } else {
                    dcpu.registers[1] = (char) data[p].removeFirst();
                    dcpu.registers[2] = 0x0000;
                }
                break;
            }
            case 2: {
                // Transmit data
                int p = dcpu.registers[2];

                data[p].addLast(dcpu.registers[1]);
                dcpu.registers[2] = 0x0000;
                break;
            }
            case 3: {
                // Configure interrupts
                break;
            }
            case 4: {
                // Get port name
                int p = dcpu.registers[2];

                if(p % 2 == 1) {
                    int addr = dcpu.registers[1];
                    char[] name = ("PORT " + p).toCharArray();
                    for (int i = 0; i < 8; i++, addr++) {
                        dcpu.ram[addr] = (i < name.length) ? name[i] : 0;
                    }
                    dcpu.registers[2] = 0;
                } else {
                    dcpu.registers[2] = 1;
                }

                break;
            }
        }
    }

    public void tick60hz() {
    }
}
