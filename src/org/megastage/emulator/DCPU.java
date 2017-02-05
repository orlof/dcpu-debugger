package org.megastage.emulator;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;

/**
 * Experimental 1.7 update to Notch's 1.4 emulator
 * @author Notch, Herobrine
 *
 */
public class DCPU
{
    public static final int khz = 1000;
    private static final boolean SHIFT_DISTANCE_5_BITS = true;

    public DebugData debugData;
    public boolean running = false;
    public int[] memUseCount = new int[65536];
    public boolean turbo;
    public boolean trace = false;
    public File[] floppyFile = new File[2];

    private boolean[] breakPoints = new boolean[65536];
    private boolean stepOver = false;
    private int stepOverPc = -1;
    public char[] ram = new char[65536];

    public char pc;
    public char sp;
    public char ex;
    public char ia;
    public char[] registers = new char[8];
    public long cycles, cycleStart;

    public final ArrayList<DCPUHardware> hardware = new ArrayList<DCPUHardware>();
    public final VirtualClockV2 clock = new VirtualClockV2();
    public final VirtualClock clockv1 = new VirtualClock();
    public final VirtualKeyboard kbd = new VirtualKeyboard();
    public final VirtualAsciiKeyboard asciiKbd = new VirtualAsciiKeyboard();
    public final VirtualFloppyDrive[] floppy = { new VirtualFloppyDrive(), new VirtualFloppyDrive()};
    public final VirtualHic hic = new VirtualHic();
    public final VirtualRci rci = new VirtualRci();
    public final VirtualSpeaker speaker = new VirtualSpeaker();

    public boolean isSkipping = false;
    public boolean isOnFire = false;
    public boolean queueingEnabled = false; //TODO: Verify implementation
    public char[] interrupts = new char[256];
    public int ip;
    public int iwp;

    public boolean exSet = false;
    public File traceFile = new File(System.getProperty("user.dir"), "dcpu-trace.log");
    private DisasmAddr lineDisassembler;
    public PrintWriter out;

    public int getAddrB(int type) {
        switch (type & 0xF8) {
            case 0x00:
                return 0x10000 + (type & 0x7);
            case 0x08:
                return registers[type & 0x7];
            case 0x10:
                cycles++;
                return ram[pc++] + registers[type & 0x7] & 0xFFFF;
            case 0x18:
                switch (type & 0x7) {
                    case 0x0:
                        return (--sp) & 0xFFFF;
                    case 0x1:
                        return sp & 0xFFFF;
                    case 0x2:
                        cycles++;
                        return ram[pc++] + sp & 0xFFFF;
                    case 0x3:
                        return 0x10008;
                    case 0x4:
                        return 0x10009;
                    case 0x5:
                        return 0x10010;
                    case 0x6:
                        cycles++;
                        return ram[pc++];
                }
                cycles++;
                return 0x20000 | ram[pc++];
        }

        throw new IllegalStateException("Illegal a value type " + Integer.toHexString(type) + "! How did you manage that!?");
    }

    public int getAddrA(int type) {
        if (type >= 0x20) {
            return 0x20000 | (type & 0x1F) + 0xFFFF & 0xFFFF;
        }

        switch (type & 0xF8) {
            case 0x00:
                return 0x10000 + (type & 0x7);
            case 0x08:
                return registers[type & 0x7];
            case 0x10:
                cycles++;
                return ram[pc++] + registers[type & 0x7] & 0xFFFF;
            case 0x18:
                switch (type & 0x7) {
                    case 0x0:
                        return sp++ & 0xFFFF;
                    case 0x1:
                        return sp & 0xFFFF;
                    case 0x2:
                        cycles++;
                        return ram[pc++] + sp & 0xFFFF;
                    case 0x3:
                        return 0x10008;
                    case 0x4:
                        return 0x10009;
                    case 0x5:
                        return 0x10010;
                    case 0x6:
                        cycles++;
                        return ram[pc++];
                }
                cycles++;
                return 0x20000 | ram[pc++];
        }

        throw new IllegalStateException("Illegal a value type " + Integer.toHexString(type) + "! How did you manage that!?");
    }

    public char getValA(int type) {
        if (type >= 0x20) {
            return (char)((type & 0x1F) + 0xFFFF);
        }

        switch (type & 0xF8) {
            case 0x00:
                return registers[type & 0x7];
            case 0x08:
                return ram[registers[type & 0x7]];
            case 0x10:
                cycles++;
                return ram[ram[pc++] + registers[type & 0x7] & 0xFFFF];
            case 0x18:
                switch (type & 0x7) {
                    case 0x0:
                        return ram[sp++ & 0xFFFF];
                    case 0x1:
                        return ram[sp & 0xFFFF];
                    case 0x2:
                        cycles++;
                        return ram[ram[pc++] + sp & 0xFFFF];
                    case 0x3:
                        return sp;
                    case 0x4:
                        return pc;
                    case 0x5:
                        return ex;
                    case 0x6:
                        cycles++;
                        return ram[ram[pc++]];
                }
                cycles++;
                return ram[pc++];
        }

        throw new IllegalStateException("Illegal a value type " + Integer.toHexString(type) + "! How did you manage that!?");
    }

