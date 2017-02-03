package org.megastage.emulator;

public class VirtualSpeaker extends DCPUHardware {
    private SynthChip channel1;
    private SynthChip channel2;

    public VirtualSpeaker() {
        super(0xC0F00001, 0x0001, 0x5672746B);

        channel1 = new SynthChip();
        channel1.start();
        channel2 = new SynthChip(0.75);
        channel2.start();
    }

    public void interrupt() {
        int a = this.dcpu.registers[0];
        switch(a) {
            case 0:
                char freq1 = this.dcpu.registers[1];
                channel1.setFrequency(freq1);
                break;
            case 1:
                char freq2 = this.dcpu.registers[1];
                channel2.setFrequency(freq2);
                break;
        }
    }

    @Override
    public void powerOff() {
        channel1.exit();
        channel2.exit();
    }
}