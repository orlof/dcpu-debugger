package org.megastage.emulator;

import java.util.ArrayList;

public class Disassembler {
    private final char[] binary;
    private int addr, line;
    private StringBuilder sb = new StringBuilder();
    private ArrayList<Dat> dat = new ArrayList<>(8);
    private int startaddr;

    class Dat {
        int addr;
        char val;
        Dat(int addr, char val) {
            this.addr = addr;
            this.val = val;
        }
    }

    public Disassembler(char[] binary) {
        this.binary = binary;
        this.addr = 0;
    }

    public String disassemble() {
        try {
            while (true) {
                startaddr = addr;
                char c = binary[addr];
                addr++;
                int op = c & 0b11111;

                switch (op) {
                    case 0x01:
                        basic("SET", c);
                        break;
                    case 0x02:
                        basic("ADD", c);
                        break;
                    case 0x03:
                        basic("SUB", c);
                        break;
                    case 0x04:
                        basic("MUL", c);
                        break;
                    case 0x05:
                        basic("MLI", c);
                        break;
                    case 0x06:
                        basic("DIV", c);
                        break;
                    case 0x07:
                        basic("DVI", c);
                        break;
                    case 0x08:
                        basic("MOD", c);
                        break;
                    case 0x09:
                        basic("MDI", c);
                        break;
                    case 0x0a:
                        basic("AND", c);
                        break;
                    case 0x0b:
                        basic("BOR", c);
                        break;
                    case 0x0c:
                        basic("XOR", c);
                        break;
                    case 0x0d:
                        basic("SHR", c);
                        break;
                    case 0x0e:
                        basic("ASR", c);
                        break;
                    case 0x0f:
                        basic("SHL", c);
                        break;
                    case 0x10:
                        basic("IFB", c);
                        break;
                    case 0x11:
                        basic("IFC", c);
                        break;
                    case 0x12:
                        basic("IFE", c);
                        break;
                    case 0x13:
                        basic("IFN", c);
                        break;
                    case 0x14:
                        basic("IFG", c);
                        break;
                    case 0x15:
                        basic("IFA", c);
                        break;
                    case 0x16:
                        basic("IFL", c);
                        break;
                    case 0x17:
                        basic("IFU", c);
                        break;
                    case 0x1a:
                        basic("ADX", c);
                        break;
                    case 0x1b:
                        basic("SBX", c);
                        break;
                    case 0x1e:
                        basic("STI", c);
                        break;
                    case 0x1f:
                        basic("STD", c);
                        break;

                    case 0x00:
                        op = (c >> 5) & 0b11111;
                        switch(op) {
                            case 0x01:
                                special("JSR", c);
                                break;
                            case 0x08:
                                special("INT", c);
                                break;
                            case 0x09:
                                special("IAG", c);
                                break;
                            case 0x0a:
                                special("IAS", c);
                                break;
                            case 0x0b:
                                special("RFI", c);
                                break;
                            case 0x0c:
                                special("IAQ", c);
                                break;
                            case 0x10:
                                special("HWN", c);
                                break;
                            case 0x11:
                                special("HWQ", c);
                                break;
                            case 0x12:
                                special("HWI", c);
                                break;
                            default:
                                addData(startaddr, c);
                        }
                        break;

                    default:
                        addData(startaddr, c);
                }
            }
        } catch(ArrayIndexOutOfBoundsException ex) {
        }
        for(int i = startaddr; i < addr; i++) {
            addData(i, binary[i & 0xffff]);
        }
        closeDat();
        return sb.toString();
    }

    private void addData(int addr, char val) {
        if(dat.size() == 8) {
            closeDat();
        }

        dat.add(new Dat(addr, val));
    }

    private void closeDat() {
        if(dat.size() > 0) {
            sb.append("decomp,").append(line++).append(",").append(dat.size());
            for(Dat d: dat) {
                sb.append(String.format(",%04X:%04X", d.addr, d.val & 0xffff));
            }

            sb.append("\nDAT ");
            for (int i = 0; i < dat.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(String.format("0x%04X", dat.get(i).val & 0xffff));
            }
            sb.append("\n");
            dat.clear();
        }
    }

    private void special(String cmd, char c) {
        closeDat();
        int a = (c >> 10) & 0b111111;
        int op = (c >> 5) & 0b11111;
        String aa = addrA(a, true);

        sb.append("decomp,").append(line++).append(",").append(addr - startaddr);
        for(int i=startaddr; i < addr; i++) {
            sb.append(String.format(",%04X:%04X", i, (int) binary[i]));
        }

        sb.append("\n").append(cmd).append(" ").append(aa).append("\n");
    }

    private void basic(String cmd, char c) {
        closeDat();
        int a = (c >> 10) & 0b111111;
        int b = (c >> 5) & 0b11111;
        String aa = addrA(a, true);
        String bb = addrA(b, false);

        sb.append("decomp,").append(line++).append(",").append(addr - startaddr);
        for(int i=startaddr; i < addr; i++) {
            sb.append(String.format(",%04X:%04X", i, (int) binary[i]));
        }

        sb.append("\n").append(cmd).append(" ").append(bb).append(", ").append(aa).append("\n");
    }

    private static final String[] REG = new String[] {
            "A", "B", "C", "X", "Y", "Z", "I", "J",
            "[A]", "[B]", "[C]", "[X]", "[Y]", "[Z]", "[I]", "[J]"
    };

    private String addrA(int a, boolean isA) {
        if(a < 0x10) {
            return REG[a];
        } else if(a < 0x18) {
            return "[" + REG[a-0x10] + " + " + hex(binary[addr++]) + "]";
        } else if(a == 0x18) {
            return isA ? "POP": "PUSH";
        } else if(a == 0x19) {
            return "[SP]";
        } else if(a == 0x1a) {
            return "[SP + " + hex(binary[addr++]) + "]";
        } else if(a == 0x1b) {
            return "SP";
        } else if(a == 0x1c) {
            return "PC";
        } else if(a == 0x1d) {
            return "EX";
        } else if(a == 0x1e) {
            return "[" + hex(binary[addr++]) + "]";
        } else if(a == 0x1f) {
            return hex(binary[addr++]);
        } else {
            return hex((a - 0x21) & 0xffff);
        }
    }

    private String hex(int i) {
        return String.format("0x%04X", i & 0xffff);
    }
}
