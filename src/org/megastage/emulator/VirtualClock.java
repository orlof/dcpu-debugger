package org.megastage.emulator;

public class VirtualClock extends DCPUHardware {
    private int interval;
    private int intCount;
    private char ticks;
    private char interruptMessage;

    public VirtualClock() {
        super(0x12d0b402, 0x8008, 0x1eb37e91);
    }

    public void interrupt() {
        int a = this.dcpu.registers[0];
        switch(a) {
            case 0:
                this.interval = this.dcpu.registers[1];
                break;
            case 1:
                this.dcpu.registers[2] = this.ticks;
                this.ticks = 0;
                break;
            case 2:
                this.interruptMessage = this.dcpu.registers[1];
                break;
        }
    }

    public void tick60hz() {
        if (this.interval == 0) return;
        if (++this.intCount >= this.interval) {
            if (this.interruptMessage != 0) this.dcpu.interrupt(this.interruptMessage);
            this.intCount = 0;
            this.ticks++;
        }
    }

    @Override
    public void powerOff() {
        this.intCount = 0;
        this.interruptMessage = 0;
        this.interval = 0;
        this.ticks = 0;
    }
}