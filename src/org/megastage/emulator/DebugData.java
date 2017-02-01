package org.megastage.emulator;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DebugData {
    public HashMap<String, String> defines = new HashMap<>();
    public HashMap<String, String> labels = new HashMap<>();
    public HashMap<String, String> constants = new HashMap<>();
    public List<LineData> lines = new ArrayList<>(65536);
    public int[] memToLineNum = new int[65536];
    public String[] memToLabel = new String[65536];

    private int curAddr = 0;
    public boolean fromDecompile = false;

    public static DebugData load(File file) throws IOException {
        DebugData me = new DebugData();

        BufferedReader br = new BufferedReader(new FileReader(file));
        me.init(br);
        br.close();

        return me;
    }

    public static DebugData fromRam(char[] ram) throws IOException {
        DebugData me = new DebugData();
        String src = new Disassembler(ram).disassemble();
        BufferedReader br = new BufferedReader(new StringReader(src));

        me.init2(br);
        br.close();

        me.fromDecompile = true;
        return me;
    }

    private void init(BufferedReader br) throws IOException {
        defines = initMap(br);
        labels = initMap(br);

        constants.putAll(labels);
        constants.putAll(defines);

        for(String label: labels.keySet()) {
            String value = labels.get(label);
            memToLabel[Integer.parseInt(value)] = label;
        }

        String label = memToLabel[0] == null ? "MEM_START": memToLabel[0];
        int base = 0;
        for(int i=1; i < memToLabel.length; i++) {
            if(memToLabel[i] == null) {
                if(i - base < 64) {
                    memToLabel[i] = label + "+" + (i - base);
                } else {
                    memToLabel[i] = "";
                }
            } else {
                label = memToLabel[i];
                base = i;
            }

        }
        init2(br);
    }

    private void init2(BufferedReader br) throws IOException {
        while(true) {
            String dataLine = br.readLine();
            if(dataLine == null) break;

            String codeLine = br.readLine();
            LineData ld = new LineData(dataLine, codeLine);
            for(int addr: ld.mem) {
                if(addr<65535) memToLineNum[addr] = lines.size();
                else System.out.println(ld.toString());
            }
            lines.add(ld);
        }
    }

    private HashMap<String, String> initMap(BufferedReader br) throws IOException {
        int numItems = Integer.parseInt(br.readLine());

        HashMap<String, String> map = new HashMap<>();
        for(int i=0; i < numItems; i++) {
            String key = br.readLine();
            String val = br.readLine();
            map.put(key, val);
        }
        return map;
    }

    public class LineData {
        public String filename;
        public int lineNum;
        public String text;
        public List<Integer> mem;

        public String toString() {
            return text + " " + mem.toString() + " " + mem.size();
        }

        public LineData(char[] ram, int addr) {
            filename = "";
        }

        public LineData(String data, String code) throws IOException {
            String[] pieces = data.split(",");

            this.filename = pieces[0];
            this.lineNum  = Integer.parseInt(pieces[1]);

            int len = Integer.parseInt(pieces[2]);

            mem = new ArrayList<>(len);
            for(int i=0; i < len; i++) {
                String[] item = pieces[3+i].split(":");

                int addr = Integer.parseInt(item[0], 16);
                int val  = Integer.parseInt(item[1], 16);

                if(addr == curAddr) {
                    curAddr++;
                    mem.add(addr);
                }
            }

            this.text = code;
        }
    }
}