    public char get(int addr) {
        if (addr < 0x10000)
            return ram[addr & 0xFFFF];
        if (addr < 0x10008)
            return registers[addr & 0x7];
        if (addr >= 0x20000)
            return (char)addr;
        if (addr == 0x10008)
            return sp;
        if (addr == 0x10009)
            return pc;
        if (addr == 0x10010)
            return ex;
        throw new IllegalStateException("Illegal address " + Integer.toHexString(addr) + "! How did you manage that!?");
    }

    public void set(int addr, char val) {
        if (addr < 0x10000)
            ram[addr & 0xFFFF] = val;
        else if (addr < 0x10008) {
            registers[addr & 0x7] = val;
        } else if (addr < 0x20000) {
            if (addr == 0x10008)
                sp = val;
            else if (addr == 0x10009) {
                pc = val;
                rememberJump();
            } else if (addr == 0x10010)
                ex = val;
            else
                throw new IllegalStateException("Illegal address " + Integer.toHexString(addr) + "! How did you manage that!?");
        }
    }

    public static int getInstructionLength(char opcode) {
        int len = 1;
        int cmd = opcode & 0x1F;
        if (cmd == 0) {
            cmd = opcode >> 5 & 0x1F;
            if (cmd > 0) {
                int atype = opcode >> 10 & 0x3F;
                if (((atype & 0xF8) == 16) || (atype == 31) || (atype == 30)) len++;
            }
        }
        else {
            int atype = opcode >> 5 & 0x1F;
            int btype = opcode >> 10 & 0x3F;
            if ((atype >= 0x10 && atype <= 0x017) || atype == 0x1a || atype == 0x1e || atype == 0x1f) len++;
            if ((btype >= 0x10 && btype <= 0x017) || btype == 0x1a || btype == 0x1e || btype == 0x1f) len++;
        }
        return len;
    }

    public void skip() {
        isSkipping = true;
    }

    public HashMap<Character, SortedMap<Character, Integer>> jumps = new HashMap<>();
    public char startPC;

