package org.megastage.emulator;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class VirtualClockV2 extends DCPUHardware {
    private long startTime;
    private int interval;
    private int intCount;
    private char ticks;
    private char interruptMessage;

    public VirtualClockV2() {
        super(0x12d1b402, 0x8008, 0x1eb37e91);
        this.startTime = System.currentTimeMillis();
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
            case 0x10:
                GregorianCalendar gc = new GregorianCalendar();
                this.dcpu.registers[1] = (char) gc.get(Calendar.YEAR);
                this.dcpu.registers[2] = (char) ((((gc.get(Calendar.MONTH) + 1) << 8) | gc.get(Calendar.DATE)) & 0xffff);
                this.dcpu.registers[3] = (char) (((gc.get(Calendar.HOUR_OF_DAY) << 8) | gc.get(Calendar.MINUTE)) & 0xffff);
                this.dcpu.registers[4] = (char) gc.get(Calendar.SECOND);
                this.dcpu.registers[5] = (char) gc.get(Calendar.MILLISECOND);

                break;
            case 0x11:
                long uptime = System.currentTimeMillis() - startTime;

                // z
                this.dcpu.registers[5] = (char) ((uptime % 1000) & 0xffff); // z
                uptime /= 1000;

                this.dcpu.registers[4] = (char) ((uptime % 60) & 0xffff); // y
                uptime /= 60;

                long minutes = uptime % 60;
                uptime /= 60;

                long hours = uptime % 24;
                uptime /= 24;

                this.dcpu.registers[3] = (char) (((hours << 8) | (minutes)) & 0xffff); // x

                this.dcpu.registers[2] = (char) (uptime & 0xffff); // c

                break;
            case 0x12:
                // TODO NOT IMPLEMENTED

                break;
            case 0xffff:
                this.startTime = System.currentTimeMillis();
                this.interval = 0;
                this.intCount = 0;
                this.interruptMessage = 0;
                this.ticks = 0;

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