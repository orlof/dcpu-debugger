package org.megastage.emulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class VirtualVtaci extends DCPUHardware {

    private long calibrationStartTime;
    private boolean needToCalibrate = true;

    private List<Thruster> thrusters = new ArrayList<>();
    private List<Gimble> gimbles = new ArrayList<>();
    private char baseAddress;

    enum Mode {Disable, ForceAndMoment, GroupControl};
    Mode mode = Mode.Disable;

    Random r = new Random();

    public VirtualVtaci() {
        super(0xF7F7EE03, 0x0400, 0xC2200311);

        for(int i=0; i < 8; i++) {
            thrusters.add(new Thruster());
        }

        for(int i=0; i < 2; i++) {
            gimbles.add(new Gimble(i));
        }
    }

    public void interrupt() {
        int a = dcpu.registers[0];

        switch(a) {
            case 0: {
                // Stop All Thrust
                break;
            }
            case 1: {
                // Calibrate Thrusters
                needToCalibrate = false;
                calibrationStartTime = System.currentTimeMillis();
                break;
            }
            case 2: {
                // Calibrate Status
                if(needToCalibrate) {
                    dcpu.registers[0] = 2;
                } else {
                    long curtime = System.currentTimeMillis();
                    if (curtime > calibrationStartTime && curtime < calibrationStartTime + 10000) {
                        dcpu.registers[0] = 1;
                    } else {
                        dcpu.registers[0] = 0;
                    }
                }
                dcpu.registers[1] = (char) thrusters.size();
                dcpu.registers[2] = (char) gimbles.size();
                break;
            }
            case 3: {
                // Set Thrust mode
                char c = dcpu.registers[2];
                if(c==0) mode = Mode.Disable;
                if(c==1) mode = Mode.ForceAndMoment;
                if(c==2) mode = Mode.GroupControl;
                baseAddress = dcpu.registers[1];
                break;
            }
            case 4: {
                // Get Information of Thrusters
                int b = dcpu.registers[1];
                dcpu.registers[0] = (char) thrusters.size();
                dcpu.registers[2] = (char) 0;

                for(Thruster t: thrusters) {
                    for(int i=0; i < 5; i++) {
                        dcpu.ram[b++] = t.data[i];
                        b &= 0xffff;
                    }
                }

                break;
            }
            case 5: {
                // Get Information of Gimbles
                int b = dcpu.registers[1];

                for(Gimble g: gimbles) {
                    for(int i=0; i < 2; i++) {
                        dcpu.ram[b++] = g.data[i];
                        b &= 0xffff;
                    }
                }

                break;
            }
            case 6: {
                // Set Truster Groups
                int b = dcpu.registers[1];

                for(Thruster t: thrusters) {
                    t.grp = dcpu.ram[b++];
                }

                break;
            }
            case 7: {
                // Set Gimble Groups
                int b = dcpu.registers[1];

                for(Gimble t: gimbles) {
                    t.grp = dcpu.ram[b++];
                }

                break;
            }
            case 8: {
                // Thrusters Status
                int b = dcpu.registers[1];

                for(Thruster t: thrusters) {
                    if(dcpu.ram[(baseAddress + t.grp) & 0xffff] == 0) {
                        dcpu.ram[b++] = 0;
                    } else {
                        dcpu.ram[b++] = 1;
                    }
                }

                break;
            }
        }
    }

    public void tick60hz() {
    }

    private class Thruster {
        char[] data = new char[5];
        char grp;

        Thruster() {
            for(int i=0;i < data.length; i++) {
                data[i] = (char) r.nextInt();
            }
        }
    }

    private class Gimble {
        char[] data = new char[2];
        char grp;

        Gimble(int thr) {
            data[0] = (char) r.nextInt();
            data[1] = (char) thr;
        }
    }
}