    public void tick() {
        startPC = pc;

        if (isOnFire) {
//      cycles += 10; //Disabled to match speed of crashing seen in livestreams
            /* For Java 7+
              int pos = ThreadLocalRandom.current().nextInt();
            char val = (char) (pos >> 16);//(char) ThreadLocalRandom.current().nextInt(65536);
            int len = (int)(1 / (ThreadLocalRandom.current().nextFloat() + 0.001f)) - 80;
            */
            int pos = (int)(Math.random() * 0x10000) & 0xFFFF;
            char val = (char) ((int)(Math.random() * 0x10000) & 0xFFFF);
            int len = (int)(1 / (Math.random() + 0.001f)) - 0x50;
            for (int i = 0; i < len; i++) {
                ram[(pos + i) & 0xFFFF] = val;
            }
        }

        memUseCount[pc & 0xffff]++;

        if (isSkipping) {
            cycles++;

            char opcode = ram[pc];
            int cmd = opcode & 0x1F;
            pc = (char)(pc + getInstructionLength(opcode));
            isSkipping = (cmd >= 16) && (cmd <= 23);
            return;
        }

        if (!queueingEnabled) {
            if (ip != iwp) {
                char a = interrupts[ip = ip + 1 & 0xFF];
                if (ia > 0) {
                    queueingEnabled = true;
                    ram[--sp & 0xFFFF] = pc;
                    ram[--sp & 0xFFFF] = registers[0];
                    registers[0] = a;
                    pc = ia;
                    return;
                }
            }
        }

        cycles++;

        char opcode = ram[pc++];

        int cmd = opcode & 0x1F;
        if (cmd == 0) {
            cmd = opcode >> 5 & 0x1F;
            if (cmd != 0)
            {
                int atype = opcode >> 10 & 0x3F;
                int aaddr = getAddrA(atype);
                char a = get(aaddr);

                if(stepOver) {
                    stepOver = false;
                    stepOverPc = pc;
                }

                if(trace) {
                    trace(lineDisassembler.disassemble(startPC, a));
                }

                switch (cmd) {
                    case 1: //JSR
                        cycles += 2;
                        ram[--sp & 0xFFFF] = pc;
                        pc = a;

                        rememberJump();

                        break;
//        case 7: //HCF
//          cycles += 8;
//          isOnFire = true;
//          break;
                    case 8: //INT
                        cycles += 3;
                        interrupt(a);
                        break;
                    case 9: //IAG
                        set(aaddr, ia);
                        break;
                    case 10: //IAS
                        ia = a;
                        break;
                    case 11: //RFI
                        cycles += 2;
                        //disables interrupt queueing, pops A from the stack, then pops PC from the stack
                        queueingEnabled = false;
                        registers[0] = ram[sp++ & 0xFFFF];
                        pc = ram[sp++ & 0xFFFF];
                        break;
                    case 12: //IAQ
                        cycles++;
                        //if a is nonzero, interrupts will be added to the queue instead of triggered. if a is zero, interrupts will be triggered as normal again
                        if (a == 0) {
                            queueingEnabled = false;
                        } else {
                            queueingEnabled = true;
                        }
                        break;
                    case 16: //HWN
                        cycles++;
                        set(aaddr, (char)hardware.size());
                        break;
                    case 17: //HWQ
                        cycles += 3;
                        synchronized (hardware) {
                            if ((a >= 0) && (a < hardware.size())) {
                                ((DCPUHardware)hardware.get(a)).query();
                            }
                        }
                        break;
                    case 18: //HWI
                        cycles += 3;
                        synchronized (hardware) {
                            if ((a >= 0) && (a < hardware.size())) {
                                ((DCPUHardware)hardware.get(a)).interrupt();
                            }
                        }
                        break;
                    case 2:
                    case 3:
                    case 4:
                    case 5:
                    case 6:
                    case 13:
                    case 14:
                    case 15:
                    default:
                        break;
                }
            }
        } else {
            int atype = opcode >> 10 & 0x3F;

            char a = getValA(atype);

            int btype = opcode >> 5 & 0x1F;
            int baddr = getAddrB(btype);
            char b = get(baddr);

            if(stepOver) {
                stepOver = false;
                stepOverPc = pc;
            }

            if(trace) {
                trace(lineDisassembler.disassemble(startPC, a, b));
            }

            switch (cmd) {
                case 1: //SET
                    b = a;
                    break;
                case 2:{ //ADD
                    cycles++;
                    int val = b + a;
                    b = (char)val;
                    set(baddr, b);
                    ex = (char)(val >> 16);
                    return;
                }case 3:{ //SUB
                    cycles++;
                    int val = b - a;
                    b = (char)val;
                    set(baddr, b);
                    ex = (char)(val >> 16);
                    return;
                }case 4:{ //MUL
                    cycles++;
                    int val = b * a;
                    b = (char)val;
                    set(baddr, b);
                    ex = (char)(val >> 16);
                    return;
                }case 5:{ //MLI
                    cycles++;
                    int val = (short)b * (short)a;
                    b = (char)val;
                    set(baddr, b);
                    ex = (char)(val >> 16);
                    return;
                }case 6:{ //DIV
                    cycles += 2;
                    if (a == 0) {
                        b = ex = 0;
                    } else {
                        set(baddr, (char) (b / a));
                        ex = (char) (((long) b << 16) / a);
                        return;
                    }
                    break;
                }case 7:{ //DVI
                    cycles += 2;
                    if (a == 0) {
                        b = ex = 0;
                    } else {
                        set(baddr, (char) ((short) b / (short) a));
                        ex = (char) ((b << 16) / ((short) a));
                        return;
                    }
                    break;
                }case 8: //MOD
                    cycles += 2;
                    if (a == 0)
                        b = 0;
                    else {
                        b = (char)(b % a);
                    }
                    break;
                case 9: //MDI
                    cycles += 2;
                    if (a == 0)
                        b = 0;
                    else {
                        b = (char)((short)b % (short)a);
                    }
                    break;
                case 10: //AND
                    b = (char)(b & a);
                    break;
                case 11: //BOR
                    b = (char)(b | a);
                    break;
                case 12: //XOR
                    b = (char)(b ^ a);
                    break;
                case 13: { //SHR
                    if(!SHIFT_DISTANCE_5_BITS && a > 31) {
                        set(baddr, (char) 0);
                        ex = (char) 0;
                    } else {
                        set(baddr, (char) (b >>> a));
                        ex = (char) (b << 16 >>> a);
                    }
                    return;
                }
                case 14: { //ASR
                    if(!SHIFT_DISTANCE_5_BITS && a > 31) {
                        a = 31;
                    }
                    set(baddr, (char)((short)b >> a));
                    ex = (char)(b << 16 >> a);
                    return;
                }
                case 15: //SHL
                    if(!SHIFT_DISTANCE_5_BITS && a > 31) {
                        set(baddr, (char) 0);
                        ex = (char) 0;
                    } else {
                        set(baddr, (char) (b << a));
                        ex = (char) (b << a >> 16);
                    }
                    return;
                case 16: //IFB
                    cycles++;
                    if ((b & a) == 0) skip();
                    return;
                case 17: //IFC
                    cycles++;
                    if ((b & a) != 0) skip();
                    return;
                case 18: //IFE
                    cycles++;
                    if (b != a) skip();
                    return;
                case 19: //IFN
                    cycles++;
                    if (b == a) skip();
                    return;
                case 20: //IFG
                    cycles++;
                    if (b <= a) skip();
                    return;
                case 21: //IFA
                    cycles++;
                    if ((short)b <= (short)a) skip();
                    return;
                case 22: //IFL
                    cycles++;
                    if (b >= a) skip();
                    return;
                case 23: //IFU
                    cycles++;
                    if ((short)b >= (short)a) skip();
                    return;
                case 26:{ //ADX
                    cycles+=2;
                    int val = b + a + ex;
                    b = (char)val;
                    set(baddr, b);
                    ex = (char)(val >> 16);
                    return;
                }case 27:{ //SBX
                    cycles+=2;
                    int val = b - a + ex;
                    b = (char)val;
                    set(baddr, b);
                    ex = (char)(val >> 16);
                    return;
                }case 30: //STI
                    cycles++;
                    b = a;
                    set(baddr, b);
                    registers[6]++;
                    registers[7]++;
                    return;
                case 31: //STD
                    cycles++;
                    b = a;
                    set(baddr, b);
                    registers[6]--;
                    registers[7]--;
                    return;
                case 24:
                case 25:
            }
            set(baddr, b);
        }
    }

