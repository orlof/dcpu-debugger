package org.megastage.emulator;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.*;

public class GUI {
    private final DCPU dcpu;

    private JFrame mainFrame;
    private JTable stackTable;
    private JTable jumpTable;
    private JTable hexDumpTable;
    private JTable[] sectorTable;
    private JTable watchTable;
//    private JTable editorTable;
    private JTable registerTable;

    private JToggleButton runButton;
    private JToggleButton turboButton;
    private JToggleButton traceButton;
    private JLabel[] floppyLabel = new JLabel[2];

    private JSplitPane codeHexSplitPane;
    private JSplitPane lemSplitPane;
    private JTabbedPane tabbedPane;
    private JPanel lem;
    private JLabel traceLabel;

    public GUI(DCPU dcpu) {
        this.dcpu = dcpu;
        this.stackTable = new JTable(dcpu.stackTableModel);
        this.jumpTable = new JTable(dcpu.jumpTableModel);
        this.hexDumpTable = new JTable(dcpu.hexDumpTableModel);
        this.sectorTable = new JTable[] { new JTable(dcpu.sectorTableModel[0]), new JTable(dcpu.sectorTableModel[1])};
        //this.editorTable = new EditorTable(dcpu.editorTableModel);
        this.watchTable = new WatchTable(dcpu.watchTableModel);
        this.registerTable = new JTable(dcpu.registerTableModel);
        this.tabbedPane = new JTabbedPane();
    }

    public void init() {
        mainFrame = new JFrame("Megastage DCPU Debugger");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, createUpperPanel(), createLowerPanel());
        //SwingUtils.setDividerLocation(splitPane, 500);
        splitPane.setResizeWeight(0.5);

        mainFrame.getContentPane().add(splitPane);
        //f.getContentPane().add(createUpperPanel(dcpu.view), BorderLayout.CENTER);
        //f.getContentPane().add(createLowerPanel(), BorderLayout.PAGE_END);

        mainFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        mainFrame.setVisible(true);
        mainFrame.createBufferStrategy(2);
        mainFrame.pack();
    }

    private JComponent createUpperPanel() {
        lemSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createHwPanel(), createEditorPanel());
        lemSplitPane.setResizeWeight(0.25);
        SwingUtils.setDividerLocation(lemSplitPane, 0.25);
        return lemSplitPane;
    }

    private JComponent createHwPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.add(createLemPanel(), BorderLayout.CENTER);
        p.add(createFloppiesPanel(), BorderLayout.PAGE_END);
        return p;
    }

    private Component createFloppiesPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.add(createFloppyPanel(0), BorderLayout.PAGE_START);
        p.add(createFloppyPanel(1), BorderLayout.PAGE_END);
        return p;
    }

    public void updateFloppyName(int unit) {
        if(dcpu.floppyFile[unit] == null) {
            floppyLabel[unit].setText("No disc");
        } else {
            String filename = dcpu.floppyFile[unit].getName();
            floppyLabel[unit].setText(filename);
        }
    }

    private Component createFloppyPanel(int unit) {
        JPanel mainPanel = new JPanel(new GridLayout(1, 1));

        JPanel filePanel = new JPanel(new FlowLayout());

        floppyLabel[unit] = new JLabel();
        updateFloppyName(unit);
        filePanel.add(floppyLabel[unit]);

//        mainPanel.add(createJButton("Eject", e -> dcpu.floppy.eject()));
        filePanel.add(createJButton("Load", e -> {
            JFileChooser jfc = new JFileChooser();
            jfc.setDialogTitle("Choose floppy image file");
            jfc.setCurrentDirectory(new File(".").getAbsoluteFile());
            int ret = jfc.showOpenDialog(mainFrame);
            if (ret == JFileChooser.APPROVE_OPTION) {
                File floppyFile = jfc.getSelectedFile();

                if (floppyFile.isFile()) {
                    dcpu.floppyFile[unit] = floppyFile;
                    try {
                        InputStream is = new FileInputStream(floppyFile);
                        dcpu.floppy[unit].insert(new FloppyDisk(is));
                        is.close();
                        updateFloppyName(unit);
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                } else {
                    dcpu.floppyFile[unit] = null;
                    dcpu.floppy[unit].eject();
                    updateFloppyName(unit);
                }
            } else {
                dcpu.floppyFile[unit] = null;
                dcpu.floppy[unit].eject();
                updateFloppyName(unit);
            }
        }));
        filePanel.add(createJButton("Save", e -> {
            if(dcpu.floppyFile[unit] != null) {
                try {
                    dcpu.floppy[unit].getDisk().save(dcpu.floppyFile[unit]);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }));
        filePanel.add(createJButton("Edit", e -> {
            if(dcpu.floppyFile[unit] != null) {
                JFrame diskEditorFrame = new JFrame("Disk Editor");
                diskEditorFrame.getContentPane().add(createDiskEditorControlPanel(unit), BorderLayout.NORTH);
                diskEditorFrame.getContentPane().add(createSectorPanel(unit), BorderLayout.CENTER);
                diskEditorFrame.setVisible(true);
                diskEditorFrame.pack();
            }
        }));
        filePanel.add(createJButton("New", e -> {
            JFileChooser jfc = new JFileChooser();
            jfc.setDialogTitle("Choose floppy image file");
            jfc.setCurrentDirectory(new File(".").getAbsoluteFile());
            int ret = jfc.showSaveDialog(mainFrame);
            if (ret == JFileChooser.APPROVE_OPTION) {

                dcpu.floppyFile[unit] = jfc.getSelectedFile();
                try {
                    InputStream is = new ByteArrayInputStream(new byte[0]);
                    dcpu.floppy[unit].insert(new FloppyDisk(is));
                    is.close();
                    dcpu.floppy[unit].getDisk().save(dcpu.floppyFile[unit]);
                    updateFloppyName(unit);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }));

        mainPanel.add(filePanel);
        return mainPanel;
    }

    private JComponent createSectorPanel(int unit) {
        JPanel sectorPanel = new JPanel();
        sectorPanel.setLayout(new BorderLayout());

        Font font = new Font("monospaced", Font.PLAIN, 8);
        //int charWidth = hexDumpTable.getFontMetrics(font).charWidth('f');
        sectorTable[unit].setFont(font);
        //editorTable.setDefaultRenderer(String.class, new MultiLineTableCellRenderer());
        sectorTable[unit].setFillsViewportHeight(true);
        sectorTable[unit].setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        int numCols = dcpu.sectorTableModel[unit].getColumnCount();
        for(int i=1; i < numCols-1; i++) {
            TableColumn col = sectorTable[unit].getColumnModel().getColumn(i);
            //col.setMinWidth(40);
            col.setPreferredWidth(40);
        }

        sectorTable[unit].getColumnModel().getColumn(0).setPreferredWidth(40);
        sectorTable[unit].getColumnModel().getColumn(numCols-1).setPreferredWidth(200);

        sectorPanel.add(sectorTable[unit], BorderLayout.CENTER);
        sectorPanel.add(sectorTable[unit].getTableHeader(), BorderLayout.NORTH);

        return sectorPanel;
    }

    public JTextField sectorTextField;
    public JLabel sectorLabel;

    private JComponent createDiskEditorControlPanel(int unit) {
        JPanel panel = new JPanel();
        panel.add(createJButton("-", e -> {
            updateSector(unit, dcpu.sectorTableModel[unit].getSector() - 1);
        }));
        panel.add(createJButton("+", e -> {
            updateSector(unit, dcpu.sectorTableModel[unit].getSector() + 1);
        }));

        sectorTextField = new JTextField(4);
        sectorTextField.addActionListener(e -> {
            int sector = Integer.parseInt(((JTextField) e.getSource()).getText());
            updateSector(unit, sector);
        });
        panel.add(sectorTextField);

        sectorLabel = new JLabel();
        panel.add(sectorLabel);

        updateSector(unit, dcpu.sectorTableModel[unit].getSector());

        return panel;
    }

    public void updateSector(int unit, int sector) {
        if(sector < 0) sector = 1439;
        if(sector > 1439) sector = 0;
        sectorTextField.setText(String.valueOf(sector));
        sectorLabel.setText(String.format("%04X", sector));
        dcpu.sectorTableModel[unit].setSector(sector);
    }

    private JComponent createLemPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BorderLayout());
        lem = new JPanel(new BorderLayout());
        lem.setBorder(BorderFactory.createLineBorder(Color.lightGray,2,true));
        dcpu.view.canvas.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                lem.setBorder(BorderFactory.createLineBorder(Color.BLUE, 2, true));
            }

            @Override
            public void focusLost(FocusEvent e) {
                lem.setBorder(BorderFactory.createLineBorder(Color.lightGray, 2, true));
            }
        });
        lem.add(dcpu.view.canvas, BorderLayout.CENTER);
        p.add(lem, BorderLayout.CENTER);
        p.add(createControlPanel(), BorderLayout.PAGE_END);
        return p;
    }

    private JPanel createControlPanel() {
        JPanel controlButtonPanel = new JPanel();
        //controlButtonPanel.setLayout(new GridLayout(1, 4));
        controlButtonPanel.add(createJButton("Step", e -> {
            if (!dcpu.running) {
                dcpu.tickle();
            }
        }));

        controlButtonPanel.add(createJButton("Over", e -> {
            if (!dcpu.running) {
                dcpu.stepOver();
            }
        }));

        runButton = new JToggleButton("Run");
        runButton.addActionListener(e -> {
            if (runButton.isSelected()) {
                dcpu.running = true;
                dcpu.run();
            } else {
                dcpu.running = false;
            }
        });
        controlButtonPanel.add(runButton);

        turboButton = new JToggleButton("Turbo");
        turboButton.addActionListener(e -> {
            if(turboButton.isSelected()) {
                dcpu.turbo = true;
            } else {
                dcpu.turbo = false;
            }
        });
        controlButtonPanel.add(turboButton);

        controlButtonPanel.add(createJButton("Focus", e -> {
            selectEditorLine(dcpu.curLineNum(), true);
        }));

        controlButtonPanel.add(createJButton("Reset", e -> {
            for(int i=0;i<dcpu.memUseCount.length; i++) dcpu.memUseCount[i] = 0;
            dcpu.jumps.clear();
            dcpu.jumpTableModel.update();
            dcpu.cycleStart = dcpu.cycles;
            dcpu.updateDebugger(true);
            //dcpu.registerTableModel.fireTableChanged(new TableModelEvent(dcpu.registerTableModel, 11));
        }));

        if(dcpu.debugData.fromDecompile) {
            controlButtonPanel.add(createJButton("Disasm", e -> {
                try {
                    char[] mem = new char[65536];
                    System.arraycopy(dcpu.ram, 0, mem, 0, 65536);
                    dcpu.debugData = DebugData.fromRam(mem);
                    updateEditorData();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }));
        }

        traceButton = new JToggleButton("Trace");
        traceButton.addActionListener(e -> {
            if(traceButton.isSelected()) {
                try {
                    dcpu.out = new PrintWriter(new BufferedWriter(new FileWriter(dcpu.traceFile, true)));
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                dcpu.trace = true;
            } else {
                dcpu.trace = false;
                dcpu.out.close();
            }
        });
        controlButtonPanel.add(traceButton);

        controlButtonPanel.add(createJButton("Dump", e -> {
            File file = new File(System.getProperty("user.dir"), "dcpu-dump.log");
            file.delete();
            try {
                PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file, true)));
                for(int i=0; i < dcpu.ram.length; i++) {
                    if(i % 16 == 0) {
                        if(i!=0) pw.println();
                        pw.format("%04X", i);
                    }
                    pw.format(" %04X", (int) dcpu.ram[i]);
                }
                pw.println();
                pw.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }));

        return controlButtonPanel;
    }

    private JComponent createEditorPanel() {
        codeHexSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createCodePanel(), createHexDumpPanel());
        codeHexSplitPane.setResizeWeight(0.60);
        SwingUtils.setDividerLocation(codeHexSplitPane, 0.60);
        return codeHexSplitPane;
    }


    EditorData[] editorData;

    private class EditorData {
        String filename;
        EditorTable table;
        DCPU.EditorTableModel model;
        int start, index;
    }

    public void fireEditorChanged(int lineNum) {
        EditorData ed = editorData[lineNum];
        ed.model.fireTableDataChanged();
    }

    private JComponent createCodePanel() {
        editorData = new EditorData[dcpu.debugData.lines.size()];

        String file = null;
        EditorData edt = null;
        int count = 0;

        for(int i=0; i < dcpu.debugData.lines.size(); i++) {
            DebugData.LineData ld = dcpu.debugData.lines.get(i);
            if(!ld.filename.equals(file)) {
                if(edt != null) {
                    edt.model.fireTableChanged(new TableModelEvent(edt.model));
                }
                file = ld.filename;
                DCPU.EditorTableModel model = dcpu.createEditorTableModel(i);
                EditorTable editorTable = createEditorTable(model);
                edt = new EditorData();
                edt.filename = ld.filename;
                edt.table = editorTable;
                edt.model = model;
                edt.start = i;
                edt.index = count++;
                JScrollPane scrollPane = new JScrollPane(editorTable);
                tabbedPane.add(file, scrollPane);
            }
            editorData[i] = edt;
            edt.model.addRow();
        }

        return tabbedPane;
    }

    private void updateEditorData() {
        EditorData edt = editorData[0];

        editorData = new EditorData[dcpu.debugData.lines.size()];

        for(int i=0; i < dcpu.debugData.lines.size(); i++) {
            editorData[i] = edt;
        }

        edt.model.rows = dcpu.debugData.lines.size();
        edt.model.fireTableDataChanged();
    }

    private EditorTable createEditorTable(DCPU.EditorTableModel etm) {
        EditorTable editorTable = new EditorTable(etm);
        editorTable.setFont(new Font("monospaced", Font.PLAIN, 10));
        editorTable.setDefaultRenderer(String.class, new MultiLineTableCellRenderer());
        editorTable.setFillsViewportHeight(true);
        editorTable.getColumnModel().getColumn(0).setMinWidth(40);
        editorTable.getColumnModel().getColumn(0).setMaxWidth(40);
        editorTable.getColumnModel().getColumn(1).setMinWidth(40);
        editorTable.getColumnModel().getColumn(1).setMaxWidth(40);
        editorTable.getColumnModel().getColumn(2).setMinWidth(50);
        editorTable.getColumnModel().getColumn(2).setMaxWidth(50);
        ColorRenderer renderer = new ColorRenderer(etm.start);
        renderer.setHorizontalAlignment(JLabel.RIGHT);
        editorTable.getColumnModel().getColumn(2).setCellRenderer(renderer);
        editorTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        editorTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                int row = editorTable.rowAtPoint(evt.getPoint());
                int col = editorTable.columnAtPoint(evt.getPoint());

                if (row >= 0 && col > 0) {
                    dcpu.jumpTableModel.setTargetAddress((char) (dcpu.debugData.lines.get(row + etm.start).mem.get(0)).intValue());
                }
            }
        });

        return editorTable;
    }

    private JComponent createHexDumpPanel() {
        Font font = new Font("monospaced", Font.PLAIN, 8);
        //int charWidth = hexDumpTable.getFontMetrics(font).charWidth('f');
        hexDumpTable.setFont(font);
        //editorTable.setDefaultRenderer(String.class, new MultiLineTableCellRenderer());
        hexDumpTable.setFillsViewportHeight(true);
        hexDumpTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        int numCols = hexDumpTable.getColumnCount();
        for(int i=1; i < numCols-1; i++) {
            TableColumn col = hexDumpTable.getColumnModel().getColumn(i);
            col.setPreferredWidth(30);
        }

        hexDumpTable.getColumnModel().getColumn(0).setPreferredWidth(35);
        hexDumpTable.getColumnModel().getColumn(numCols-1).setPreferredWidth(120);

        JScrollPane scrollPane = new JScrollPane(hexDumpTable);
        return scrollPane;
    }

    private JComponent createLowerPanel() {
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new GridLayout(1, 4));

        bottomPanel.add(createRegisterPanel());
        bottomPanel.add(createStackPanel());
        bottomPanel.add(createJumpPanel());
        bottomPanel.add(createWatchPanel());

        return bottomPanel;
    }

    private JComponent createRegisterPanel() {
        JPanel registerPanel = new JPanel();
        registerPanel.setLayout(new BorderLayout());

        registerTable.setFont(new Font("monospaced", Font.PLAIN, 10));
        registerTable.setFillsViewportHeight(true);
        registerTable.setRowSelectionAllowed(false);
        registerTable.setDefaultRenderer(Object.class, new NoFocusBorderRenderer());

        registerPanel.add(registerTable, BorderLayout.CENTER);
        registerPanel.add(registerTable.getTableHeader(), BorderLayout.NORTH);

        return registerPanel;
    }

    private JComponent createWatchPanel() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout());

        JPanel ctrlPanel = new JPanel();
        ctrlPanel.setLayout(new GridLayout(1, 2));

        ctrlPanel.add(createJButton("New", e -> {
            dcpu.watchTableModel.addRow();
            dcpu.watchTableModel.fireTableChanged(new TableModelEvent(dcpu.watchTableModel));
        }));
        ctrlPanel.add(createJButton("Del", e -> {
            dcpu.watchTableModel.delRow();
            dcpu.watchTableModel.fireTableChanged(new TableModelEvent(dcpu.watchTableModel));
        }));
        mainPanel.add(ctrlPanel, BorderLayout.PAGE_END);

        watchTable.setFont(new Font("monospaced", Font.PLAIN, 10));
        watchTable.setFillsViewportHeight(true);
        // watchTable.setRowSelectionAllowed(false);
        watchTable.setDefaultRenderer(Object.class, new NoFocusBorderRenderer());
        watchTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        //editorTable.getColumnModel().getColumn(0).setMaxWidth(50);
        watchTable.getColumnModel().getColumn(1).setMinWidth(60);
        watchTable.getColumnModel().getColumn(1).setMaxWidth(60);
        watchTable.getColumnModel().getColumn(2).setMinWidth(40);
        watchTable.getColumnModel().getColumn(2).setMaxWidth(40);
        watchTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        JScrollPane scrollPane = new JScrollPane(watchTable);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        return mainPanel;
    }

    private JComponent createStackPanel() {
        stackTable.setFont(new Font("monospaced", Font.PLAIN, 10));
        stackTable.setFillsViewportHeight(true);
        stackTable.setRowSelectionAllowed(false);
        stackTable.setDefaultRenderer(Object.class, new NoFocusBorderRenderer());
        stackTable.getColumnModel().getColumn(0).setMinWidth(60);
        stackTable.getColumnModel().getColumn(0).setMaxWidth(60);
        stackTable.getColumnModel().getColumn(1).setMinWidth(40);
        stackTable.getColumnModel().getColumn(1).setMaxWidth(40);
        stackTable.getColumnModel().getColumn(2).setPreferredWidth(80);

        stackTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        JScrollPane scrollPane = new JScrollPane(stackTable);

        return scrollPane;
    }

    private JComponent createJumpPanel() {
        jumpTable.setFont(new Font("monospaced", Font.PLAIN, 10));
        jumpTable.setFillsViewportHeight(true);
        jumpTable.setRowSelectionAllowed(true);
        jumpTable.setDefaultRenderer(Object.class, new NoFocusBorderRenderer());
        jumpTable.getColumnModel().getColumn(0).setMinWidth(60);
        jumpTable.getColumnModel().getColumn(0).setMaxWidth(60);
        jumpTable.getColumnModel().getColumn(1).setMinWidth(40);
        jumpTable.getColumnModel().getColumn(1).setMaxWidth(40);
        jumpTable.getColumnModel().getColumn(2).setPreferredWidth(80);

        jumpTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        jumpTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                int row = jumpTable.rowAtPoint(evt.getPoint());
                int col = jumpTable.columnAtPoint(evt.getPoint());

                if (row >= 0) {
                    selectEditorLine(dcpu.debugData.memToLineNum[dcpu.jumpTableModel.getFrom(row)], true);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(jumpTable);

        return scrollPane;
    }

    private JButton createJButton(String label, ActionListener listener) {
        JButton button = new JButton(label);
        button.addActionListener(listener);
        return button;
    }

    public boolean isLineVisible(int lineNum) {
        DebugData.LineData ld = dcpu.debugData.lines.get(lineNum);
        EditorData ed = editorData[lineNum];
        return tabbedPane.getSelectedIndex()==ed.index && SwingUtils.isCellVisible(ed.table, lineNum-ed.start, 0);
    }

    public void selectEditorLine(int lineNum, boolean scroll) {
        DebugData.LineData ld = dcpu.debugData.lines.get(lineNum);
        EditorData ed = editorData[lineNum];

        ed.table.clearSelection();
        if(scroll) {
            tabbedPane.setSelectedIndex(ed.index);
            SwingUtils.scrollToCenter(ed.table, lineNum-ed.start, 0);
        }
        ed.table.setRowSelectionInterval(lineNum - ed.start, lineNum - ed.start);
    }

    public void showStackAddress(int address) {
        SwingUtils.scroll(stackTable, address);
    }

    public int[] selectedWatches() {
        return watchTable.getSelectedRows();
    }

    public void toggleRunButton(boolean newState) {
        runButton.setSelected(newState);
        if(newState) {
            runButton.setText("Pause");
        } else {
            runButton.setText("Run");
        }
    }

    class ColorRenderer extends DefaultTableCellRenderer {
        private final int start;

        public ColorRenderer(int start) {
            this.start = start;
        }

        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component cellComponent = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if(!dcpu.debugData.lines.get(row + start).mem.isEmpty()) {
                int address = dcpu.debugData.lines.get(row + start).mem.get(0);
                int temp = dcpu.memUseCount[address];

                cellComponent.setBackground(dcpu.getTempColor(temp));
            } else {
                cellComponent.setBackground(Color.white);
            }
            return cellComponent;
        }
    }

    private class WatchTable extends JTable {
        public WatchTable(TableModel tableModel) {
            super(tableModel);
        }

        public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) {
            //Always toggle on single selection
            super.changeSelection(rowIndex, columnIndex, !extend, extend);
        }
    }

    private class EditorTable extends JTable {
        public EditorTable(TableModel tableModel) {
            super(tableModel);
        }

        public void changeSelection(int row, int column, boolean toggle, boolean extend) {
        }

        @Override
        public TableCellRenderer getCellRenderer(int row, int column) {
            if(getValueAt(row, column) instanceof Boolean) {
                return super.getDefaultRenderer(Boolean.class);
            } else {
                return super.getCellRenderer(row, column);
            }
        }

        @Override
        public TableCellEditor getCellEditor(int row, int column) {
            if(getValueAt(row, column) instanceof Boolean) {
                return super.getDefaultEditor(Boolean.class);
            } else {
                return super.getCellEditor(row, column);
            }
        }
    };


}
