package org.megastage.emulator;

public class DisasmAddr {
    private final char[] memory;
    private int addr;

    public DisasmAddr(char[] mem) {
        this.memory = mem;
    }

    private StringBuilder sb = new StringBuilder();

    public String disassemble(int addr, char a, char b) {
        this.addr = addr;

        sb.setLength(0);
        sb.append(hex(addr)).append(" ");

        char c = memory[addr++];
        int op = c & 0b11111;

        switch (op) {
            case 0x01:
                basic(sb, "SET", c);
                break;
            case 0x02:
                basic(sb, "ADD", c);
                break;
            case 0x03:
                basic(sb, "SUB", c);
                break;
            case 0x04:
                basic(sb, "MUL", c);
                break;
            case 0x05:
                basic(sb, "MLI", c);
                break;
            case 0x06:
                basic(sb, "DIV", c);
                break;
            case 0x07:
                basic(sb, "DVI", c);
                break;
            case 0x08:
                basic(sb, "MOD", c);
                break;
            case 0x09:
                basic(sb, "MDI", c);
                break;
            case 0x0a:
                basic(sb, "AND", c);
                break;
            case 0x0b:
                basic(sb, "BOR", c);
                break;
            case 0x0c:
                basic(sb, "XOR", c);
                break;
            case 0x0d:
                basic(sb, "SHR", c);
                break;
            case 0x0e:
                basic(sb, "ASR", c);
                break;
            case 0x0f:
                basic(sb, "SHL", c);
                break;
            case 0x10:
                basic(sb, "IFB", c);
                break;
            case 0x11:
                basic(sb, "IFC", c);
                break;
            case 0x12:
                basic(sb, "IFE", c);
                break;
            case 0x13:
                basic(sb, "IFN", c);
                break;
            case 0x14:
                basic(sb, "IFG", c);
                break;
            case 0x15:
                basic(sb, "IFA", c);
                break;
            case 0x16:
                basic(sb, "IFL", c);
                break;
            case 0x17:
                basic(sb, "IFU", c);
                break;
            case 0x1a:
                basic(sb, "ADX", c);
                break;
            case 0x1b:
                basic(sb, "SBX", c);
                break;
            case 0x1e:
                basic(sb, "STI", c);
                break;
            case 0x1f:
                basic(sb, "STD", c);
                break;

            default:
                addData(sb, c);
        }
        sb.append(" (").append(hex(b)).append(", ").append(hex(a)).append(")");

        return sb.toString();
    }

    public String disassemble(int startPc, char a) {
        addr = startPc;

        sb.setLength(0);
        sb.append(hex(addr)).append(" ");

        char c = memory[addr++];
        int op = (c >> 5) & 0b11111;

        switch(op) {
            case 0x01:
                special(sb, "JSR", c);
                break;
            case 0x08:
                special(sb, "INT", c);
                break;
            case 0x09:
                special(sb, "IAG", c);
                break;
            case 0x0a:
                special(sb, "IAS", c);
                break;
            case 0x0b:
                special(sb, "RFI", c);
                break;
            case 0x0c:
                special(sb, "IAQ", c);
                break;
            case 0x10:
                special(sb, "HWN", c);
                break;
            case 0x11:
                special(sb, "HWQ", c);
                break;
            case 0x12:
                special(sb, "HWI", c);
                break;
            default:
                addData(sb, c);
        }
        sb.append(" (").append(hex(a)).append(")");

        return sb.toString();
    }

    private void addData(StringBuilder sb, char val) {
        sb.append("DAT ").append(" ").append(hex(val));
    }

    private void special(StringBuilder sb, String cmd, char c) {
        int a = (c >> 10) & 0b111111;
        String aa = addrA(a, true);

        sb.append(cmd).append(" ").append(aa);
    }

    private void basic(StringBuilder sb, String cmd, char c) {
        int a = (c >> 10) & 0b111111;
        int b = (c >> 5) & 0b11111;
        String aa = addrA(a, true);
        String bb = addrA(b, false);

        // sb.append(String.format(",%04X:%04X", i, (int) binary[i]));

        sb.append(cmd).append(" ").append(bb).append(", ").append(aa);
    }

    private static final String[] REG = new String[] {
            "A", "B", "C", "X", "Y", "Z", "I", "J",
            "[A]", "[B]", "[C]", "[X]", "[Y]", "[Z]", "[I]", "[J]"
    };

    private String addrA(int a, boolean isA) {
        if(a < 0x10) {
            return REG[a];
        } else if(a < 0x18) {
            return "[" + REG[a-0x10] + " + " + hex(memory[addr++]) + "]";
        } else if(a == 0x18) {
            return isA ? "POP": "PUSH";
        } else if(a == 0x19) {
            return "[SP]";
        } else if(a == 0x1a) {
            return "[SP + " + hex(memory[addr++]) + "]";
        } else if(a == 0x1b) {
            return "SP";
        } else if(a == 0x1c) {
            return "PC";
        } else if(a == 0x1d) {
            return "EX";
        } else if(a == 0x1e) {
            return "[" + hex(memory[addr++]) + "]";
        } else if(a == 0x1f) {
            return hex(memory[addr++]);
        } else {
            return hex((a - 0x21) & 0xffff);
        }
    }

    private String hex(int i) {
        return String.format("0x%04X", i & 0xffff);
    }
}