    private void rememberJump() {
        if(!jumps.containsKey(pc)) {
            jumps.put(pc, new TreeMap<>());
        }
        if(!jumps.get(pc).containsKey(startPC)) {
            jumps.get(pc).put(startPC, 1);
        } else {
            jumps.get(pc).put(startPC, jumps.get(pc).get(startPC) + 1);
        }
    }

    public void trace(String text) {
            out.println(text);
    }

    public final String hex(int v) {
        //return String.valueOf(v);
        return String.format("%04X", v);
    }

    public void interrupt(char a) {
        interrupts[iwp = iwp + 1 & 0xFF] = a;
        if (iwp == ip) isOnFire = true;
    }

    public void tickHardware() {
        synchronized (hardware) {
            for (DCPUHardware aHardware : hardware) {
                aHardware.tick60hz();
            }
        }
    }

    public boolean addHardware(DCPUHardware hw) {
        synchronized (hardware) {
            return hardware.add(hw);
        }
    }

    public boolean removeHardware(DCPUHardware hw) {
        synchronized (hardware) {
            return hardware.remove(hw);
        }
    }

    public List<DCPUHardware> getHardware() {
        //TODO sync elsewhere
        synchronized (hardware) {
            return new ArrayList<DCPUHardware>(hardware);
        }
    }

    public void run() {
        (new Thread() {
            @Override
            public void run() {
                running = true;
                gui.toggleRunButton(true);
                int hz = 100 * khz;
                int cyclesPerFrame = hz / 60 + 1;

                long nsPerFrame = 16666666L;
                long lastShownCycles = cycles;

                while (running) {
                    long nextFrameTime = System.nanoTime() + nsPerFrame;
                    while (!turbo && System.nanoTime() < nextFrameTime) {
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    long cyclesFrameEnd = cycles + cyclesPerFrame;

                    while (cycles < cyclesFrameEnd) {
                        tick();

                        if(stepOverPc == pc) {
                            stepOverPc = -1;
                            running = false;
                            break;
                        }
                        if(breakPoints[pc] && !isSkipping) {
                            running = false;
                            break;
                        }
                    }
                    tickHardware();
                    if(cycles > lastShownCycles + 10000) {
                        registerTableModel.fireTableChanged(new TableModelEvent(registerTableModel, 11));
                        lastShownCycles = cycles;
                    }
                }
                SwingUtilities.invokeLater(() -> {
                    updateDebugger(true);
                    gui.toggleRunButton(false);
                });
            }
        }).start();
    }

    public boolean stepOver() {
        stepOver = true;
        boolean isPcVisible = gui.isLineVisible(debugData.memToLineNum[pc & 0xffff]);
        tick();
        if(stepOverPc == pc) {
            stepOverPc = -1;
            tickHardware();
            updateDebugger(isPcVisible);
            return false;
        }
        if(breakPoints[pc]) {
            tickHardware();
            updateDebugger(true);
            return false;
        }
        run();
        return true;
    }

    public void tickle() {
        boolean isPcVisible = gui.isLineVisible(debugData.memToLineNum[pc & 0xffff]);
        tick();
        tickHardware();
        updateDebugger(isPcVisible);
    }

    public void updateDebugger(boolean updatePc) {
        registerTableModel.fireTableDataChanged();
        hexDumpTableModel.fireTableDataChanged();
        calcTemperatures();
        gui.fireEditorChanged(debugData.memToLineNum[pc & 0xffff]);
        stackTableModel.fireTableChanged(new TableModelEvent(stackTableModel));

        gui.selectEditorLine(debugData.memToLineNum[pc & 0xffff], updatePc);

        watchTableModel.evaluateExpressions();

        if(sp > 0) {
            gui.showStackAddress(65535 - (sp & 0xffff));
        }
    }

    public void loadBinary(File file) throws IOException {
        InputStream is = new FileInputStream(file);
        System.out.println("Loading bootrom: " + file.toString());

        int i = 0;
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(is))) {
            if(file.getName().endsWith(".boot")) {
                // seems like TC boot floppy, skip first sector
                dis.skipBytes(1024);
            }
            for (; i < ram.length; i++) {
                ram[i] = dis.readChar();
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
        } catch (IOException e) {
            for (; i < ram.length; i++) {
                ram[i] = 0;
            }
        }
        is.close();
    }

    public void loadDebugInfo(File binFile) throws IOException {
        String filename = binFile.getAbsolutePath();
        filename = filename.substring(0, filename.lastIndexOf('.'));
        File dbgFile = new File(filename + ".dbg");
        if(dbgFile.isFile()) {
            debugData = DebugData.load(dbgFile);
        } else {
            debugData = DebugData.fromRam(ram);
        }
    }

    private final String[] args;

    public DCPU(String[] args) {
        this.args = args;
    }

    public static void main(String[] args) throws Exception {
        DCPU dcpu = new DCPU(args);

        SwingUtilities.invokeLater(() -> {
            try {
                dcpu.setup();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void setup() throws Exception {
        traceFile.delete();

        File binFile = null;

        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        if(args.length > 0) {
            binFile = new File(args[0]).getAbsoluteFile();
            if(!binFile.isFile()) {
                System.out.println("File not found: " + binFile.getAbsolutePath());
                System.exit(1);
            }

            if(args.length > 1) {
                floppyFile[0] = new File(args[1]).getAbsoluteFile();
                if(!floppyFile[0].isFile()) {
                    System.out.println("Floppy file not found: " + binFile.getAbsolutePath());
                    System.exit(1);
                }
            }
            if(args.length > 2) {
                floppyFile[1] = new File(args[2]).getAbsoluteFile();
                if(!floppyFile[1].isFile()) {
                    System.out.println("Floppy file not found: " + binFile.getAbsolutePath());
                    System.exit(1);
                }
            }
        } else {
            JFileChooser jfc = new JFileChooser();
            jfc.setDialogTitle("Choose DCPU memory image file");
            jfc.setCurrentDirectory(new File(".").getAbsoluteFile());
            int ret = jfc.showOpenDialog(null);
            if(ret == JFileChooser.APPROVE_OPTION) {
                binFile = jfc.getSelectedFile();
            }

            if(binFile == null) {
                System.out.println("File must be specified: " + binFile.getAbsolutePath());
                System.exit(1);
            }
            if(!binFile.isFile()) {
                System.out.println("File not found: " + binFile.getAbsolutePath());
                System.exit(1);
            }


            JFileChooser jfc2 = new JFileChooser();
            jfc2.setDialogTitle("Choose floppy image file");
            jfc2.setCurrentDirectory(new File(".").getAbsoluteFile());
            int ret2 = jfc2.showOpenDialog(null);
            if(ret2 == JFileChooser.APPROVE_OPTION) {
                floppyFile[0] = jfc2.getSelectedFile();

                if(!floppyFile[0].isFile()) {
                    System.exit(1);
                }
            }

            JFileChooser jfc3 = new JFileChooser();
            jfc3.setDialogTitle("Choose floppy image file");
            jfc3.setCurrentDirectory(new File(".").getAbsoluteFile());
            int ret3 = jfc3.showOpenDialog(null);
            if(ret3 == JFileChooser.APPROVE_OPTION) {
                floppyFile[1] = jfc3.getSelectedFile();

                if(!floppyFile[1].isFile()) {
                    System.exit(1);
                }
            }
        }

        loadBinary(binFile);
        loadDebugInfo(binFile);

        lineDisassembler = new DisasmAddr(ram);

        clock.connectTo(this);
        //clockv1.connectTo(this);
        //kbd.connectTo(this);
        asciiKbd.connectTo(this);
        floppy[0].connectTo(this);
        floppy[1].connectTo(this);
        hic.connectTo(this);
        rci.connectTo(this);
        speaker.connectTo(this);

        if(floppyFile[0] != null) {
            InputStream is = new FileInputStream(floppyFile[0]);
            floppy[0].insert(new FloppyDisk(is));
            is.close();
        }
        if(floppyFile[1] != null) {
            InputStream is = new FileInputStream(floppyFile[1]);
            floppy[1].insert(new FloppyDisk(is));
            is.close();
        }

        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        manager.addKeyEventDispatcher(e -> {
            if(view.canvas.isFocusOwner()) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    System.exit(0);
                }
                if (e.getID() == KeyEvent.KEY_PRESSED) {
                    if(kbd.isConnected())
                        kbd.keyPressed(e.getKeyCode(), e.getKeyChar());
                    if(asciiKbd.isConnected())
                        asciiKbd.keyPressed(e.getKeyCode(), e.getKeyChar());
                } else if (e.getID() == KeyEvent.KEY_RELEASED) {
                    if(kbd.isConnected())
                        kbd.keyReleased(e.getKeyCode(), e.getKeyChar());
                    if(asciiKbd.isConnected())
                        asciiKbd.keyReleased(e.getKeyCode(), e.getKeyChar());
                } else if (e.getID() == KeyEvent.KEY_TYPED) {
                    // kbd.keyTyped(e.getKeyCode(), e.getKeyChar());
                }
            }
            return false;
        });

        final VirtualMonitor mon = new VirtualMonitor();
        mon.connectTo(this);

        view = new LEM1802Viewer();
        view.attach(mon);

        gui = new GUI(this);
        gui.init();

        view.canvas.setup();
        updateDebugger(true);

        for (DCPUHardware hw : hardware) {
            hw.powerOn();
        }
    }

    private GUI gui;
    public LEM1802Viewer view;

    public RegisterTableModel registerTableModel = new RegisterTableModel();
    public StackTableModel stackTableModel = new StackTableModel();
    public JumpTableModel jumpTableModel = new JumpTableModel();
    public HexDumpTableModel hexDumpTableModel = new HexDumpTableModel();
    public SectorTableModel[] sectorTableModel = { new SectorTableModel(0), new SectorTableModel(1)};
    public WatchTableModel watchTableModel = new WatchTableModel();

    public int curLineNum() {
        return debugData.memToLineNum[pc];
    }

    public EditorTableModel createEditorTableModel(int start) {
        EditorTableModel etm = new EditorTableModel();
        etm.start = start;
        return etm;
    }

    class RegisterTableModel extends AbstractTableModel {
        @Override
        public int getRowCount() {
            return 12;
        }

        private final String[] cols = new String[] {"Name", "Value", "Memory"};
        public String getColumnName(int column) {
            return cols[column];
        }

        @Override
        public int getColumnCount() {
            return cols.length;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            switch(columnIndex) {
                case 0:
                    if (rowIndex < 8) {
                        return "ABCXYZIJ".substring(rowIndex, rowIndex+1);
                    } else if (rowIndex == 8) {
                        return "SP";
                    } else if (rowIndex == 9) {
                        return "PC";
                    } else if (rowIndex == 10) {
                        return "EX";
                    } else if (rowIndex == 11) {
                        return String.format("%012d", cycles);
                    }
                    break;
                case 1:
                    if (rowIndex < 8) {
                        return String.format("%04X", (int) registers[rowIndex]);
                    } else if (rowIndex == 8) {
                        return String.format("%04X", (int) sp);
                    } else if (rowIndex == 9) {
                        return String.format("%04X", (int) pc);
                    } else if (rowIndex == 10) {
                        return String.format("%04X", (int) ex);
                    } else if (rowIndex == 11) {
                        return String.format("%012d", cycles-cycleStart);
                    }
                    break;
                case 2:
                    if (rowIndex < 8) {
                        return String.format("[%04X]", (int) ram[(int) registers[rowIndex]]);
                    } else if (rowIndex == 8) {
                        return String.format("[%04X]", (int) ram[(int) sp]);
                    } else if (rowIndex == 9) {
                        return String.format("[%04X]", (int) ram[(int) pc]);
                    } else if (rowIndex == 10) {
                        return String.format("[%04X]", (int) ram[(int) ex]);
                    } else if (rowIndex == 11) {
                        return isSkipping ? "Skipping": "Executing";
                    }
                    break;
            }
            return null;
        }
    }

    class WatchTableModel extends AbstractTableModel {
        ArrayList<Watch> watches = new ArrayList<>();

        @Override
        public int getRowCount() {
            return watches.size();
        }

        private final String[] cols = new String[] {"Expr", "Decimal", "Hex"};
        public String getColumnName(int column) {
            return cols[column];
        }

        @Override
        public int getColumnCount() {
            return cols.length;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if(columnIndex == 0) {
                return watches.get(rowIndex).text;
            } else if(columnIndex == 1) {
                Watch w = watches.get(rowIndex);
                if(w.expr == null) {
                    return "NA";
                }
                return watches.get(rowIndex).val;
            } else if(columnIndex == 2) {
                Watch w = watches.get(rowIndex);
                if(w.expr == null) {
                    return "NA";
                }
                return String.format("%04X", watches.get(rowIndex).val);
            }
            return null;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            Watch w = watches.get(rowIndex);
            w.text = (String) aValue;
            try {
                w.expr = new Expr(w.text, DCPU.this);
                w.val = w.expr.eval();
            } catch(RuntimeException ex) {
                w.expr = null;
                w.val = 0;
            }
        }

        public void evaluateExpressions() {
            for(Watch w: watches) {
                if(w.expr != null) {
                    w.eval();
                }
            }
            fireTableDataChanged();
        }

        public void addRow() {
            int[] c = gui.selectedWatches();
            if(c.length == 0) {
                watches.add(new Watch());
            } else {
                for(int i: c) {
                    Watch other = watches.get(i);
                    Watch w = new Watch();
                    w.text = other.text;
                    w.expr = other.expr;
                    w.val = other.val;
                    watches.add(w);
                }
            }
        }

        public void delRow() {
            int[] c = gui.selectedWatches();
            for(int i: c) {
                watches.remove(i);
            }
        }

        class Watch {
            String text;
            Expr expr = new Expr("", DCPU.this);
            int val;

            void eval() {
                val = expr.eval();
            }
        }
    }

    class StackTableModel extends AbstractTableModel {

        @Override
        public int getRowCount() {
            if(sp == 0) return 0;
            return 65536 - (int) sp;
        }

        private final String[] cols = new String[] {"Address", "Value", "Registers", "Label"};
        public String getColumnName(int column) {
            return cols[column];
        }

        @Override
        public int getColumnCount() {
            return cols.length;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if(columnIndex == 0) {
                return String.format("%04X", 65535 - rowIndex);
            } else if(columnIndex == 2) {
                int address = 65535 - rowIndex;
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("SP+%d", address - (sp & 0xffff)));
                for(int i=0; i < registers.length; i++) {
                    if((registers[i] & 0xffff) == address) {
                        sb.append(" A B C X Y Z I J".substring(2*i, 2*i+2));
                    }
                }
                return sb.toString();
            } else if(columnIndex == 1) {
                return String.format("%04X", (int) ram[65535 - rowIndex]);
            } else if(columnIndex == 3) {
                return debugData.memToLabel[ram[65535 - rowIndex]];
            }
            return null;
        }
    }

    class JumpTableModel extends AbstractTableModel {

        public int getFrom(int row) {
            return data.get(row).from;
        }

        private class Data implements Comparable {
            public char from;
            public int count;

            public Data(char from, Integer count) {
                this.from = from;
                this.count = count;
            }

            @Override
            public int compareTo(Object o) {
                Data other = (Data) o;
                return new Integer(other.count).compareTo(count);
            }
        }


        public char targetAddr;
        public ArrayList<Data> data = new ArrayList<>(100);

        public void setTargetAddress(char addr) {
            targetAddr = addr;
            update();
        }

        public void update() {
            data.clear();

            if(jumps.containsKey(targetAddr)) {
                SortedMap<Character, Integer> sm = jumps.get(targetAddr);
                for (Character c : sm.keySet()) {
                    data.add(new Data(c, sm.get(c)));
                }
                Collections.sort(data);
            }

            jumpTableModel.fireTableChanged(new TableModelEvent(jumpTableModel));
        }

        @Override
        public int getRowCount() {
            return data.size();
        }

        private final String[] cols = new String[] {"From", "#", "File", "Label"};
        public String getColumnName(int column) {
            return cols[column];
        }

        @Override
        public int getColumnCount() {
            return cols.length;
        }

        @Override
        public Object getValueAt(int row, int col) {
            if(col == 0) {
                return String.format("%04X", (int) data.get(row).from);
            } else if(col == 1) {
                return data.get(row).count;
            } else if(col == 2) {
                return debugData.lines.get(debugData.memToLineNum[(int) data.get(row).from]).filename;
            } else if(col == 3) {
                return debugData.memToLabel[(int) data.get(row).from];
            }
            return null;
        }
    }

    class HexDumpTableModel extends AbstractTableModel {
        private final int COL_SIZE = 8;

        @Override
        public int getRowCount() {
            return 65536 / COL_SIZE;
        }

        public String getColumnName(int column) {
            switch(column) {
                case 0:
                    return "Mem";
                case 1:
                    return "0/8";
                case 2:
                    return "1/9";
                case 3:
                    return "2/a";
                case 4:
                    return "3/b";
                case 5:
                    return "4/c";
                case 6:
                    return "5/d";
                case 7:
                    return "6/e";
                case 8:
                    return "7/f";
                case 9:
                    return "Text";
            }
            return null;
        }

        @Override
        public int getColumnCount() {
            return COL_SIZE + 2;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if(columnIndex == 0) {
                return String.format("%04X", (int) (rowIndex * COL_SIZE));
            } else if(columnIndex <= COL_SIZE) {
                return String.format("%04X", (int) ram[rowIndex * COL_SIZE + columnIndex - 1]);
            } else {
                StringBuilder sb = new StringBuilder();
                int addr = rowIndex * COL_SIZE;
                for(int i=0; i < COL_SIZE; i++) {
                    sb.append(getChar(ram[addr+i] >> 8));
                    sb.append(getChar(ram[addr+i] >> 0));
                }
                return sb.toString();
            }
        }

        private char getChar(int c) {
            if(c >= 32 && c < 127) {
                return (char) c;
            }
            return '.';
        }
    }

    class SectorTableModel extends AbstractTableModel {
        SectorTableModel(int unit) {
            this.unit = unit;
        }
        private final int COL_SIZE = 16;
        private final int unit;

        @Override
        public int getRowCount() {
            return 512 / COL_SIZE;
        }

        public String getColumnName(int column) {
            if(column == 0) {
                return "Addr";
            } else if(column == COL_SIZE+1) {
                return "Text";
            } {
                return " 0123456789abcdef".substring(column, column+1);
            }
        }

        @Override
        public int getColumnCount() {
            return COL_SIZE + 2;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if(columnIndex == 0) {
                return String.format("%04X", (int) (rowIndex * COL_SIZE));
            } else if(columnIndex <= COL_SIZE) {
                return String.format("%04X", (int) getWord(rowIndex * COL_SIZE + columnIndex - 1));
            } else {
                StringBuilder sb = new StringBuilder();
                int addr = rowIndex * COL_SIZE;
                for(int i=0; i < COL_SIZE; i++) {
                    sb.append(getChar(getWord(addr+i) >> 8));
                    sb.append(getChar(getWord(addr+i) >> 0));
                }
                return sb.toString();
            }
        }

        private char getWord(int index) {
            return floppy[unit].getDisk().data[sector * VirtualFloppyDrive.WORDS_PER_SECTOR + index];
        }

        private char getChar(int c) {
            if(c >= 32 && c < 127) {
                return (char) c;
            }
            return '.';
        }

        private int sector;
        public void setSector(int sector) {
            this.sector = sector;
            fireTableDataChanged();
        }

        public int getSector() {
            return sector;
        }
    }

    private static final Color cold = new Color(0xaf, 0xc7, 0xe4);
    private static final Color hot = new Color(0xdb, 0x94, 0xc2);
    private static Color[] grad = new Color[10];
    static {
        float redStep = (hot.getRed() - cold.getRed()) / 9f;
        float greenStep = (hot.getGreen() - cold.getGreen()) / 9f;
        float blueStep = (hot.getBlue() - cold.getBlue()) / 9f;
        for(int i=0; i < grad.length; i++) {
            grad[i] = new Color(
                    cold.getRed() + (int) (i * redStep),
                    cold.getGreen() + (int) (i * greenStep),
                    cold.getBlue() + (int) (i * blueStep));
        }
    }

    private static final int[] sortedHotSpot = new int[65536];
    private static final int[] hotSpotSamples = new int[10];

    void calcTemperatures() {
        System.arraycopy(memUseCount, 0, sortedHotSpot, 0, 65536);
        Arrays.sort(sortedHotSpot);
        int i = 0;
        while(sortedHotSpot[i] == 0 && i < 65535) i++;

        float step = (65535 - i) / 8f;
        hotSpotSamples[0] = 0;
        for(int j=0; j < 9; j++) {
            hotSpotSamples[j+1] = sortedHotSpot[(int) (i + j * step)];
        }
    }

    Color getTempColor(int temp) {
        if(temp == 0) return grad[0];

        for(int i=1; i < hotSpotSamples.length; i++) {
            if(temp < hotSpotSamples[i]) return grad[i-1];
        }

        return grad[9];
    }

    public class EditorTableModel extends AbstractTableModel {
        int start;

        @Override
        public int getRowCount() {
            return rows;
        }

        @Override
        public int getColumnCount() {
            return cols.length;
        }

        private final String[] cols = new String[] {"Break", "Addr", "Uses", "Source"};
        public String getColumnName(int column) {
            return cols[column];
        }
/*
        public Class getColumnClass(int columnIndex) {
            if(columnIndex >0) return String.class;
            return Boolean.class;
        }
*/
        public boolean isCellEditable(int row, int column) {
            return column == 0;
        }

        @Override
        public void setValueAt(Object aValue, int row, int column) {
            if (aValue instanceof Boolean && column == 0) {
                if(!debugData.lines.get(row+start).mem.isEmpty()) {
                    int address = debugData.lines.get(row+start).mem.get(0);
                    breakPoints[address] = (boolean) aValue;
                    fireTableCellUpdated(row, column);
                }
            }
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if(columnIndex == 1) {
                if(!debugData.lines.get(rowIndex+start).mem.isEmpty()) {
                    return String.format("%04X", debugData.lines.get(rowIndex+start).mem.get(0));
                } else {
                    return null;
                }
            } else if(columnIndex == 2) {
                if(!debugData.lines.get(rowIndex+start).mem.isEmpty()) {
                    return memUseCount[debugData.lines.get(rowIndex+start).mem.get(0)];
                }
                return null;
            } else if(columnIndex == 3) {
                return debugData.lines.get(rowIndex+start).text;
            } else if(columnIndex == 0) {
                if(!debugData.lines.get(rowIndex+start).mem.isEmpty()) {
                    int address = debugData.lines.get(rowIndex+start).mem.get(0);
                    return breakPoints[address];
                } else {
                    return null;
                }
            }
            return null;
        }

        public int rows;
        public void addRow() {
            rows++;
        }
    }
}
