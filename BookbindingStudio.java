/*
 * BookbindingStudio.java
 *
 * Bookbinding Studio — design a hand-bound book and get everything you need
 * at the bench: a true-scale punching template, an animated step-by-step
 * sewing diagram, the thread length, board and cover cutting sizes, and a
 * page-imposition map for printing your own text block.
 *
 * Bindings: pamphlet stitch, kettle stitch (case binding), long-stitch,
 * Coptic, and Japanese stab binding (yotsume family).
 *
 * Single file, no dependencies. Requires Java 17 or later.
 *
 *     java BookbindingStudio.java
 *
 * Author: Mario Gianota
 */

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import java.io.*;
import java.util.*;
import java.util.List;

// ============================================================================
//  Main window
// ============================================================================

public class BookbindingStudio extends JFrame {

    public static void main(String[] args) {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", "Bookbinding Studio");
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) { }
            new BookbindingStudio().setVisible(true);
        });
    }

    // ---- spec controls ----
    private final JComboBox<StyleKind> styleBox = new JComboBox<>(StyleKind.values());
    private final JTextArea styleBlurb = new JTextArea(3, 18);
    private final JComboBox<PaperSize> paperBox = new JComboBox<>(PaperSize.values());
    private final JComboBox<String> orientBox = new JComboBox<>(new String[] {
            "Landscape sheet → upright book", "Portrait sheet → tall book" });
    private final JSpinner customW = dspin(300, 50, 1000, 1);
    private final JSpinner customH = dspin(210, 50, 1000, 1);
    private final JComboBox<Grain> grainBox = new JComboBox<>(Grain.values());
    private final JLabel grainWarn = new JLabel(" ");
    private final JSpinner gsm = ispin(90, 50, 300, 5);
    private final JSpinner sheets = ispin(4, 1, 12, 1);
    private final JSpinner sigs = ispin(6, 2, 40, 1);
    private final JSpinner stations = ispin(6, 4, 12, 2);
    private final JSpinner margin = dspin(15, 5, 60, 1);
    private final JSpinner inset = dspin(10, 4, 30, 1);
    private final JComboBox<ThreadType> threadBox = new JComboBox<>(ThreadType.values());
    private final JSpinner board = dspin(2.0, 0.5, 4.0, 0.5);
    private final JSpinner square = dspin(3, 0, 8, 0.5);
    private final JSpinner turnIn = dspin(15, 8, 25, 1);
    private final JComboBox<Units> unitsBox = new JComboBox<>(Units.values());
    private final JTextField titleField = new JTextField("Notebook", 12);
    private final JButton coverBtn = new JButton("Cover");
    private final JButton threadBtn = new JButton("Thread");
    private final JLabel sheetsLabel = new JLabel("Sheets per signature");
    private final JLabel sigsLabel = new JLabel("Signatures");
    private final JLabel insetLabel = new JLabel("Hole inset");
    private final JLabel status = new JLabel(" ");
    private Color coverColor = new Color(0x2F4F6F);
    private Color threadColor = new Color(0xC0392B);

    // ---- views ----
    private final PreviewPanel preview = new PreviewPanel();
    private final SignaturesPanel sigPanel = new SignaturesPanel();
    private final TemplatePanel template = new TemplatePanel();
    private final SewingPanel sewing = new SewingPanel();
    private final CoverPanel cover = new CoverPanel();
    private final MaterialsPanel materials = new MaterialsPanel();
    private final StudioPanel[] panels = { preview, sigPanel, template, sewing, cover, materials };
    private final JTabbedPane tabs = new JTabbedPane();

    private boolean updating;
    private StyleKind currentKind = StyleKind.KETTLE;
    private BookSpec spec;
    private Geometry geo;
    private File currentFile;

    public BookbindingStudio() {
        super("Bookbinding Studio");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        tabs.addTab("Preview", preview);
        tabs.addTab("Signatures", sigPanel);
        tabs.addTab("Template", template);
        tabs.addTab("Sewing", sewing);
        tabs.addTab("Cover & Boards", cover);
        tabs.addTab("Materials", materials);

        JScrollPane specScroll = new JScrollPane(buildSpecPanel(),
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        specScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(0xCFC6B8)));
        specScroll.getVerticalScrollBar().setUnitIncrement(16);

        status.setBorder(new EmptyBorder(5, 10, 5, 10));
        status.setFont(status.getFont().deriveFont(Font.PLAIN, 12.5f));

        JPanel root = new JPanel(new BorderLayout());
        root.add(specScroll, BorderLayout.WEST);
        root.add(tabs, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xCFC6B8)));
        south.add(status, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);
        setJMenuBar(buildMenus());

        setControls(BookSpec.defaults());
        setSize(1320, 860);
        setMinimumSize(new Dimension(1000, 680));
        setLocationRelativeTo(null);
    }

    // ---------------------------------------------------------------- spec UI

    private JPanel buildSpecPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new EmptyBorder(10, 12, 12, 12));
        int[] row = { 0 };

        section(p, row, "Binding");
        full(p, row, styleBox);
        styleBlurb.setLineWrap(true);
        styleBlurb.setWrapStyleWord(true);
        styleBlurb.setEditable(false);
        styleBlurb.setOpaque(false);
        styleBlurb.setFont(styleBlurb.getFont().deriveFont(Font.ITALIC, 11.5f));
        styleBlurb.setBorder(new EmptyBorder(2, 2, 4, 2));
        full(p, row, styleBlurb);

        section(p, row, "Paper");
        pair(p, row, new JLabel("Sheet size"), paperBox);
        full(p, row, orientBox);
        pair(p, row, new JLabel("Custom width (mm)"), customW);
        pair(p, row, new JLabel("Custom height (mm)"), customH);
        pair(p, row, new JLabel("Grain"), grainBox);
        grainWarn.setFont(grainWarn.getFont().deriveFont(Font.BOLD, 11.5f));
        full(p, row, grainWarn);
        pair(p, row, new JLabel("Weight (gsm)"), gsm);

        section(p, row, "Text block");
        pair(p, row, sheetsLabel, sheets);
        pair(p, row, sigsLabel, sigs);
        pair(p, row, new JLabel("Sewing stations"), stations);
        pair(p, row, new JLabel("Head/tail margin (mm)"), margin);
        pair(p, row, insetLabel, inset);
        pair(p, row, new JLabel("Thread"), threadBox);

        section(p, row, "Cover");
        pair(p, row, new JLabel("Board thickness (mm)"), board);
        pair(p, row, new JLabel("Squares (mm)"), square);
        pair(p, row, new JLabel("Turn-ins (mm)"), turnIn);
        pair(p, row, new JLabel("Title"), titleField);
        JPanel colours = new JPanel(new GridLayout(1, 2, 6, 0));
        colours.setOpaque(false);
        colours.add(coverBtn);
        colours.add(threadBtn);
        full(p, row, colours);

        section(p, row, "Display");
        pair(p, row, new JLabel("Units"), unitsBox);

        GridBagConstraints g = new GridBagConstraints();
        g.gridy = row[0]; g.weighty = 1; g.gridwidth = 2; g.fill = GridBagConstraints.BOTH;
        p.add(Box.createGlue(), g);

        // listeners
        styleBox.addActionListener(e -> { if (!updating) onStyleChanged(); });
        for (JComboBox<?> c : List.of(paperBox, orientBox, grainBox, threadBox, unitsBox))
            c.addActionListener(e -> refresh());
        for (JSpinner s : List.of(customW, customH, gsm, sheets, sigs, stations, margin, inset, board, square, turnIn))
            s.addChangeListener(e -> refresh());
        titleField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refresh(); }
            public void removeUpdate(DocumentEvent e) { refresh(); }
            public void changedUpdate(DocumentEvent e) { refresh(); }
        });
        coverBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, "Cover colour", coverColor);
            if (c != null) { coverColor = c; refresh(); }
        });
        threadBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, "Thread colour", threadColor);
            if (c != null) { threadColor = c; refresh(); }
        });
        return p;
    }

    private static void section(JPanel p, int[] row, String title) {
        JLabel l = new JLabel(title.toUpperCase(Locale.UK));
        l.setFont(l.getFont().deriveFont(Font.BOLD, 11f));
        l.setForeground(new Color(0x8A5A2B));
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.gridy = row[0]++; g.gridwidth = 2; g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(row[0] == 1 ? 0 : 12, 0, 4, 0);
        p.add(l, g);
    }

    private static void pair(JPanel p, int[] row, JComponent label, JComponent c) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.gridy = row[0]; g.anchor = GridBagConstraints.WEST; g.insets = new Insets(2, 0, 2, 8);
        p.add(label, g);
        g = new GridBagConstraints();
        g.gridx = 1; g.gridy = row[0]++; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1; g.insets = new Insets(2, 0, 2, 0);
        p.add(c, g);
    }

    private static void full(JPanel p, int[] row, JComponent c) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.gridy = row[0]++; g.gridwidth = 2; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1;
        g.insets = new Insets(2, 0, 2, 0);
        p.add(c, g);
    }

    private static JSpinner ispin(int v, int min, int max, int step) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(v, min, max, step));
        ((JSpinner.DefaultEditor) s.getEditor()).getTextField().setColumns(5);
        return s;
    }

    private static JSpinner dspin(double v, double min, double max, double step) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(v, min, max, step));
        ((JSpinner.DefaultEditor) s.getEditor()).getTextField().setColumns(5);
        return s;
    }

    private static int ival(JSpinner s) { return ((Number) s.getValue()).intValue(); }
    private static double dval(JSpinner s) { return ((Number) s.getValue()).doubleValue(); }

    /** Re-range the style-dependent spinners, keeping the current values when they're valid. */
    private void onStyleChanged() {
        StyleKind kind = (StyleKind) styleBox.getSelectedItem();
        BindingStyle b = kind.impl;
        BindingStyle old = currentKind.impl;
        int sh = ival(sheets), sg = ival(sigs), st = ival(stations);
        if (b.usesSignatures() != old.usesSignatures() || sh > b.maxSheets()) sh = b.defaultSheets();
        if (sg < b.minSignatures() || sg > b.maxSignatures() || old.minSignatures() == old.maxSignatures()) sg = b.defaultSignatures();
        if (st < b.minStations() || st > b.maxStations() || (st - b.minStations()) % b.stationStep() != 0) st = b.defaultStations();
        applyStyleModels(kind, sh, sg, st);
        refresh();
    }

    private void applyStyleModels(StyleKind kind, int sh, int sg, int st) {
        boolean was = updating;
        updating = true;
        BindingStyle b = kind.impl;
        currentKind = kind;
        sheets.setModel(new SpinnerNumberModel(clamp(sh, 1, b.maxSheets()), 1, b.maxSheets(), 1));
        sigs.setModel(new SpinnerNumberModel(clamp(sg, b.minSignatures(), b.maxSignatures()),
                b.minSignatures(), b.maxSignatures(), 1));
        stations.setModel(new SpinnerNumberModel(clamp(st, b.minStations(), b.maxStations()),
                b.minStations(), b.maxStations(), b.stationStep()));
        for (JSpinner s : List.of(sheets, sigs, stations))
            ((JSpinner.DefaultEditor) s.getEditor()).getTextField().setColumns(5);
        sheetsLabel.setText(b.usesSignatures() ? "Sheets per signature" : "Folded leaves");
        sigs.setEnabled(b.minSignatures() != b.maxSignatures());
        sigsLabel.setEnabled(sigs.isEnabled());
        inset.setEnabled(b.usesInset());
        insetLabel.setEnabled(b.usesInset());
        insetLabel.setText(kind == StyleKind.COPTIC ? "Board hole inset (mm)" : "Hole inset (mm)");
        styleBlurb.setText(b.blurb());
        updating = was;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private BookSpec readSpec() {
        StyleKind kind = (StyleKind) styleBox.getSelectedItem();
        BindingStyle b = kind.impl;
        int st = ival(stations);
        if ((st - b.minStations()) % b.stationStep() != 0) {
            st = clamp(st + 1, b.minStations(), b.maxStations());
            boolean was = updating; updating = true; stations.setValue(st); updating = was;
        }
        return new BookSpec(kind, (PaperSize) paperBox.getSelectedItem(), orientBox.getSelectedIndex() == 0,
                dval(customW), dval(customH), (Grain) grainBox.getSelectedItem(), ival(gsm),
                ival(sheets), ival(sigs), st, dval(margin), dval(inset), (ThreadType) threadBox.getSelectedItem(),
                dval(board), dval(square), dval(turnIn), coverColor, threadColor,
                (Units) unitsBox.getSelectedItem(), titleField.getText());
    }

    private void setControls(BookSpec s) {
        updating = true;
        styleBox.setSelectedItem(s.style());
        currentKind = s.style();
        applyStyleModels(s.style(), s.sheetsPerSig(), s.signatures(), s.stations());
        paperBox.setSelectedItem(s.paper());
        orientBox.setSelectedIndex(s.landscape() ? 0 : 1);
        customW.setValue(s.customW());
        customH.setValue(s.customH());
        grainBox.setSelectedItem(s.grain());
        gsm.setValue(s.gsm());
        margin.setValue(s.marginMm());
        inset.setValue(s.insetMm());
        threadBox.setSelectedItem(s.thread());
        board.setValue(s.boardMm());
        square.setValue(s.squareMm());
        turnIn.setValue(s.turnInMm());
        unitsBox.setSelectedItem(s.units());
        titleField.setText(s.title());
        coverColor = s.coverColor();
        threadColor = s.threadColor();
        updating = false;
        refresh();
    }

    private void refresh() {
        if (updating) return;
        spec = readSpec();
        geo = Geometry.of(spec);
        List<SewStep> steps = spec.style().impl.steps(spec, geo);

        boolean custom = spec.paper() == PaperSize.CUSTOM;
        customW.setEnabled(custom);
        customH.setEnabled(custom);
        orientBox.setEnabled(!custom);
        coverBtn.setIcon(new SwatchIcon(coverColor));
        threadBtn.setIcon(new SwatchIcon(threadColor));

        if (geo.grainOk()) {
            grainWarn.setForeground(new Color(0x2E7D32));
            grainWarn.setText("✓ Grain runs parallel to the spine");
        } else {
            grainWarn.setForeground(new Color(0xB23121));
            grainWarn.setText("⚠ Grain runs across the spine — pages will cockle");
        }

        for (StudioPanel p : panels) p.setData(spec, geo, steps);

        Units u = spec.units();
        BindingStyle b = spec.style().impl;
        String block = b.usesSignatures()
                ? spec.signatures() + (spec.signatures() == 1 ? " signature × " : " signatures × ") + spec.sheetsPerSig() + " sheets"
                : spec.sheetsPerSig() + " folded leaves";
        status.setText(b.name() + "   ·   " + geo.pages() + " pages   ·   " + block + "   ·   leaf "
                + u.size(geo.leafW(), geo.leafH()) + "   ·   block ≈ " + u.len(geo.blockT())
                + "   ·   thread ≈ " + u.thread(Sew.cutLength(steps)));
    }

    // ---------------------------------------------------------------- menus

    private JMenuBar buildMenus() {
        int mod = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        file.add(item("New book", KeyStroke.getKeyStroke(KeyEvent.VK_N, mod), e -> { currentFile = null; setControls(BookSpec.defaults()); }));
        file.add(item("Open…", KeyStroke.getKeyStroke(KeyEvent.VK_O, mod), e -> open()));
        file.add(item("Save…", KeyStroke.getKeyStroke(KeyEvent.VK_S, mod), e -> save()));
        file.addSeparator();
        file.add(item("Export current tab as PNG…", KeyStroke.getKeyStroke(KeyEvent.VK_E, mod), e -> exportPng()));
        file.add(item("Print punching template…", KeyStroke.getKeyStroke(KeyEvent.VK_P, mod), e -> template.print(this)));
        file.addSeparator();
        file.add(item("Quit", KeyStroke.getKeyStroke(KeyEvent.VK_Q, mod), e -> System.exit(0)));
        bar.add(file);

        JMenu view = new JMenu("View");
        String[] names = { "Preview", "Signatures", "Template", "Sewing", "Cover & Boards", "Materials" };
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            view.add(item(names[i], KeyStroke.getKeyStroke(KeyEvent.VK_1 + i, mod), e -> tabs.setSelectedIndex(idx)));
        }
        bar.add(view);

        JMenu help = new JMenu("Help");
        help.add(item("Bench tips", null, e -> showTips()));
        help.add(item("About Bookbinding Studio", null, e -> JOptionPane.showMessageDialog(this,
                "<html><h2>Bookbinding Studio</h2>"
                        + "Design hand-bound books: templates, sewing diagrams,<br>"
                        + "thread lengths and cutting sizes.<br><br>"
                        + "By Mario Gianota<br><br>"
                        + "<i>Thread lengths and cover sizes are bench estimates.<br>"
                        + "Always check against your own materials.</i></html>",
                "About", JOptionPane.INFORMATION_MESSAGE)));
        bar.add(help);
        return bar;
    }

    private static JMenuItem item(String text, KeyStroke ks, ActionListener a) {
        JMenuItem m = new JMenuItem(text);
        if (ks != null) m.setAccelerator(ks);
        m.addActionListener(a);
        return m;
    }

    private void showTips() {
        JEditorPane ep = new JEditorPane("text/html",
                "<html><body style='font-family:sans-serif; width:460px'>"
                        + "<h3>Bench tips</h3><ul>"
                        + "<li><b>Grain.</b> Paper folds and lies flat best when its grain runs parallel to the spine. "
                        + "To test, bend a sheet gently both ways: it gives more easily along the grain.</li>"
                        + "<li><b>Punch from the inside.</b> Pierce signatures from the inside of the fold outwards, "
                        + "with the template sitting in the fold, so the holes line up from sheet to sheet.</li>"
                        + "<li><b>Check the 50 mm bar.</b> Before punching, measure the bar on the printed template. "
                        + "If it isn't 50 mm, reprint at 100% / Actual size.</li>"
                        + "<li><b>Tension.</b> Pull along the line of the spine, never outward away from it, "
                        + "or the thread will tear the paper.</li>"
                        + "<li><b>Long threads tangle.</b> Above about 2 m, it's easier to sew with a shorter length "
                        + "and join a new one with a weaver's knot inside a signature.</li>"
                        + "<li><b>Wax.</b> Unwaxed linen should be drawn across a block of beeswax two or three times.</li>"
                        + "<li><b>Kettle stitches</b> should sit snugly in the head and tail, but don't over-tighten "
                        + "them or the spine will curve.</li>"
                        + "</ul></body></html>");
        ep.setEditable(false);
        JOptionPane.showMessageDialog(this, new JScrollPane(ep), "Bench tips", JOptionPane.PLAIN_MESSAGE);
    }

    // ---------------------------------------------------------------- files

    private JFileChooser chooser() {
        JFileChooser fc = new JFileChooser(currentFile != null ? currentFile.getParentFile() : null);
        fc.setFileFilter(new FileNameExtensionFilter("Bookbinding Studio files (*.bind)", "bind"));
        return fc;
    }

    private void save() {
        JFileChooser fc = chooser();
        fc.setSelectedFile(currentFile != null ? currentFile : new File(safeName(spec.title()) + ".bind"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase(Locale.UK).endsWith(".bind")) f = new File(f.getParentFile(), f.getName() + ".bind");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8")) {
            spec.toProperties().store(w, "Bookbinding Studio book");
            currentFile = f;
            setTitle("Bookbinding Studio — " + f.getName());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Couldn't save: " + ex.getMessage(), "Save", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void open() {
        JFileChooser fc = chooser();
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        try (Reader r = new InputStreamReader(new FileInputStream(f), "UTF-8")) {
            Properties p = new Properties();
            p.load(r);
            setControls(BookSpec.fromProperties(p));
            currentFile = f;
            setTitle("Bookbinding Studio — " + f.getName());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Couldn't open: " + ex.getMessage(), "Open", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportPng() {
        StudioPanel p = (StudioPanel) tabs.getSelectedComponent();
        JComponent c = p.exportComponent();
        if (c.getWidth() <= 0 || c.getHeight() <= 0) return;
        JFileChooser fc = new JFileChooser(currentFile != null ? currentFile.getParentFile() : null);
        fc.setFileFilter(new FileNameExtensionFilter("PNG image", "png"));
        fc.setSelectedFile(new File(safeName(spec.title()) + "-" + safeName(tabs.getTitleAt(tabs.getSelectedIndex())) + ".png"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase(Locale.UK).endsWith(".png")) f = new File(f.getParentFile(), f.getName() + ".png");
        try {
            ImageIO.write(render(c, 2), "png", f);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Couldn't export: " + ex.getMessage(), "Export", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Paint a component into an image at the given pixel scale. */
    static BufferedImage render(JComponent c, int scale) {
        BufferedImage img = new BufferedImage(c.getWidth() * scale, c.getHeight() * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.scale(scale, scale);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, c.getWidth(), c.getHeight());
        c.paint(g);
        g.dispose();
        return img;
    }

    private static String safeName(String s) {
        String t = s == null ? "" : s.trim().replaceAll("[^A-Za-z0-9 _-]", "").replace(' ', '-');
        return t.isEmpty() ? "book" : t;
    }

    // Used by the self-test harness.
    void selectStyleForTest(StyleKind k) { styleBox.setSelectedItem(k); }
    JTabbedPane tabsForTest() { return tabs; }
    StudioPanel[] panelsForTest() { return panels; }
    SewingPanel sewingForTest() { return sewing; }
}

// ============================================================================
//  Model
// ============================================================================

enum Units {
    MM("Millimetres"), INCHES("Inches");
    final String label;
    Units(String l) { label = l; }
    @Override public String toString() { return label; }

    static String n1(double v) {
        return Math.abs(v - Math.rint(v)) < 0.05 ? String.format(Locale.UK, "%.0f", v) : String.format(Locale.UK, "%.1f", v);
    }
    String len(double mm) {
        return this == MM ? n1(mm) + " mm" : String.format(Locale.UK, "%.2f in", mm / 25.4);
    }
    String num(double mm) {
        return this == MM ? n1(mm) : String.format(Locale.UK, "%.2f", mm / 25.4);
    }
    String unit() { return this == MM ? "mm" : "in"; }
    String size(double w, double h) { return num(w) + " × " + num(h) + " " + unit(); }
    String thread(double mm) {
        return this == MM ? String.format(Locale.UK, "%.2f m", mm / 1000)
                : String.format(Locale.UK, "%.0f in (%.2f yd)", mm / 25.4, mm / 914.4);
    }
}

enum PaperSize {
    A3("A3", 297, 420), A4("A4", 210, 297), A5("A5", 148, 210), A6("A6", 105, 148),
    LETTER("US Letter", 215.9, 279.4), LEGAL("US Legal", 215.9, 355.6), TABLOID("Tabloid", 279.4, 431.8),
    CUSTOM("Custom", 0, 0);
    final String label; final double shortMm, longMm;
    PaperSize(String l, double s, double lg) { label = l; shortMm = s; longMm = lg; }
    @Override public String toString() { return label; }
}

enum Grain {
    LONG("Long grain (parallel to long edge)"), SHORT("Short grain (parallel to short edge)");
    final String label;
    Grain(String l) { label = l; }
    @Override public String toString() { return label; }
}

enum ThreadType {
    LINEN_18("Linen 18/3  (≈0.50 mm)", 0.50), LINEN_25("Linen 25/3  (≈0.40 mm)", 0.40),
    LINEN_35("Linen 35/3  (≈0.30 mm)", 0.30), WAXED_POLY("Waxed polyester  (≈0.80 mm)", 0.80),
    FLOSS("Embroidery floss  (≈0.60 mm)", 0.60);
    final String label; final double diaMm;
    ThreadType(String l, double d) { label = l; diaMm = d; }
    @Override public String toString() { return label; }
}

enum Side { INSIDE, OUTSIDE, FRONT, BACK }

enum Action { START, PASS, LINK, LOCK, CHAIN, KETTLE, TIE, WRAP_SPINE, WRAP_HEAD, WRAP_TAIL, TIE_OFF }

/**
 * One step of a sewing path.
 * x       — column in the spine diagram (signature number; boards and "between" positions use other values)
 * station — 0-based sewing station
 * side    — where the needle is after this step
 * lengthMm — thread consumed by this step
 */
record SewStep(double x, int station, Side side, Action action, String text, double lengthMm) { }

record BookSpec(StyleKind style, PaperSize paper, boolean landscape, double customW, double customH,
                Grain grain, int gsm, int sheetsPerSig, int signatures, int stations,
                double marginMm, double insetMm, ThreadType thread,
                double boardMm, double squareMm, double turnInMm,
                Color coverColor, Color threadColor, Units units, String title) {

    static BookSpec defaults() {
        return new BookSpec(StyleKind.KETTLE, PaperSize.A4, true, 300, 210, Grain.SHORT, 90,
                4, 6, 6, 15, 10, ThreadType.LINEN_25, 2.0, 3, 15,
                new Color(0x2F4F6F), new Color(0xC0392B), Units.MM, "Notebook");
    }

    Properties toProperties() {
        Properties p = new Properties();
        p.setProperty("style", style.name());
        p.setProperty("paper", paper.name());
        p.setProperty("landscape", Boolean.toString(landscape));
        p.setProperty("customW", Double.toString(customW));
        p.setProperty("customH", Double.toString(customH));
        p.setProperty("grain", grain.name());
        p.setProperty("gsm", Integer.toString(gsm));
        p.setProperty("sheets", Integer.toString(sheetsPerSig));
        p.setProperty("signatures", Integer.toString(signatures));
        p.setProperty("stations", Integer.toString(stations));
        p.setProperty("margin", Double.toString(marginMm));
        p.setProperty("inset", Double.toString(insetMm));
        p.setProperty("thread", thread.name());
        p.setProperty("board", Double.toString(boardMm));
        p.setProperty("square", Double.toString(squareMm));
        p.setProperty("turnIn", Double.toString(turnInMm));
        p.setProperty("coverColour", String.format("#%06X", coverColor.getRGB() & 0xFFFFFF));
        p.setProperty("threadColour", String.format("#%06X", threadColor.getRGB() & 0xFFFFFF));
        p.setProperty("units", units.name());
        p.setProperty("title", title);
        return p;
    }

    static BookSpec fromProperties(Properties p) {
        BookSpec d = defaults();
        return new BookSpec(
                StyleKind.valueOf(p.getProperty("style", d.style.name())),
                PaperSize.valueOf(p.getProperty("paper", d.paper.name())),
                Boolean.parseBoolean(p.getProperty("landscape", "true")),
                Double.parseDouble(p.getProperty("customW", "300")),
                Double.parseDouble(p.getProperty("customH", "210")),
                Grain.valueOf(p.getProperty("grain", d.grain.name())),
                Integer.parseInt(p.getProperty("gsm", "90")),
                Integer.parseInt(p.getProperty("sheets", "4")),
                Integer.parseInt(p.getProperty("signatures", "6")),
                Integer.parseInt(p.getProperty("stations", "6")),
                Double.parseDouble(p.getProperty("margin", "15")),
                Double.parseDouble(p.getProperty("inset", "10")),
                ThreadType.valueOf(p.getProperty("thread", d.thread.name())),
                Double.parseDouble(p.getProperty("board", "2")),
                Double.parseDouble(p.getProperty("square", "3")),
                Double.parseDouble(p.getProperty("turnIn", "15")),
                Color.decode(p.getProperty("coverColour", "#2F4F6F")),
                Color.decode(p.getProperty("threadColour", "#C0392B")),
                Units.valueOf(p.getProperty("units", "MM")),
                p.getProperty("title", "Notebook"));
    }
}

/** Everything derived from a BookSpec: sizes, thicknesses, station positions. All lengths in mm. */
record Geometry(double sheetW, double sheetH, double leafW, double leafH, double caliper,
                double sigThickness, double blockT, int pages, int totalSheets,
                List<Double> stations, boolean grainOk, String duplexEdge) {

    static Geometry of(BookSpec s) {
        BindingStyle b = s.style().impl;
        double sw, sh;
        if (s.paper() == PaperSize.CUSTOM) { sw = s.customW(); sh = s.customH(); }
        else if (s.landscape()) { sw = s.paper().longMm; sh = s.paper().shortMm; }
        else { sw = s.paper().shortMm; sh = s.paper().longMm; }
        double lw = sw / 2, lh = sh;                       // one fold, parallel to the sheet's height
        double cal = s.gsm() * 0.00125;                    // ≈ 0.1 mm at 80 gsm
        int total = b.usesSignatures() ? s.signatures() * s.sheetsPerSig() : s.sheetsPerSig();
        double sigT = s.sheetsPerSig() * 2 * cal;
        double block = b.usesSignatures()
                ? s.signatures() * (sigT + s.thread().diaMm)   // paper + swell from thread in each fold
                : total * 2 * cal;
        int pages = b.usesSignatures() ? total * 4 : total * 2;
        boolean grainOk = (s.grain() == Grain.LONG) == (sh >= sw);
        double m = Math.min(s.marginMm(), lh / 4);
        int n = s.stations();
        List<Double> st = new ArrayList<>();
        for (int i = 0; i < n; i++) st.add(m + i * (lh - 2 * m) / (n - 1));
        String duplex = sh < sw ? "short edge" : "long edge";
        return new Geometry(sw, sh, lw, lh, cal, sigT, block, pages, total,
                Collections.unmodifiableList(st), grainOk, duplex);
    }
}

// ============================================================================
//  Binding styles
// ============================================================================

enum StyleKind {
    PAMPHLET(new Pamphlet()), KETTLE(new KettleStitch()), LONG_STITCH(new LongStitch()),
    COPTIC(new Coptic()), STAB(new JapaneseStab());
    final BindingStyle impl;
    StyleKind(BindingStyle b) { impl = b; }
    @Override public String toString() { return impl.name(); }
}

sealed interface BindingStyle permits Pamphlet, KettleStitch, LongStitch, Coptic, JapaneseStab {
    String name();
    String blurb();
    boolean usesSignatures();
    int minStations();
    int maxStations();
    int stationStep();
    int defaultStations();
    int minSignatures();
    int maxSignatures();
    int defaultSignatures();
    int maxSheets();
    int defaultSheets();
    boolean usesInset();
    double minCol(BookSpec s);
    double maxCol(BookSpec s);
    List<SewStep> steps(BookSpec s, Geometry g);
    void drawSpine(Graphics2D g2, BookSpec s, Geometry g, List<SewStep> steps, Rectangle2D r);
    String needle();
    String ruleOfThumb(BookSpec s, Geometry g);
}

/** Shared sewing helpers. */
final class Sew {
    private Sew() { }

    static final double TAILS_MM = 160;      // 2 × 8 cm tails
    static final double SLACK = 0.10;        // 10% working slack

    static double pathLength(List<SewStep> steps) {
        double t = 0;
        for (SewStep s : steps) t += s.lengthMm();
        return t;
    }

    static double cutLength(List<SewStep> steps) { return pathLength(steps) * (1 + SLACK) + TAILS_MM; }

    static int other(int i, int n) { return n - 1 - i; }

    static String sigName(BookSpec s, int sig) {
        return s.style() == StyleKind.PAMPHLET ? "the signature" : "signature " + sig;
    }

    /** Kettle-stitch sewing, shared by the kettle (case) binding and long-stitch through a cover. */
    static List<SewStep> kettleSteps(BookSpec s, Geometry g, boolean throughCover) {
        List<Double> y = g.stations();
        int n = y.size(), N = s.signatures();
        double hole = throughCover ? 2.0 : 1.0;
        double transfer = g.sigThickness() + 2;
        double kettle = 3 * g.sigThickness() + 12;
        String where = throughCover ? " (through the cover spine and the fold)" : "";
        List<SewStep> out = new ArrayList<>();
        for (int sig = 1; sig <= N; sig++) {
            boolean down = sig % 2 == 1;
            int first = down ? 0 : n - 1, last = down ? n - 1 : 0;
            if (sig == 1)
                out.add(new SewStep(1, first, Side.INSIDE, Action.START,
                        "Leaving an 8 cm tail outside, go IN at kettle station 1 of signature 1" + where + ".", hole));
            else
                out.add(new SewStep(sig, first, Side.INSIDE, Action.PASS,
                        "Place signature " + sig + " on the stack, folds aligned, and go straight IN at its station "
                                + (first + 1) + where + ".", transfer + hole));
            int prev = first;
            for (int idx = 1; idx < n; idx++) {
                int k = down ? idx : n - 1 - idx;
                Side side = idx % 2 == 1 ? Side.OUTSIDE : Side.INSIDE;
                String text = side == Side.OUTSIDE
                        ? "Inside the fold, run to station " + (k + 1) + " and come OUT."
                        : "Along the outside of the spine, run to station " + (k + 1) + " and go back IN.";
                if (idx == n - 1) text += " This is the kettle station.";
                out.add(new SewStep(sig, k, side, Action.PASS, text, Math.abs(y.get(k) - y.get(prev)) + hole));
                prev = k;
            }
            if (sig == 2)
                out.add(new SewStep(1.5, last, Side.OUTSIDE, Action.TIE,
                        "Pull snug along the spine (not outward) and tie the working thread to the tail with a square knot at station "
                                + (last + 1) + ". Signatures 1 and 2 are now joined.", 10));
            else if (sig >= 3)
                out.add(new SewStep(sig - 1.5, last, Side.OUTSIDE, Action.KETTLE,
                        "Kettle stitch: slip the needle between signatures " + (sig - 2) + " and " + (sig - 1)
                                + ", behind the thread linking them at station " + (last + 1)
                                + ", then pass it up through the loop you've just formed. Snug it down.", kettle));
            if (sig == N) {
                if (N >= 3)
                    out.add(new SewStep(sig - 1.5, last, Side.OUTSIDE, Action.KETTLE,
                            "Make a second kettle stitch in the same place to lock the end.", kettle));
                out.add(new SewStep(sig, last, Side.OUTSIDE, Action.TIE_OFF,
                        "Finish with a half hitch, run the needle a few centimetres into the last fold, and trim. Done.", 30));
            }
        }
        return out;
    }

    /** Draws the outside thread from a step list onto a spine-face rectangle. */
    static void spineFromSteps(Graphics2D g, Geometry geo, List<SewStep> steps, Rectangle2D r,
                               double minC, double maxC, Color thread, float width) {
        double cols = maxC - minC + 1;
        double cw = r.getWidth() / cols;
        g.setColor(thread);
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 1; i < steps.size(); i++) {
            SewStep a = steps.get(i - 1), b = steps.get(i);
            if (a.side() != Side.OUTSIDE) continue;
            double x1 = r.getX() + (a.x() - minC + 0.5) * cw, x2 = r.getX() + (b.x() - minC + 0.5) * cw;
            double y1 = r.getY() + geo.stations().get(a.station()) / geo.leafH() * r.getHeight();
            double y2 = r.getY() + geo.stations().get(b.station()) / geo.leafH() * r.getHeight();
            g.draw(new Line2D.Double(x1, y1, x2, y2));
        }
        for (SewStep s : steps) {
            if (s.action() == Action.KETTLE || s.action() == Action.CHAIN || s.action() == Action.LOCK || s.action() == Action.TIE) {
                double x = r.getX() + (s.x() - minC + 0.5) * cw;
                double y = r.getY() + geo.stations().get(s.station()) / geo.leafH() * r.getHeight();
                double rr = Math.max(2, Math.min(cw * 0.45, 5));
                g.draw(new Ellipse2D.Double(x - rr, y - rr * 0.7, 2 * rr, 1.4 * rr));
            }
        }
    }
}

// ---------------------------------------------------------------- pamphlet

final class Pamphlet implements BindingStyle {
    public String name() { return "Pamphlet stitch"; }
    public String blurb() { return "One signature sewn through its fold and cover. The quickest binding: zines, booklets, slim notebooks."; }
    public boolean usesSignatures() { return true; }
    public int minStations() { return 3; }
    public int maxStations() { return 7; }
    public int stationStep() { return 2; }
    public int defaultStations() { return 3; }
    public int minSignatures() { return 1; }
    public int maxSignatures() { return 1; }
    public int defaultSignatures() { return 1; }
    public int maxSheets() { return 12; }
    public int defaultSheets() { return 6; }
    public boolean usesInset() { return false; }
    public double minCol(BookSpec s) { return 1; }
    public double maxCol(BookSpec s) { return 1; }
    public String needle() { return "One straight bookbinding needle"; }
    public String ruleOfThumb(BookSpec s, Geometry g) {
        return "about 3 × the spine height, i.e. " + s.units().thread(3 * g.leafH());
    }

    public List<SewStep> steps(BookSpec s, Geometry g) {
        List<Double> y = g.stations();
        int n = y.size(), c = n / 2;
        List<SewStep> out = new ArrayList<>();
        out.add(new SewStep(1, c, Side.OUTSIDE, Action.START,
                "Open the signature with its cover wrapped round it. Starting INSIDE, push the needle OUT through the centre station "
                        + (c + 1) + ", leaving an 8 cm tail inside.", 1));
        List<Integer> seq = new ArrayList<>();
        for (int k = c - 1; k >= 0; k--) seq.add(k);          // out towards the head…
        for (int k = 1; k <= c - 1; k++) seq.add(k);          // …and back
        for (int k = c + 1; k < n; k++) seq.add(k);           // long stitch inside, on to the tail…
        for (int k = n - 2; k >= c + 1; k--) seq.add(k);      // …and back
        seq.add(c);                                           // finish at the centre
        int prev = c;
        Side side = Side.OUTSIDE;
        for (int i = 0; i < seq.size(); i++) {
            int k = seq.get(i);
            Side next = side == Side.OUTSIDE ? Side.INSIDE : Side.OUTSIDE;
            String text;
            if (next == Side.INSIDE) text = "On the outside, run to station " + (k + 1) + " and go IN.";
            else text = "Inside the fold, run to station " + (k + 1) + " and come OUT.";
            if (next == Side.OUTSIDE && (prev - c) * (k - c) < 0)
                text += " This long inside stitch passes over the centre hole.";
            if (i == seq.size() - 1)
                text = "On the outside, run to the centre station " + (k + 1)
                        + " and go IN, on the opposite side of the long inside stitch from the tail.";
            out.add(new SewStep(1, k, next, Action.PASS, text, Math.abs(y.get(k) - y.get(prev)) + 1));
            prev = k;
            side = next;
        }
        out.add(new SewStep(1, c, Side.INSIDE, Action.TIE_OFF,
                "Tighten along the spine, then tie the tail and working thread in a square knot over the long stitch. Trim to 1 cm.", 0));
        return out;
    }

    public void drawSpine(Graphics2D g, BookSpec s, Geometry geo, List<SewStep> steps, Rectangle2D r) {
        g.setColor(s.coverColor());
        g.fill(r);
        g.setColor(G.mix(s.coverColor(), Color.BLACK, 0.25));
        g.draw(new Line2D.Double(r.getCenterX(), r.getY(), r.getCenterX(), r.getMaxY()));
        Sew.spineFromSteps(g, geo, steps, r, 1, 1, s.threadColor(), 2.2f);
    }
}

// ---------------------------------------------------------------- kettle stitch (case binding)

final class KettleStitch implements BindingStyle {
    public String name() { return "Kettle stitch (case binding)"; }
    public String blurb() { return "Signatures linked at head and tail with kettle stitches, then cased in boards and cloth. The classic hardback."; }
    public boolean usesSignatures() { return true; }
    public int minStations() { return 4; }
    public int maxStations() { return 12; }
    public int stationStep() { return 2; }
    public int defaultStations() { return 6; }
    public int minSignatures() { return 2; }
    public int maxSignatures() { return 40; }
    public int defaultSignatures() { return 6; }
    public int maxSheets() { return 12; }
    public int defaultSheets() { return 4; }
    public boolean usesInset() { return false; }
    public double minCol(BookSpec s) { return 1; }
    public double maxCol(BookSpec s) { return s.signatures(); }
    public String needle() { return "One straight bookbinding needle"; }
    public String ruleOfThumb(BookSpec s, Geometry g) {
        return "spine height × (signatures + 1), i.e. " + s.units().thread(g.leafH() * (s.signatures() + 1));
    }
    public List<SewStep> steps(BookSpec s, Geometry g) { return Sew.kettleSteps(s, g, false); }

    public void drawSpine(Graphics2D g, BookSpec s, Geometry geo, List<SewStep> steps, Rectangle2D r) {
        Color c = s.coverColor();
        g.setPaint(new GradientPaint((float) r.getX(), 0, G.mix(c, Color.BLACK, 0.25),
                (float) r.getCenterX(), 0, G.mix(c, Color.WHITE, 0.12), true));
        g.fill(r);
        g.setColor(G.mix(c, Color.BLACK, 0.4));
        g.setStroke(new BasicStroke(1.2f));
        for (double f : new double[] { 0.07, 0.93 })
            g.draw(new Line2D.Double(r.getX(), r.getY() + r.getHeight() * f, r.getMaxX(), r.getY() + r.getHeight() * f));
        g.setColor(s.threadColor());
        g.fill(new Rectangle2D.Double(r.getX() + 1, r.getY() - 2, r.getWidth() - 2, 3));
        g.fill(new Rectangle2D.Double(r.getX() + 1, r.getMaxY() - 1, r.getWidth() - 2, 3));
        if (r.getWidth() >= 14 && s.title() != null && !s.title().isBlank()) {
            AffineTransform at = g.getTransform();
            g.translate(r.getCenterX(), r.getY() + r.getHeight() * 0.5);
            g.rotate(Math.PI / 2);
            g.setFont(new Font(Font.SERIF, Font.BOLD, (int) Math.min(14, r.getWidth() * 0.55)));
            g.setColor(G.mix(G.textOn(c), new Color(0xD4AF37), 0.6));
            G.centre(g, s.title(), 0, g.getFontMetrics().getAscent() / 2.0 - 1);
            g.setTransform(at);
        }
    }
}

// ---------------------------------------------------------------- long-stitch

final class LongStitch implements BindingStyle {
    public String name() { return "Long-stitch"; }
    public String blurb() { return "Signatures sewn straight through a soft cover spine, so the stitching becomes the decoration. No glue needed."; }
    public boolean usesSignatures() { return true; }
    public int minStations() { return 4; }
    public int maxStations() { return 12; }
    public int stationStep() { return 2; }
    public int defaultStations() { return 6; }
    public int minSignatures() { return 2; }
    public int maxSignatures() { return 30; }
    public int defaultSignatures() { return 5; }
    public int maxSheets() { return 12; }
    public int defaultSheets() { return 6; }
    public boolean usesInset() { return false; }
    public double minCol(BookSpec s) { return 1; }
    public double maxCol(BookSpec s) { return s.signatures(); }
    public String needle() { return "One straight bookbinding needle (and an awl for the cover)"; }
    public String ruleOfThumb(BookSpec s, Geometry g) {
        return "spine height × (signatures + 1), i.e. " + s.units().thread(g.leafH() * (s.signatures() + 1));
    }
    public List<SewStep> steps(BookSpec s, Geometry g) { return Sew.kettleSteps(s, g, true); }

    public void drawSpine(Graphics2D g, BookSpec s, Geometry geo, List<SewStep> steps, Rectangle2D r) {
        g.setColor(s.coverColor());
        g.fill(r);
        g.setColor(G.mix(s.coverColor(), Color.BLACK, 0.3));
        g.draw(new Line2D.Double(r.getX(), r.getY(), r.getX(), r.getMaxY()));
        g.draw(new Line2D.Double(r.getMaxX(), r.getY(), r.getMaxX(), r.getMaxY()));
        Sew.spineFromSteps(g, geo, steps, r, 1, s.signatures(), s.threadColor(), 2.2f);
    }
}

// ---------------------------------------------------------------- Coptic

final class Coptic implements BindingStyle {
    public String name() { return "Coptic"; }
    public String blurb() { return "An exposed spine of chain stitches joining signatures straight to the boards. Opens completely flat."; }
    public boolean usesSignatures() { return true; }
    public int minStations() { return 4; }
    public int maxStations() { return 12; }
    public int stationStep() { return 2; }
    public int defaultStations() { return 4; }
    public int minSignatures() { return 2; }
    public int maxSignatures() { return 30; }
    public int defaultSignatures() { return 6; }
    public int maxSheets() { return 12; }
    public int defaultSheets() { return 4; }
    public boolean usesInset() { return true; }
    public double minCol(BookSpec s) { return 0; }
    public double maxCol(BookSpec s) { return s.signatures() + 1; }
    public String needle() { return "One curved needle (makes the chain stitches much easier)"; }
    public String ruleOfThumb(BookSpec s, Geometry g) {
        return "roughly spine height × signatures, plus extra for the boards: "
                + s.units().thread(g.leafH() * s.signatures() * 1.4);
    }

    public List<SewStep> steps(BookSpec s, Geometry g) {
        List<Double> y = g.stations();
        int n = y.size(), N = s.signatures();
        double hole = 1.5, link = 2 * s.insetMm() + s.boardMm() + 8, lock = 8;
        double chain = 2 * g.sigThickness() + 10, transfer = g.sigThickness() + 2;
        List<SewStep> out = new ArrayList<>();

        for (int k = 0; k < n; k++) {
            if (k == 0)
                out.add(new SewStep(1, 0, Side.OUTSIDE, Action.START,
                        "Lay signature 1 on the front board. Starting INSIDE signature 1 with an 8 cm tail, come OUT at station 1.", hole));
            else
                out.add(new SewStep(1, k, Side.OUTSIDE, Action.PASS,
                        "Inside the fold, run to station " + (k + 1) + " and come OUT.", Math.abs(y.get(k) - y.get(k - 1)) + hole));
            out.add(new SewStep(0, k, Side.OUTSIDE, Action.LINK,
                    "Take the thread round the spine edge of the front board and go through board hole " + (k + 1)
                            + " from the outside, coming up between the board and signature 1.", link));
            out.add(new SewStep(0.5, k, Side.OUTSIDE, Action.LOCK,
                    "Pass the needle behind the thread between board and signature to lock it, then pull snug so the board sits close.", lock));
            if (k < n - 1)
                out.add(new SewStep(1, k, Side.INSIDE, Action.PASS, "Go back IN at station " + (k + 1) + " of signature 1."
                        + (k == 0 ? " Tie the tail to the working thread inside with a square knot." : ""), hole));
            else transfer(out, s, 2, k, link, transfer + hole);
        }

        for (int sig = 2; sig <= N; sig++) {
            boolean down = sig % 2 == 1;
            int prev = down ? 0 : n - 1;
            for (int idx = 1; idx < n; idx++) {
                int k = down ? idx : n - 1 - idx;
                out.add(new SewStep(sig, k, Side.OUTSIDE, Action.PASS, "Inside the fold, run to station " + (k + 1) + " and come OUT.",
                        Math.abs(y.get(k) - y.get(prev)) + hole));
                if (sig == N)
                    out.add(new SewStep(N + 1, k, Side.OUTSIDE, Action.LINK, "Go through back board hole " + (k + 1)
                            + " from the outside, coming up between the board and signature " + N + ".", link));
                String below = sig - 2 == 0 ? "the front board" : "signature " + (sig - 2);
                out.add(new SewStep(sig - 1.5, k, Side.OUTSIDE, Action.CHAIN,
                        "Chain stitch: pass the needle behind the stitch that links signature " + (sig - 1) + " to " + below
                                + " at station " + (k + 1) + ", then pull up and snug.", chain));
                if (idx < n - 1 || sig == N)
                    out.add(new SewStep(sig, k, Side.INSIDE, Action.PASS, "Go back IN at station " + (k + 1) + " of signature " + sig + ".", hole));
                else transfer(out, s, sig + 1, k, link, transfer + hole);
                prev = k;
            }
        }
        int lastStation = N % 2 == 1 ? n - 1 : 0;
        out.add(new SewStep(N, lastStation, Side.INSIDE, Action.TIE_OFF,
                "Inside the last signature, tie off round the stitch with two half hitches and trim. Done.", 30));
        return out;
    }

    private static void transfer(List<SewStep> out, BookSpec s, int next, int k, double link, double len) {
        int N = s.signatures();
        if (next == N)
            out.add(new SewStep(N + 1, k, Side.OUTSIDE, Action.LINK, "Signature " + N
                    + " is the last one, so first go through back board hole " + (k + 1)
                    + " from the outside and come up between the board and the stack.", link));
        out.add(new SewStep(next, k, Side.INSIDE, Action.PASS, "Place signature " + next
                + " on the stack and go IN at its station " + (k + 1) + ".", len));
    }

    public void drawSpine(Graphics2D g, BookSpec s, Geometry geo, List<SewStep> steps, Rectangle2D r) {
        int N = s.signatures();
        double cw = r.getWidth() / (N + 2);
        g.setColor(G.PAPER);
        g.fill(r);
        g.setColor(G.FAINT);
        for (int i = 1; i <= N; i++) {
            double x = r.getX() + (i + 0.5) * cw;
            g.draw(new Line2D.Double(x, r.getY(), x, r.getMaxY()));
        }
        g.setColor(G.mix(s.coverColor(), Color.BLACK, 0.2));
        g.fill(new Rectangle2D.Double(r.getX(), r.getY() - 3, cw * 0.8, r.getHeight() + 6));
        g.fill(new Rectangle2D.Double(r.getMaxX() - cw * 0.8, r.getY() - 3, cw * 0.8, r.getHeight() + 6));
        Sew.spineFromSteps(g, geo, steps, r, 0, N + 1, s.threadColor(), 1.8f);
    }
}

// ---------------------------------------------------------------- Japanese stab

final class JapaneseStab implements BindingStyle {
    public String name() { return "Japanese stab (yotsume)"; }
    public String blurb() { return "Folded leaves stacked and sewn through the whole block near the spine, wrapping spine, head and tail. Classic four-hole pattern or more."; }
    public boolean usesSignatures() { return false; }
    public int minStations() { return 3; }
    public int maxStations() { return 8; }
    public int stationStep() { return 1; }
    public int defaultStations() { return 4; }
    public int minSignatures() { return 1; }
    public int maxSignatures() { return 1; }
    public int defaultSignatures() { return 1; }
    public int maxSheets() { return 200; }
    public int defaultSheets() { return 20; }
    public boolean usesInset() { return true; }
    public double minCol(BookSpec s) { return 0; }
    public double maxCol(BookSpec s) { return 0; }
    public String needle() { return "One large-eyed needle; an awl or drill and a clamp for the holes"; }
    public String ruleOfThumb(BookSpec s, Geometry g) { return null; }

    static Side flip(Side s) { return s == Side.FRONT ? Side.BACK : Side.FRONT; }
    static String face(Side s) { return s == Side.FRONT ? "front" : "back"; }

    public List<SewStep> steps(BookSpec s, Geometry g) {
        List<Double> y = g.stations();
        int n = y.size();
        double T = g.blockT() + 0.6, in = s.insetMm(), H = g.leafH();
        List<SewStep> out = new ArrayList<>();
        out.add(new SewStep(0, 0, Side.FRONT, Action.START,
                "Clamp the block between its covers. From the BACK, push the needle through hole 1 to the front, leaving an 8 cm tail on the back.", T));
        out.add(new SewStep(0, 0, Side.FRONT, Action.WRAP_SPINE,
                "Wrap the spine: take the thread round the spine edge to the back and push through hole 1 again to the front.", 2 * in + 2 * T));
        out.add(new SewStep(0, 0, Side.FRONT, Action.WRAP_HEAD,
                "Wrap the head: take the thread over the head edge to the back and push through hole 1 again to the front.", 2 * y.get(0) + 2 * T));
        Side side = Side.FRONT;
        for (int i = 1; i < n; i++) {
            Side next = flip(side);
            out.add(new SewStep(0, i, next, Action.PASS, "Along the " + face(side) + ", run to hole " + (i + 1)
                    + " and push through to the " + face(next) + ".", Math.abs(y.get(i) - y.get(i - 1)) + T));
            side = next;
            out.add(new SewStep(0, i, side, Action.WRAP_SPINE, "Wrap the spine: round the spine edge to the " + face(flip(side))
                    + " and push through hole " + (i + 1) + " again to the " + face(side) + ".", 2 * in + 2 * T));
            if (i == n - 1)
                out.add(new SewStep(0, i, side, Action.WRAP_TAIL, "Wrap the tail: over the tail edge to the " + face(flip(side))
                        + " and push through hole " + (i + 1) + " again to the " + face(side) + ".", 2 * (H - y.get(i)) + 2 * T));
        }
        for (int i = n - 2; i >= 1; i--) {
            Side next = flip(side);
            out.add(new SewStep(0, i, next, Action.PASS, "Heading back, run along the " + face(side) + " to hole " + (i + 1)
                    + " and push through to the " + face(next) + ".", Math.abs(y.get(i + 1) - y.get(i)) + T));
            side = next;
        }
        out.add(new SewStep(0, 0, side, Action.TIE_OFF, "Run along the " + face(side)
                + " to hole 1. Tie the working thread to the tail with a square knot, ease the knot into the hole with the needle tip, and trim.",
                Math.abs(y.get(1) - y.get(0))));
        return out;
    }

    public void drawSpine(Graphics2D g, BookSpec s, Geometry geo, List<SewStep> steps, Rectangle2D r) {
        g.setColor(G.PAPER);
        g.fill(r);
        g.setColor(G.FAINT);
        for (int i = 1; i < 6; i++) {
            double x = r.getX() + r.getWidth() * i / 6;
            g.draw(new Line2D.Double(x, r.getY(), x, r.getMaxY()));
        }
        g.setColor(s.coverColor());
        g.fill(new Rectangle2D.Double(r.getX(), r.getY(), 3, r.getHeight()));
        g.fill(new Rectangle2D.Double(r.getMaxX() - 3, r.getY(), 3, r.getHeight()));
        g.setColor(s.threadColor());
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (double yy : geo.stations()) {
            double py = r.getY() + yy / geo.leafH() * r.getHeight();
            g.draw(new Line2D.Double(r.getX(), py, r.getMaxX(), py));
        }
    }
}

// ============================================================================
//  Cover calculations
// ============================================================================

record Piece(String name, double w, double h, int qty, String note) { }

final class CoverCalc {
    private CoverCalc() { }

    // case binding
    static double caseBoardW(BookSpec s, Geometry g) { return g.leafW() + s.squareMm() - 3; }
    static double caseBoardH(BookSpec s, Geometry g) { return g.leafH() + 2 * s.squareMm(); }
    static double caseSpineW(BookSpec s, Geometry g) { return g.blockT() + 1; }
    static double caseGap(BookSpec s) { return 2 * s.boardMm() + 1; }
    static double caseClothW(BookSpec s, Geometry g) {
        return 2 * caseBoardW(s, g) + 2 * caseGap(s) + caseSpineW(s, g) + 2 * s.turnInMm();
    }
    static double caseClothH(BookSpec s, Geometry g) { return caseBoardH(s, g) + 2 * s.turnInMm(); }

    // Coptic boards
    static double copticBoardW(BookSpec s, Geometry g) { return g.leafW() + s.squareMm(); }
    static double copticBoardH(BookSpec s, Geometry g) { return g.leafH() + 2 * s.squareMm(); }

    // limp long-stitch cover
    static double limpFlap(Geometry g) { return Math.round(g.leafW() * 0.5); }
    static double limpW(Geometry g) { return 2 * limpFlap(g) + 2 * g.leafW() + g.blockT() + 2; }

    static List<Piece> pieces(BookSpec s, Geometry g) {
        List<Piece> p = new ArrayList<>();
        double W = g.leafW(), H = g.leafH(), T = g.blockT(), t = s.turnInMm();
        switch (s.style()) {
            case PAMPHLET -> p.add(new Piece("Wrap-around cover (card, 200–300 gsm)", 2 * W + Math.max(T, 0.5), H, 1,
                    "Fold in half; punch with the signature"));
            case KETTLE -> {
                double bw = caseBoardW(s, g), bh = caseBoardH(s, g);
                p.add(new Piece("Boards (greyboard " + Units.n1(s.boardMm()) + " mm)", bw, bh, 2, "Grain parallel to the spine"));
                p.add(new Piece("Spine stiffener (thin card)", caseSpineW(s, g), bh, 1, "Flat back"));
                p.add(new Piece("Book cloth or covering paper", caseClothW(s, g), caseClothH(s, g), 1,
                        "Joint gaps " + s.units().len(caseGap(s)) + "; mitre the corners"));
                p.add(new Piece("Endpapers (folded)", 2 * W, H, 2, "Tip on to the first and last signatures"));
                p.add(new Piece("Mull / super", T + 50, H - 20, 1, "Glued across the spine"));
            }
            case LONG_STITCH -> p.add(new Piece("Limp cover (leather, heavy card or paper)", limpW(g), H, 1,
                    "Fore-edge flaps " + s.units().len(limpFlap(g)) + "; spine " + s.units().len(T)));
            case COPTIC -> {
                double bw = copticBoardW(s, g), bh = copticBoardH(s, g);
                p.add(new Piece("Boards (greyboard " + Units.n1(s.boardMm()) + " mm)", bw, bh, 2,
                        "Holes " + s.units().len(s.insetMm()) + " from the spine edge"));
                p.add(new Piece("Covering paper", bw + 2 * t, bh + 2 * t, 2, "Mitre the corners"));
                p.add(new Piece("Board liners", bw - 4, bh - 4, 2, "Glued inside each board"));
            }
            case STAB -> {
                p.add(new Piece("Covers (heavy paper or card)", W, H, 2, "Punched with the block"));
                p.add(new Piece("Spine-edge corner protectors (optional)", 25, 25, 4, "Cloth, folded over the corners"));
            }
        }
        return p;
    }
}

// ============================================================================
//  Drawing helpers
// ============================================================================

final class G {
    private G() { }
    static final Color PAPER = new Color(0xFBF6EA);
    static final Color DESK = new Color(0xEFE8DC);
    static final Color INK = new Color(0x2B2620);
    static final Color FAINT = new Color(0xBDB2A0);
    static final Color BOARD = new Color(0x8C8172);
    static final Color ACCENT = new Color(0xB5651D);
    static final Color CUT = new Color(0xC0392B);

    static Graphics2D hq(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), a); }

    static Color mix(Color a, Color b, double t) {
        return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    static Color textOn(Color c) {
        double l = 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
        return l < 140 ? new Color(0xFAF5EB) : new Color(0x2B2620);
    }

    static void centre(Graphics2D g, String s, double cx, double y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, (float) (cx - fm.stringWidth(s) / 2.0), (float) y);
    }

    static Stroke dashed(float w) {
        return new BasicStroke(w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[] { 5f, 4f }, 0f);
    }

    static Font sans(float size, int style) { return new Font(Font.SANS_SERIF, style, 12).deriveFont(style, size); }
    static Font serif(float size, int style) { return new Font(Font.SERIF, style, 12).deriveFont(style, size); }

    /** Horizontal dimension line with end ticks and a centred label above. */
    static void dimH(Graphics2D g, double x1, double x2, double y, String label) {
        g.setColor(G.INK);
        g.setStroke(new BasicStroke(0.8f));
        g.draw(new Line2D.Double(x1, y, x2, y));
        g.draw(new Line2D.Double(x1, y - 4, x1, y + 4));
        g.draw(new Line2D.Double(x2, y - 4, x2, y + 4));
        g.setFont(sans(11f, Font.PLAIN));
        FontMetrics fm = g.getFontMetrics();
        double tw = fm.stringWidth(label);
        g.setColor(DESK);
        g.fill(new Rectangle2D.Double((x1 + x2) / 2 - tw / 2 - 3, y - fm.getAscent() + 1, tw + 6, fm.getAscent()));
        g.setColor(G.INK);
        centre(g, label, (x1 + x2) / 2, y + 4);
    }

    /** Vertical dimension line with a rotated label. */
    static void dimV(Graphics2D g, double x, double y1, double y2, String label) {
        g.setColor(G.INK);
        g.setStroke(new BasicStroke(0.8f));
        g.draw(new Line2D.Double(x, y1, x, y2));
        g.draw(new Line2D.Double(x - 4, y1, x + 4, y1));
        g.draw(new Line2D.Double(x - 4, y2, x + 4, y2));
        AffineTransform at = g.getTransform();
        g.translate(x, (y1 + y2) / 2);
        g.rotate(-Math.PI / 2);
        g.setFont(sans(11f, Font.PLAIN));
        FontMetrics fm = g.getFontMetrics();
        double tw = fm.stringWidth(label);
        g.setColor(DESK);
        g.fill(new Rectangle2D.Double(-tw / 2 - 3, -fm.getAscent() + 1, tw + 6, fm.getAscent()));
        g.setColor(G.INK);
        centre(g, label, 0, 4);
        g.setTransform(at);
    }
}

final class SwatchIcon implements Icon {
    private final Color c;
    SwatchIcon(Color c) { this.c = c; }
    public int getIconWidth() { return 14; }
    public int getIconHeight() { return 14; }
    public void paintIcon(Component comp, Graphics g0, int x, int y) {
        Graphics2D g = G.hq(g0);
        g.setColor(c);
        g.fillRoundRect(x, y, 14, 14, 4, 4);
        g.setColor(Color.DARK_GRAY);
        g.drawRoundRect(x, y, 13, 13, 4, 4);
        g.dispose();
    }
}

// ============================================================================
//  Views
// ============================================================================

abstract class StudioPanel extends JPanel {
    BookSpec spec;
    Geometry geo;
    List<SewStep> steps = List.of();

    StudioPanel() { setBackground(G.DESK); }

    void setData(BookSpec s, Geometry g, List<SewStep> st) {
        spec = s; geo = g; steps = st;
        dataChanged();
        repaint();
    }

    void dataChanged() { }

    JComponent exportComponent() { return this; }

    /** Caption helper in pixel space. */
    static void caption(Graphics2D g, String s, double cx, double y) {
        g.setColor(G.INK);
        g.setFont(G.sans(12f, Font.BOLD));
        G.centre(g, s, cx, y);
    }

    static void note(Graphics2D g, String s, double cx, double y) {
        g.setColor(new Color(0x6B6255));
        g.setFont(G.sans(11f, Font.PLAIN));
        G.centre(g, s, cx, y);
    }
}

// ---------------------------------------------------------------- Preview

final class PreviewPanel extends StudioPanel {

    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        if (spec == null) return;
        Graphics2D g = G.hq(g0);
        int w = getWidth(), h = getHeight();
        BindingStyle b = spec.style().impl;
        double W = geo.leafW(), H = geo.leafH();
        double gap = 50;

        g.setFont(G.serif(22f, Font.BOLD));
        g.setColor(G.INK);
        g.drawString(spec.title().isBlank() ? "Untitled" : spec.title(), 30, 40);
        g.setFont(G.sans(12.5f, Font.PLAIN));
        g.setColor(new Color(0x6B6255));
        g.drawString(b.name() + " · " + geo.pages() + " pages · " + spec.units().size(W, H), 30, 60);

        int cols = (int) Math.round(b.maxCol(spec) - b.minCol(spec) + 1);
        double sc = Math.min((w - 4 * gap - 60) / (3 * W + 10), (h - 170) / H);
        double fw = W * sc, fh = H * sc;
        double spw = Math.max(Math.max(geo.blockT() * sc, 26), Math.min(cols * 9, 140));
        double total = fw + spw + 2 * fw + 2 * gap;
        double x0 = (w - total) / 2, y0 = 90 + (h - 170 - fh) / 2;

        // front cover
        Rectangle2D front = new Rectangle2D.Double(x0, y0, fw, fh);
        drawFront(g, front, sc);
        caption(g, "Front", front.getCenterX(), front.getMaxY() + 26);

        // spine
        Rectangle2D spine = new Rectangle2D.Double(front.getMaxX() + gap, y0, spw, fh);
        shadow(g, spine);
        b.drawSpine(g, spec, geo, steps, spine);
        g.setColor(G.alpha(G.INK, 120));
        g.setStroke(new BasicStroke(1f));
        g.draw(spine);
        caption(g, "Spine", spine.getCenterX(), spine.getMaxY() + 26);
        note(g, "≈ " + spec.units().len(spec.style() == StyleKind.COPTIC ? geo.blockT() + 2 * spec.boardMm() : geo.blockT()),
                spine.getCenterX(), spine.getMaxY() + 42);
        if (spec.style() == StyleKind.KETTLE)
            note(g, "(sewing hidden under the cloth)", spine.getCenterX(), spine.getMaxY() + 57);

        // open
        Rectangle2D open = new Rectangle2D.Double(spine.getMaxX() + gap, y0, 2 * fw, fh);
        drawOpen(g, open);
        caption(g, "Open", open.getCenterX(), open.getMaxY() + 26);
        note(g, spec.style() == StyleKind.STAB
                        ? "Stab bindings don't open flat: allow at least " + spec.units().len(spec.insetMm() + 10) + " inner margin"
                        : spec.style() == StyleKind.PAMPHLET ? "Centre of the signature: the thread runs along the fold"
                        : "Centre of a signature: the thread runs along the fold",
                open.getCenterX(), open.getMaxY() + 42);
        g.dispose();
    }

    private static void shadow(Graphics2D g, Rectangle2D r) {
        g.setColor(new Color(0, 0, 0, 28));
        g.fill(new RoundRectangle2D.Double(r.getX() + 4, r.getY() + 5, r.getWidth(), r.getHeight(), 6, 6));
    }

    private void drawFront(Graphics2D g, Rectangle2D r, double sc) {
        Color c = spec.coverColor();
        shadow(g, r);
        switch (spec.style()) {
            case KETTLE -> {
                g.setColor(c);
                g.fill(r);
                g.setColor(G.mix(c, Color.BLACK, 0.18));
                g.fill(new Rectangle2D.Double(r.getX(), r.getY(), Math.max(6, (geo.blockT() / 2 + CoverCalc.caseGap(spec)) * sc), r.getHeight()));
                g.setColor(G.mix(c, Color.BLACK, 0.35));
                double jx = r.getX() + Math.max(6, (geo.blockT() / 2 + CoverCalc.caseGap(spec)) * sc);
                g.draw(new Line2D.Double(jx, r.getY(), jx, r.getMaxY()));
            }
            case COPTIC -> {
                g.setColor(c);
                g.fill(r);
                g.setColor(G.mix(c, Color.BLACK, 0.3));
                g.setStroke(new BasicStroke(2f));
                g.draw(new Rectangle2D.Double(r.getX() + 1, r.getY() + 1, r.getWidth() - 2, r.getHeight() - 2));
                g.setColor(spec.threadColor());
                g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (double yy : geo.stations()) {
                    double py = r.getY() + (yy + spec.squareMm()) * sc;
                    double hx = r.getX() + spec.insetMm() * sc;
                    g.draw(new Line2D.Double(r.getX(), py, hx, py));
                    g.fill(new Ellipse2D.Double(hx - 2.5, py - 2.5, 5, 5));
                }
            }
            case STAB -> {
                g.setColor(c);
                g.fill(r);
                drawStabPattern(g, r, sc);
            }
            default -> {
                g.setColor(c);
                g.fill(r);
                g.setColor(G.mix(c, Color.BLACK, 0.25));
                g.draw(new Line2D.Double(r.getX() + 3, r.getY(), r.getX() + 3, r.getMaxY()));
            }
        }
        String t = spec.title();
        if (t != null && !t.isBlank()) {
            g.setFont(G.serif((float) Math.max(12, Math.min(28, r.getWidth() / 9)), Font.BOLD));
            Color tc = G.textOn(c);
            g.setColor(spec.style() == StyleKind.KETTLE ? G.mix(tc, new Color(0xD4AF37), 0.6) : tc);
            G.centre(g, t, r.getCenterX() + (spec.style() == StyleKind.STAB ? spec.insetMm() * sc / 2 : 0), r.getY() + r.getHeight() * 0.3);
        }
    }

    private void drawStabPattern(Graphics2D g, Rectangle2D r, double sc) {
        List<Double> st = geo.stations();
        double hx = r.getX() + spec.insetMm() * sc;
        g.setColor(spec.threadColor());
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double first = r.getY() + st.get(0) * sc, last = r.getY() + st.get(st.size() - 1) * sc;
        g.draw(new Line2D.Double(hx, first, hx, last));
        for (double yy : st) {
            double py = r.getY() + yy * sc;
            g.draw(new Line2D.Double(r.getX(), py, hx, py));
        }
        g.draw(new Line2D.Double(hx, first, hx, r.getY()));
        g.draw(new Line2D.Double(hx, last, hx, r.getMaxY()));
        g.setColor(G.mix(spec.coverColor(), Color.BLACK, 0.5));
        for (double yy : st) {
            double py = r.getY() + yy * sc;
            g.fill(new Ellipse2D.Double(hx - 2, py - 2, 4, 4));
        }
    }

    private void drawOpen(Graphics2D g, Rectangle2D r) {
        shadow(g, r);
        g.setColor(G.PAPER);
        g.fill(r);
        double cx = r.getCenterX();
        g.setPaint(new GradientPaint((float) (cx - 26), 0, G.alpha(Color.BLACK, 0), (float) cx, 0, G.alpha(Color.BLACK, 40), true));
        g.fill(new Rectangle2D.Double(cx - 26, r.getY(), 52, r.getHeight()));
        g.setColor(new Color(0xD8CFBF));
        g.setStroke(new BasicStroke(1f));
        double lineGap = Math.max(6, r.getHeight() / 34);
        Random rnd = new Random(7);
        for (int side = 0; side < 2; side++) {
            double px = side == 0 ? r.getX() : cx;
            double pw = r.getWidth() / 2;
            double inner = spec.style() == StyleKind.STAB ? pw * 0.22 : pw * 0.12;
            double lx1 = side == 0 ? px + pw * 0.12 : px + inner;
            double lx2 = side == 0 ? px + pw - inner : px + pw * 0.88;
            for (double y = r.getY() + r.getHeight() * 0.1; y < r.getMaxY() - r.getHeight() * 0.1; y += lineGap) {
                double end = lx2 - rnd.nextDouble() * (lx2 - lx1) * 0.15;
                g.draw(new Line2D.Double(lx1, y, end, y));
            }
        }
        g.setColor(G.alpha(G.INK, 90));
        g.draw(new Line2D.Double(cx, r.getY(), cx, r.getMaxY()));
        g.draw(r);

        if (spec.style().impl.usesSignatures()) {
            // inside runs of signature 1, drawn along the gutter
            g.setColor(spec.threadColor());
            g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double sc = r.getHeight() / geo.leafH();
            int col = 1;
            for (int i = 1; i < steps.size(); i++) {
                SewStep a = steps.get(i - 1), b = steps.get(i);
                if (a.side() == Side.INSIDE && a.x() == col && b.x() == col && a.station() != b.station()) {
                    g.draw(new Line2D.Double(cx, r.getY() + geo.stations().get(a.station()) * sc,
                            cx, r.getY() + geo.stations().get(b.station()) * sc));
                }
            }
            g.setColor(G.INK);
            for (double yy : geo.stations()) {
                double py = r.getY() + yy * sc;
                g.fill(new Ellipse2D.Double(cx - 2, py - 2, 4, 4));
            }
        } else {
            double sc = r.getHeight() / geo.leafH();
            g.setColor(G.alpha(spec.threadColor(), 160));
            g.setStroke(G.dashed(1.6f));
            g.draw(new Line2D.Double(cx - 6, r.getY() + 4, cx - 6, r.getMaxY() - 4));
            g.setColor(G.INK);
            for (double yy : geo.stations())
                g.fill(new Ellipse2D.Double(cx - 8, r.getY() + yy * sc - 2, 4, 4));
        }
    }
}

// ---------------------------------------------------------------- Signatures

final class SignaturesPanel extends StudioPanel {
    private final DefaultTableModel model = new DefaultTableModel() {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JLabel hint = new JLabel();
    private final JPanel drawing = new JPanel() {
        @Override protected void paintComponent(Graphics g) { super.paintComponent(g); paintDrawing(g); }
    };

    SignaturesPanel() {
        setLayout(new BorderLayout());
        drawing.setBackground(G.DESK);
        drawing.setPreferredSize(new Dimension(700, 420));
        drawing.setMinimumSize(new Dimension(200, 150));
        table.setRowHeight(22);
        table.setFillsViewportHeight(true);
        hint.setBorder(new EmptyBorder(6, 10, 6, 10));
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(hint, BorderLayout.NORTH);
        bottom.add(new JScrollPane(table), BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, drawing, bottom);
        split.setResizeWeight(0.62);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);
    }

    @Override JComponent exportComponent() { return drawing; }

    @Override void dataChanged() {
        boolean sig = spec.style().impl.usesSignatures();
        model.setRowCount(0);
        if (sig) {
            model.setColumnIdentifiers(new Object[] { "Signature", "Sheet (from outside)",
                    "Outside face: left | right", "Inside face: left | right" });
            int m = spec.sheetsPerSig();
            for (int s = 0; s < spec.signatures(); s++) {
                int p0 = s * 4 * m;
                for (int j = 0; j < m; j++) {
                    int outL = p0 + 4 * m - 2 * j, outR = p0 + 1 + 2 * j;
                    int inL = p0 + 2 + 2 * j, inR = p0 + 4 * m - 1 - 2 * j;
                    model.addRow(new Object[] { s + 1, j + 1, outL + "  |  " + outR, inL + "  |  " + inR });
                }
            }
            hint.setText("<html>Print each sheet <b>double-sided, flipping on the " + geo.duplexEdge()
                    + "</b>, outside face first. Nest the sheets in order (sheet 1 outermost) and fold.</html>");
        } else {
            model.setColumnIdentifiers(new Object[] { "Leaf", "Left half (recto)", "Right half (verso)" });
            for (int j = 0; j < spec.sheetsPerSig(); j++)
                model.addRow(new Object[] { j + 1, 2 * j + 1, 2 * j + 2 });
            hint.setText("<html>Print <b>one side only</b>. Fold each sheet in half with the print outside; "
                    + "the fold becomes the fore-edge and the open edges go to the spine.</html>");
        }
    }

    private void paintDrawing(Graphics g0) {
        if (spec == null) return;
        Graphics2D g = G.hq(g0);
        int w = drawing.getWidth(), h = drawing.getHeight();
        g.setFont(G.sans(13f, Font.BOLD));
        g.setColor(G.INK);
        g.drawString("Looking down on the head of the book (spine on the left)", 24, 28);
        double left = 80, right = 190, top = 50, bottom = 24;
        double len = w - left - right;
        Color thread = spec.threadColor();
        if (spec.style().impl.usesSignatures()) {
            int N = spec.signatures(), m = spec.sheetsPerSig();
            double sh = (h - top - bottom) / (double) N;
            double gap = Math.min(5, sh / (2 * m + 1.6));
            boolean coptic = spec.style() == StyleKind.COPTIC;
            boolean limp = spec.style() == StyleKind.LONG_STITCH;
            boolean pamph = spec.style() == StyleKind.PAMPHLET;
            if (coptic) {
                g.setColor(G.mix(spec.coverColor(), Color.BLACK, 0.15));
                g.fill(new Rectangle2D.Double(left, top - 10, len, 7));
                g.fill(new Rectangle2D.Double(left, h - bottom + 3, len, 7));
            }
            if (limp || pamph) {
                g.setColor(spec.coverColor());
                g.setStroke(new BasicStroke(3f));
                double r0 = (pamph ? m * gap + 4 : (h - top - bottom) / 2 + 4);
                double cy = pamph ? top + sh / 2 : top + (h - top - bottom) / 2;
                Path2D c = new Path2D.Double();
                c.moveTo(left + len, cy - r0);
                c.lineTo(left + r0 * 0.25, cy - r0);
                c.quadTo(left - 6, cy - r0, left - 6, cy);
                c.quadTo(left - 6, cy + r0, left + r0 * 0.25, cy + r0);
                c.lineTo(left + len, cy + r0);
                g.draw(c);
            }
            for (int s = 0; s < N; s++) {
                double yc = top + (s + 0.5) * sh;
                if (gap < 1.1) {
                    double hh = Math.max(1.5, sh * 0.42);
                    g.setColor(new Color(0xE9DFCC));
                    g.fill(new RoundRectangle2D.Double(left, yc - hh, len, 2 * hh, hh * 2, hh * 2));
                    g.setColor(G.FAINT);
                    g.draw(new RoundRectangle2D.Double(left, yc - hh, len, 2 * hh, hh * 2, hh * 2));
                } else {
                    for (int k = 0; k < m; k++) {
                        double d = (m - k) * gap;
                        double end = left + len + (m - 1 - k) * 0.7;      // a little creep at the fore-edge
                        g.setColor(k == 0 ? new Color(0x9C8E78) : G.FAINT);
                        g.setStroke(new BasicStroke(1.1f));
                        Path2D p = new Path2D.Double();
                        p.moveTo(end, yc - d);
                        p.lineTo(left + d, yc - d);
                        p.append(new Arc2D.Double(left, yc - d, 2 * d, 2 * d, 90, 180, Arc2D.OPEN), true);
                        p.lineTo(end, yc + d);
                        g.draw(p);
                    }
                }
                g.setColor(thread);
                double dot = Math.max(2.5, Math.min(5, gap * 1.2));
                g.fill(new Ellipse2D.Double(left + Math.max(gap, 1) * 0.6 - dot / 2, yc - dot / 2, dot, dot));
                if (sh >= 13) {
                    int p0 = s * 4 * m;
                    g.setColor(G.INK);
                    g.setFont(G.sans((float) Math.min(12, sh * 0.6), Font.PLAIN));
                    String lab = (pamph ? "Signature" : "Sig " + (s + 1)) + "  ·  pp. " + (p0 + 1) + "–" + (p0 + 4 * m);
                    g.drawString(lab, (float) (left + len + 16), (float) (yc + 4));
                }
            }
            g.setColor(new Color(0x6B6255));
            g.setFont(G.sans(11f, Font.PLAIN));
            g.drawString("spine", (float) (left - 52), (float) (top + (h - top - bottom) / 2 + 4));
        } else {
            int L = spec.sheetsPerSig();
            double gap = Math.min(7, (h - top - bottom - 20) / (L * 1.6));
            double y0 = top + 10;
            double hx = left + spec.insetMm() / geo.leafW() * len;
            g.setColor(spec.coverColor());
            g.fill(new Rectangle2D.Double(left, y0 - 6, len, 4));
            for (int k = 0; k < L; k++) {
                double yy = y0 + k * gap * 1.6;
                double d = gap * 0.4;
                g.setColor(G.FAINT);
                g.setStroke(new BasicStroke(1f));
                Path2D p = new Path2D.Double();
                p.moveTo(left, yy);
                p.lineTo(left + len - d, yy);
                p.append(new Arc2D.Double(left + len - 2 * d, yy, 2 * d, 2 * d, 90, -180, Arc2D.OPEN), true);
                p.lineTo(left, yy + 2 * d);
                g.draw(p);
            }
            double yEnd = y0 + (L - 1) * gap * 1.6 + gap * 0.8;
            g.setColor(spec.coverColor());
            g.fill(new Rectangle2D.Double(left, yEnd + 2, len, 4));
            g.setColor(thread);
            g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Rectangle2D.Double(left - 6, y0 - 9, hx - left + 6, yEnd - y0 + 20));
            g.setColor(G.INK);
            g.setFont(G.sans(12f, Font.PLAIN));
            g.drawString(L + " folded leaves · fold at the fore-edge", (float) (left + len + 16), (float) ((y0 + yEnd) / 2 + 4));
            g.setColor(new Color(0x6B6255));
            g.setFont(G.sans(11f, Font.PLAIN));
            g.drawString("spine", (float) (left - 52), (float) ((y0 + yEnd) / 2 + 4));
            g.drawString("thread wraps the spine", (float) left, (float) (yEnd + 30));
        }
        g.dispose();
    }
}

// ---------------------------------------------------------------- Template

final class TemplatePanel extends StudioPanel {

    double contentW() {
        double w = spec.style() == StyleKind.STAB ? spec.insetMm() + 40 : 55;
        if (spec.style() == StyleKind.COPTIC) w += 50;
        if (spec.style() == StyleKind.LONG_STITCH) w += geo.blockT() + 2 + 80;
        return Math.max(w, 130);
    }

    double contentH() { return 30 + geo.leafH() + 22; }

    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        if (spec == null) return;
        Graphics2D g = G.hq(g0);
        double cw = contentW() + 20, ch = contentH() + 20;
        double sc = Math.min((getWidth() - 40) / cw, (getHeight() - 40) / ch);
        double ox = (getWidth() - cw * sc) / 2, oy = (getHeight() - ch * sc) / 2;
        g.setColor(new Color(0, 0, 0, 30));
        g.fill(new Rectangle2D.Double(ox + 5, oy + 6, cw * sc, ch * sc));
        g.setColor(Color.WHITE);
        g.fill(new Rectangle2D.Double(ox, oy, cw * sc, ch * sc));
        g.translate(ox + 10 * sc, oy + 10 * sc);
        g.scale(sc, sc);
        drawTemplate(g);
        g.dispose();
    }

    private static void font(Graphics2D g, float mm, int style) { g.setFont(G.sans(mm, style)); }

    /** Draws the whole template in millimetre user space. */
    void drawTemplate(Graphics2D g) {
        BindingStyle b = spec.style().impl;
        Units u = spec.units();
        double H = geo.leafH();
        g.setColor(G.INK);
        font(g, 4.2f, Font.BOLD);
        g.drawString("Bookbinding Studio · " + b.name() + " template", 0, 5);
        font(g, 2.8f, Font.PLAIN);
        g.drawString("Leaf " + u.size(geo.leafW(), H) + " · " + geo.stations().size()
                + " stations · print at 100% / Actual size", 0, 10);

        // calibration bar
        g.setStroke(new BasicStroke(0.25f));
        g.draw(new Rectangle2D.Double(0, 13, 50, 3));
        for (int i = 0; i <= 50; i += 5) g.draw(new Line2D.Double(i, 16, i, i % 10 == 0 ? 12 : 14));
        g.fill(new Rectangle2D.Double(0, 13, 10, 3));
        g.fill(new Rectangle2D.Double(20, 13, 10, 3));
        g.fill(new Rectangle2D.Double(40, 13, 10, 3));
        font(g, 2.6f, Font.PLAIN);
        g.drawString("← this bar must measure exactly 50 mm (1.97 in)", 53, 16);

        double y0 = 28, x = 0;
        if (spec.style() == StyleKind.STAB) {
            edgeGuide(g, x, y0, spec.insetMm() + 30, "Hole guide: align the left edge with the spine", "Spine edge");
            x += spec.insetMm() + 40;
        } else {
            foldGuide(g, x, y0);
            x += 55;
        }
        if (spec.style() == StyleKind.COPTIC) {
            edgeGuide(g, x, y0, 35, "Board drilling guide (both boards)", "Spine edge of board");
        }
        if (spec.style() == StyleKind.LONG_STITCH) coverSpineGuide(g, x, y0);

        font(g, 2.6f, Font.PLAIN);
        g.setColor(G.INK);
        g.drawString("Check the 50 mm bar before punching. Pierce from the inside of the fold outwards.", 0, (float) (y0 + H + 12));
        g.drawString("Bookbinding Studio by Mario Gianota", 0, (float) (y0 + H + 17));
    }

    private void stationLabels(Graphics2D g, double x, double y0) {
        font(g, 2.5f, Font.PLAIN);
        Units u = spec.units();
        List<Double> st = geo.stations();
        for (int i = 0; i < st.size(); i++)
            g.drawString((i + 1) + "  " + u.len(st.get(i)), (float) x, (float) (y0 + st.get(i) + 0.9));
    }

    private void foldGuide(Graphics2D g, double x, double y0) {
        double H = geo.leafH(), w = 40, cx = x + 20;
        g.setColor(G.INK);
        font(g, 3f, Font.BOLD);
        g.drawString("Signature punching guide", (float) x, (float) (y0 - 2));
        g.setStroke(new BasicStroke(0.3f));
        g.draw(new Rectangle2D.Double(x, y0, w, H));
        g.setStroke(new BasicStroke(0.3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 2f, 1.5f }, 0f));
        g.draw(new Line2D.Double(cx, y0, cx, y0 + H));
        font(g, 2.4f, Font.PLAIN);
        G.centre(g, "HEAD", cx - 8, y0 + 4);
        G.centre(g, "TAIL", cx - 8, y0 + H - 2);
        g.setStroke(new BasicStroke(0.25f));
        for (double yy : geo.stations()) {
            double py = y0 + yy;
            g.draw(new Line2D.Double(cx - 5, py, cx + 5, py));
            g.draw(new Ellipse2D.Double(cx - 0.8, py - 0.8, 1.6, 1.6));
        }
        stationLabels(g, cx + 6.5, y0);
        font(g, 2.3f, Font.ITALIC);
        g.drawString("Fold on the dashed line and sit it in the open signature.", (float) x, (float) (y0 + H + 5));
    }

    private void edgeGuide(Graphics2D g, double x, double y0, double w, String title, String edgeLabel) {
        double H = geo.leafH(), hx = x + spec.insetMm();
        g.setColor(G.INK);
        font(g, 3f, Font.BOLD);
        g.drawString(title, (float) x, (float) (y0 - 2));
        g.setStroke(new BasicStroke(0.25f));
        g.draw(new Rectangle2D.Double(x, y0, w, H));
        g.setStroke(new BasicStroke(0.9f));
        g.draw(new Line2D.Double(x, y0, x, y0 + H));
        g.setStroke(new BasicStroke(0.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 1f, 1f }, 0f));
        g.draw(new Line2D.Double(hx, y0, hx, y0 + H));
        g.setStroke(new BasicStroke(0.25f));
        for (double yy : geo.stations()) {
            double py = y0 + yy;
            g.draw(new Line2D.Double(hx - 3, py, hx + 3, py));
            g.draw(new Ellipse2D.Double(hx - 0.8, py - 0.8, 1.6, 1.6));
        }
        stationLabels(g, hx + 5, y0);
        AffineTransform at = g.getTransform();
        g.translate(x - 1.2, y0 + H / 2);
        g.rotate(-Math.PI / 2);
        font(g, 2.4f, Font.PLAIN);
        G.centre(g, edgeLabel + " · holes " + spec.units().len(spec.insetMm()) + " in", 0, 0);
        g.setTransform(at);
    }

    private void coverSpineGuide(Graphics2D g, double x, double y0) {
        double H = geo.leafH(), T = geo.blockT() + 2, flap = 15, w = T + 2 * flap;
        int N = spec.signatures();
        g.setColor(G.INK);
        font(g, 3f, Font.BOLD);
        g.drawString("Cover spine guide", (float) x, (float) (y0 - 2));
        g.setStroke(new BasicStroke(0.25f));
        g.draw(new Rectangle2D.Double(x, y0, w, H));
        g.setStroke(new BasicStroke(0.3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 2f, 1.5f }, 0f));
        g.draw(new Line2D.Double(x + flap, y0, x + flap, y0 + H));
        g.draw(new Line2D.Double(x + flap + T, y0, x + flap + T, y0 + H));
        g.setStroke(new BasicStroke(0.2f));
        double colGap = geo.blockT() / N;
        for (int i = 0; i < N; i++) {
            double cx = x + flap + 1 + (i + 0.5) * colGap;
            for (double yy : geo.stations())
                g.draw(new Ellipse2D.Double(cx - 0.45, y0 + yy - 0.45, 0.9, 0.9));
        }
        font(g, 2.2f, Font.PLAIN);
        AffineTransform at = g.getTransform();
        g.translate(x + flap - 1.5, y0 + H / 2);
        g.rotate(-Math.PI / 2);
        G.centre(g, "spine folds · " + N + " columns, one per signature", 0, 0);
        g.setTransform(at);
        if (colGap < 1.5) {
            g.setColor(G.CUT);
            font(g, 2.3f, Font.BOLD);
            g.drawString("Holes only " + Units.n1(colGap) + " mm apart:", (float) (x + w + 2), (float) (y0 + 6));
            g.drawString("use fewer, thicker signatures", (float) (x + w + 2), (float) (y0 + 9));
            g.drawString("or a thicker cover.", (float) (x + w + 2), (float) (y0 + 12));
            g.setColor(G.INK);
        }
    }

    // ---- printing, tiled vertically when the template is taller than the page
    void print(Component parent) {
        if (spec == null) return;
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("Bookbinding Studio template");
        final double pt = 72 / 25.4;
        job.setPrintable((gr, pf, pageIndex) -> {
            double ih = pf.getImageableHeight() / pt, iw = pf.getImageableWidth() / pt;
            double chunk = ih - 14;
            int pages = (int) Math.ceil(contentH() / (chunk - 10));
            if (contentH() <= ih - 4) pages = 1;
            if (pageIndex >= pages) return Printable.NO_SUCH_PAGE;
            Graphics2D g = G.hq(gr);
            g.translate(pf.getImageableX(), pf.getImageableY());
            g.scale(pt, pt);
            double offset = pages == 1 ? 0 : pageIndex * (chunk - 10);
            g.clip(new Rectangle2D.Double(0, 0, iw, pages == 1 ? ih : chunk));
            g.translate(0, -offset);
            drawTemplate(g);
            g.dispose();
            if (pages > 1) {
                Graphics2D f = G.hq(gr);
                f.translate(pf.getImageableX(), pf.getImageableY());
                f.scale(pt, pt);
                f.setColor(G.INK);
                f.setFont(G.sans(2.6f, Font.PLAIN));
                f.drawString("Page " + (pageIndex + 1) + " of " + pages
                        + " · pages overlap by 10 mm: align the top of each page with the bottom of the last", 0, (float) (chunk + 6));
                f.dispose();
            }
            return Printable.PAGE_EXISTS;
        });
        if (job.printDialog()) {
            try { job.print(); }
            catch (PrinterException ex) {
                JOptionPane.showMessageDialog(parent, "Printing failed: " + ex.getMessage(), "Print", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}

// ---------------------------------------------------------------- Sewing

final class SewingPanel extends StudioPanel {
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> list = new JList<>(listModel);
    private final JTextArea instr = new JTextArea(3, 40);
    private final JLabel counter = new JLabel(" ");
    private final JCheckBox showAll = new JCheckBox("Show whole path");
    private final JSlider speed = new JSlider(1, 10, 4);
    private final JButton playBtn = new JButton("▶ Play");
    private final javax.swing.Timer timer;
    private final JPanel canvas = new JPanel() {
        @Override protected void paintComponent(Graphics g) { super.paintComponent(g); paintCanvas(g); }
    };
    private int current;
    private double[] cumulative = new double[0];
    private boolean listUpdating;
    private StyleKind lastKind;

    SewingPanel() {
        setLayout(new BorderLayout());
        canvas.setBackground(G.DESK);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFont(G.sans(12f, Font.PLAIN));
        list.addListSelectionListener(e -> {
            if (!listUpdating && !e.getValueIsAdjusting() && list.getSelectedIndex() >= 0) go(list.getSelectedIndex());
        });
        JScrollPane listScroll = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        listScroll.setPreferredSize(new Dimension(300, 100));
        listScroll.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(0xCFC6B8)));

        instr.setLineWrap(true);
        instr.setWrapStyleWord(true);
        instr.setEditable(false);
        instr.setFont(G.sans(15f, Font.PLAIN));
        instr.setBorder(new EmptyBorder(8, 12, 8, 12));
        instr.setBackground(new Color(0xFFFCF5));

        JButton first = new JButton("|◀"), prev = new JButton("◀"), next = new JButton("▶"), last = new JButton("▶|");
        first.setToolTipText("First step");
        prev.setToolTipText("Previous step (←)");
        next.setToolTipText("Next step (→)");
        last.setToolTipText("Last step");
        first.addActionListener(e -> go(0));
        prev.addActionListener(e -> go(current - 1));
        next.addActionListener(e -> go(current + 1));
        last.addActionListener(e -> go(steps.size() - 1));
        timer = new javax.swing.Timer(1000, e -> {
            if (current >= steps.size() - 1) stop(); else go(current + 1);
        });
        playBtn.addActionListener(e -> { if (timer.isRunning()) stop(); else start(); });
        speed.addChangeListener(e -> timer.setDelay(2400 / speed.getValue()));
        timer.setDelay(2400 / speed.getValue());
        speed.setPreferredSize(new Dimension(110, 24));
        showAll.addActionListener(e -> canvas.repaint());
        showAll.setOpaque(false);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        controls.add(first); controls.add(prev); controls.add(playBtn); controls.add(next); controls.add(last);
        controls.add(new JLabel("  Speed"));
        controls.add(speed);
        controls.add(showAll);
        controls.add(Box.createHorizontalStrut(12));
        controls.add(counter);

        JPanel south = new JPanel(new BorderLayout());
        south.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xCFC6B8)));
        south.add(controls, BorderLayout.NORTH);
        south.add(instr, BorderLayout.CENTER);

        JPanel centre = new JPanel(new BorderLayout());
        centre.add(canvas, BorderLayout.CENTER);
        centre.add(south, BorderLayout.SOUTH);
        add(centre, BorderLayout.CENTER);
        add(listScroll, BorderLayout.EAST);

        InputMap im = getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "next");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "prev");
        getActionMap().put("next", new AbstractAction() { public void actionPerformed(ActionEvent e) { go(current + 1); } });
        getActionMap().put("prev", new AbstractAction() { public void actionPerformed(ActionEvent e) { go(current - 1); } });
        canvas.setFocusable(true);
        canvas.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { canvas.requestFocusInWindow(); }
        });
    }

    private void start() {
        if (current >= steps.size() - 1) go(0);
        timer.start();
        playBtn.setText("❚❚ Pause");
    }

    private void stop() {
        timer.stop();
        playBtn.setText("▶ Play");
    }

    @Override JComponent exportComponent() { return canvas; }

    @Override void dataChanged() {
        cumulative = new double[steps.size()];
        double t = 0;
        listUpdating = true;
        listModel.clear();
        for (int i = 0; i < steps.size(); i++) {
            t += steps.get(i).lengthMm();
            cumulative[i] = t;
            String txt = steps.get(i).text();
            listModel.addElement((i + 1) + ".  " + (txt.length() > 70 ? txt.substring(0, 68) + "…" : txt));
        }
        listUpdating = false;
        if (lastKind != spec.style()) { current = 0; lastKind = spec.style(); stop(); }
        go(Math.min(current, steps.size() - 1));
    }

    void go(int i) {
        if (steps.isEmpty()) return;
        current = Math.max(0, Math.min(steps.size() - 1, i));
        SewStep s = steps.get(current);
        instr.setText(s.text());
        counter.setText("Step " + (current + 1) + " of " + steps.size() + "   ·   thread used so far ≈ "
                + spec.units().thread(cumulative[current]));
        listUpdating = true;
        list.setSelectedIndex(current);
        list.ensureIndexIsVisible(current);
        listUpdating = false;
        canvas.repaint();
    }

    int currentForTest() { return current; }

    // ---- drawing

    private void paintCanvas(Graphics g0) {
        if (spec == null || steps.isEmpty()) return;
        Graphics2D g = G.hq(g0);
        if (spec.style() == StyleKind.STAB) paintStab(g); else paintSignatures(g);
        g.dispose();
    }

    private void legend(Graphics2D g, boolean stab) {
        g.setFont(G.sans(11.5f, Font.PLAIN));
        Color tc = spec.threadColor();
        int x = 20, y = 22;
        g.setColor(tc);
        g.setStroke(new BasicStroke(2.5f));
        g.drawLine(x, y - 4, x + 26, y - 4);
        g.setColor(G.INK);
        g.drawString(stab ? "thread on the front" : "outside the spine", x + 32, y);
        x += 160;
        g.setColor(tc);
        g.setStroke(stab ? new BasicStroke(2.5f) : G.dashed(2f));
        g.drawLine(x, y - 4, x + 26, y - 4);
        g.setColor(G.INK);
        g.drawString(stab ? "thread on the back" : "inside the fold", x + 32, y);
        if (!stab) {
            x += 140;
            g.setColor(tc);
            g.setStroke(new BasicStroke(2f));
            g.draw(new Ellipse2D.Double(x + 6, y - 9, 12, 9));
            g.setColor(G.INK);
            g.drawString("kettle / chain / lock loop", x + 32, y);
        }
    }

    private void paintSignatures(Graphics2D g) {
        BindingStyle b = spec.style().impl;
        int w = canvas.getWidth(), h = canvas.getHeight();
        int N = spec.signatures();
        double minC = b.minCol(spec), maxC = b.maxCol(spec);
        int cols = (int) Math.round(maxC - minC) + 1;
        double top = 70, bottom = 36, left = 70, right = 30;
        double colW = Math.min(spec.style() == StyleKind.PAMPHLET ? 140 : 90, (w - left - right) / (double) cols);
        double x0 = left + ((w - left - right) - colW * cols) / 2.0;
        double sc = (h - top - bottom) / geo.leafH();
        List<Double> st = geo.stations();
        legend(g, false);

        boolean coptic = spec.style() == StyleKind.COPTIC;
        boolean limp = spec.style() == StyleKind.LONG_STITCH;
        java.util.function.DoubleUnaryOperator px = x -> x0 + (x - minC) * colW + colW / 2;

        if (limp) {
            g.setColor(G.alpha(spec.coverColor(), 70));
            g.fill(new RoundRectangle2D.Double(px.applyAsDouble(1) - colW * 0.48, top - 12,
                    (N - 1) * colW + colW * 0.96, geo.leafH() * sc + 24, 10, 10));
            g.setColor(G.INK);
            g.setFont(G.sans(11f, Font.ITALIC));
            G.centre(g, "cover spine (sewn through)", (px.applyAsDouble(1) + px.applyAsDouble(N)) / 2, top + geo.leafH() * sc + 26);
        }

        for (int c = (int) Math.round(minC); c <= (int) Math.round(maxC); c++) {
            boolean boardCol = coptic && (c == 0 || c == N + 1);
            double cx = px.applyAsDouble(c);
            double sw = Math.max(6, colW * (boardCol ? 0.5 : 0.62));
            Rectangle2D r = new Rectangle2D.Double(cx - sw / 2, top, sw, geo.leafH() * sc);
            g.setColor(boardCol ? G.mix(spec.coverColor(), G.BOARD, 0.5) : G.PAPER);
            g.fill(r);
            g.setColor(G.alpha(G.INK, 90));
            g.setStroke(new BasicStroke(1f));
            g.draw(r);
            if (!boardCol) {
                g.setColor(G.FAINT);
                g.draw(new Line2D.Double(cx, top, cx, top + geo.leafH() * sc));
            }
            if (colW >= 26 || c % Math.max(1, (int) Math.ceil(30 / colW)) == 0 || boardCol) {
                g.setColor(G.INK);
                g.setFont(G.sans((float) Math.min(12, Math.max(8.5, colW * 0.3)), Font.BOLD));
                String lab = boardCol ? (c == 0 ? "Front" : "Back")
                        : spec.style() == StyleKind.PAMPHLET ? "Signature" : (colW >= 40 ? "Sig " + c : String.valueOf(c));
                G.centre(g, lab, cx, top - 10);
            }
            g.setColor(G.INK);
            for (double yy : st) {
                double py = top + yy * sc;
                g.fill(new Ellipse2D.Double(cx - 2.2, py - 2.2, 4.4, 4.4));
            }
        }
        g.setFont(G.sans(11f, Font.PLAIN));
        g.setColor(new Color(0x6B6255));
        for (int i = 0; i < st.size(); i++)
            g.drawString(String.valueOf(i + 1), (float) (x0 - 22), (float) (top + st.get(i) * sc + 4));
        g.setFont(G.sans(10.5f, Font.BOLD));
        g.drawString("HEAD", (float) (x0 - 46), (float) (top + 4));
        g.drawString("TAIL", (float) (x0 - 46), (float) (top + geo.leafH() * sc));

        int upto = showAll.isSelected() ? steps.size() - 1 : current;
        double off = Math.min(3.5, colW * 0.07);
        Color tc = spec.threadColor();
        for (int i = 1; i <= upto; i++) {
            SewStep a = steps.get(i - 1), s = steps.get(i);
            boolean cur = i == current;
            Color c = cur ? tc : G.alpha(tc, showAll.isSelected() && i > current ? 60 : 150);
            float wdt = cur ? 3.6f : 2.2f;
            double o = a.side() == Side.INSIDE ? -off : off;
            double x1 = px.applyAsDouble(a.x()) + o, x2 = px.applyAsDouble(s.x()) + o;
            double y1 = top + st.get(a.station()) * sc, y2 = top + st.get(s.station()) * sc;
            g.setColor(c);
            g.setStroke(a.side() == Side.INSIDE ? G.dashed(wdt) : new BasicStroke(wdt, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (s.action() == Action.LINK) {
                // round the board edge: a small arc out beyond the board
                double bx = px.applyAsDouble(s.x());
                double dir = s.x() == 0 ? -1 : 1;
                Path2D p = new Path2D.Double();
                p.moveTo(x1, y1);
                p.quadTo(bx + dir * colW * 0.45, (y1 + y2) / 2 - 10, bx, y2);
                g.draw(p);
            } else if (Math.abs(x1 - x2) < 0.5 && Math.abs(y1 - y2) < 0.5) {
                // same spot: nothing to draw except the glyph
            } else {
                g.draw(new Line2D.Double(x1, y1, x2, y2));
            }
            if (s.side() != a.side() && s.action() == Action.PASS) {
                g.setStroke(new BasicStroke(wdt));
                g.draw(new Line2D.Double(x2 - off, y2, x2 + off, y2));
            }
            glyph(g, s, px.applyAsDouble(s.x()), y2, c, colW);
        }
        // start marker and needle
        SewStep s0 = steps.get(0);
        glyph(g, s0, px.applyAsDouble(s0.x()), top + st.get(s0.station()) * sc, tc, colW);
        SewStep cs = steps.get(current);
        double nx = px.applyAsDouble(cs.x()), ny = top + st.get(cs.station()) * sc;
        g.setColor(G.ACCENT);
        g.setStroke(new BasicStroke(2f));
        g.draw(new Ellipse2D.Double(nx - 9, ny - 9, 18, 18));
        g.setFont(G.sans(11f, Font.BOLD));
        String where = cs.side() == Side.INSIDE ? "inside" : "outside";
        g.drawString(where, (float) (nx + 12), (float) (ny - 10));
    }

    private void glyph(Graphics2D g, SewStep s, double x, double y, Color c, double colW) {
        g.setColor(c);
        double r = Math.max(4, Math.min(8, colW * 0.18));
        switch (s.action()) {
            case KETTLE, CHAIN, LOCK -> {
                g.setStroke(new BasicStroke(2f));
                g.draw(new Ellipse2D.Double(x - r, y - r * 0.75, 2 * r, 1.5 * r));
            }
            case TIE -> {
                g.fill(new Ellipse2D.Double(x - 4, y - 4, 8, 8));
                g.setStroke(new BasicStroke(1.6f));
                g.draw(new Line2D.Double(x - 7, y - 6, x + 7, y + 6));
            }
            case TIE_OFF -> {
                g.setStroke(new BasicStroke(2f));
                g.draw(new Ellipse2D.Double(x - 6, y - 6, 12, 12));
                g.fill(new Ellipse2D.Double(x - 3, y - 3, 6, 6));
            }
            case START -> {
                g.setStroke(new BasicStroke(2f));
                g.draw(new Line2D.Double(x, y, x + 14, y + 14));
                g.fill(new Ellipse2D.Double(x + 11, y + 11, 6, 6));
            }
            default -> { }
        }
    }

    private void paintStab(Graphics2D g) {
        int w = canvas.getWidth(), h = canvas.getHeight();
        legend(g, true);
        double top = 70, bottom = 40, gap = 60;
        double H = geo.leafH();
        double sc = (h - top - bottom) / H;
        double regionMm = Math.min(geo.leafW(), spec.insetMm() * 3 + 40);
        double pw = Math.min(regionMm * sc, (w - 3 * gap) / 2);
        sc = Math.min(sc, pw / regionMm);
        double ph = H * sc;
        double fx = (w - 2 * pw - gap) / 2, bx = fx + pw + gap;
        List<Double> st = geo.stations();

        // front: spine on the left. back (turned over): spine on the right.
        Rectangle2D fr = new Rectangle2D.Double(fx, top, pw, ph), br = new Rectangle2D.Double(bx, top, pw, ph);
        for (Rectangle2D r : List.of(fr, br)) {
            g.setColor(G.mix(spec.coverColor(), Color.WHITE, 0.65));
            g.fill(r);
            g.setColor(G.alpha(G.INK, 90));
            g.setStroke(new BasicStroke(1f));
            g.draw(r);
        }
        g.setColor(G.INK);
        g.setStroke(new BasicStroke(3f));
        g.draw(new Line2D.Double(fx, top, fx, top + ph));
        g.draw(new Line2D.Double(bx + pw, top, bx + pw, top + ph));
        caption(g, "FRONT", fr.getCenterX(), top - 12);
        caption(g, "BACK (book turned over)", br.getCenterX(), top - 12);
        note(g, "spine edge", fx + 30, top + ph + 18);
        note(g, "spine edge", bx + pw - 30, top + ph + 18);

        double fhx = fx + spec.insetMm() * sc, bhx = bx + pw - spec.insetMm() * sc;
        g.setFont(G.sans(11f, Font.PLAIN));
        for (int i = 0; i < st.size(); i++) {
            double py = top + st.get(i) * sc;
            g.setColor(G.INK);
            g.fill(new Ellipse2D.Double(fhx - 2.5, py - 2.5, 5, 5));
            g.fill(new Ellipse2D.Double(bhx - 2.5, py - 2.5, 5, 5));
            g.setColor(new Color(0x6B6255));
            g.drawString(String.valueOf(i + 1), (float) (fhx + 8), (float) (py + 4));
            g.drawString(String.valueOf(i + 1), (float) (bhx - 16), (float) (py + 4));
        }

        int upto = showAll.isSelected() ? steps.size() - 1 : current;
        Color tc = spec.threadColor();
        for (int i = 0; i <= upto; i++) {
            SewStep s = steps.get(i);
            SewStep a = i > 0 ? steps.get(i - 1) : s;
            boolean cur = i == current;
            Color c = cur ? tc : G.alpha(tc, showAll.isSelected() && i > current ? 60 : 150);
            g.setColor(c);
            g.setStroke(new BasicStroke(cur ? 3.8f : 2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double y1 = top + st.get(a.station()) * sc, y2 = top + st.get(s.station()) * sc;
            switch (s.action()) {
                case PASS, TIE_OFF -> {
                    double hx = a.side() == Side.FRONT ? fhx : bhx;
                    g.draw(new Line2D.Double(hx, y1, hx, y2));
                }
                case WRAP_SPINE -> {
                    g.draw(new Line2D.Double(fhx, y2, fx, y2));
                    g.draw(new Line2D.Double(bhx, y2, bx + pw, y2));
                }
                case WRAP_HEAD -> {
                    g.draw(new Line2D.Double(fhx, y2, fhx, top));
                    g.draw(new Line2D.Double(bhx, y2, bhx, top));
                }
                case WRAP_TAIL -> {
                    g.draw(new Line2D.Double(fhx, y2, fhx, top + ph));
                    g.draw(new Line2D.Double(bhx, y2, bhx, top + ph));
                }
                default -> { }
            }
        }
        SewStep cs = steps.get(current);
        double ny = top + st.get(cs.station()) * sc;
        double nx = cs.side() == Side.FRONT ? fhx : bhx;
        g.setColor(G.ACCENT);
        g.setStroke(new BasicStroke(2f));
        g.draw(new Ellipse2D.Double(nx - 9, ny - 9, 18, 18));
        g.setFont(G.sans(11f, Font.BOLD));
        g.drawString("needle", (float) (nx + 12), (float) (ny - 10));
        double ty = top + st.get(0) * sc;
        g.setColor(G.alpha(tc, 150));
        g.setStroke(G.dashed(1.6f));
        g.draw(new Line2D.Double(bhx, ty, bhx - 22, ty + 18));
        g.setFont(G.sans(10f, Font.ITALIC));
        g.setColor(new Color(0x6B6255));
        g.drawString("tail", (float) (bhx - 44), (float) (ty + 30));
    }
}

// ---------------------------------------------------------------- Cover & boards

final class CoverPanel extends StudioPanel {
    private double sc, ox, oy;

    private Rectangle2D r(double x, double y, double w, double h) {
        return new Rectangle2D.Double(ox + x * sc, oy + y * sc, w * sc, h * sc);
    }
    private double X(double mm) { return ox + mm * sc; }
    private double Y(double mm) { return oy + mm * sc; }

    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        if (spec == null) return;
        Graphics2D g = G.hq(g0);
        int w = getWidth(), h = getHeight();
        Units u = spec.units();
        double W = geo.leafW(), H = geo.leafH(), T = geo.blockT(), t = spec.turnInMm();

        g.setColor(G.INK);
        g.setFont(G.serif(18f, Font.BOLD));
        g.drawString("Cover & boards: " + spec.style().impl.name(), 24, 32);

        // layout size in mm
        double lw, lh;
        switch (spec.style()) {
            case KETTLE -> { lw = CoverCalc.caseClothW(spec, geo); lh = CoverCalc.caseClothH(spec, geo); }
            case COPTIC -> {
                lw = 2 * (CoverCalc.copticBoardW(spec, geo) + 2 * t) + 20;
                lh = CoverCalc.copticBoardH(spec, geo) + 2 * t;
            }
            case LONG_STITCH -> { lw = CoverCalc.limpW(geo); lh = H; }
            case PAMPHLET -> { lw = 2 * W + Math.max(T, 0.5); lh = H; }
            default -> { lw = 2 * W + 20; lh = H; }
        }
        List<Piece> pieces = CoverCalc.pieces(spec, geo);
        double listH = 34 + pieces.size() * 20;
        sc = Math.min((w - 140) / lw, (h - 120 - listH) / lh);
        ox = (w - lw * sc) / 2;
        oy = 70;

        switch (spec.style()) {
            case KETTLE -> drawCase(g, u);
            case COPTIC -> drawCoptic(g, u);
            case LONG_STITCH -> drawLimp(g, u);
            case PAMPHLET -> drawPamphlet(g, u);
            default -> drawStab(g, u);
        }

        // cutting list
        double ly = oy + lh * sc + 58;
        g.setColor(G.INK);
        g.setFont(G.sans(13f, Font.BOLD));
        g.drawString("Cutting list", 40, (float) ly);
        g.setFont(G.sans(12.5f, Font.PLAIN));
        for (Piece p : pieces) {
            ly += 20;
            g.setColor(G.INK);
            String s = "•  " + p.name() + ":  " + u.size(p.w(), p.h()) + (p.qty() > 1 ? "  × " + p.qty() : "");
            g.drawString(s, 40, (float) ly);
            g.setColor(new Color(0x6B6255));
            g.drawString(p.note(), 40 + g.getFontMetrics().stringWidth(s) + 16, (float) ly);
        }
        g.dispose();
    }

    private void paper(Graphics2D g, Rectangle2D rr, Color c) {
        g.setColor(new Color(0, 0, 0, 25));
        g.fill(new Rectangle2D.Double(rr.getX() + 4, rr.getY() + 5, rr.getWidth(), rr.getHeight()));
        g.setColor(c);
        g.fill(rr);
        g.setColor(G.alpha(G.INK, 120));
        g.setStroke(new BasicStroke(1f));
        g.draw(rr);
    }

    private void boardRect(Graphics2D g, Rectangle2D rr, String label, String dims) {
        g.setColor(G.BOARD);
        g.fill(rr);
        g.setColor(G.mix(G.BOARD, Color.BLACK, 0.3));
        g.setStroke(new BasicStroke(1f));
        g.draw(rr);
        g.setColor(Color.WHITE);
        g.setFont(G.sans(12f, Font.BOLD));
        G.centre(g, label, rr.getCenterX(), rr.getCenterY() - 4);
        g.setFont(G.sans(11f, Font.PLAIN));
        G.centre(g, dims, rr.getCenterX(), rr.getCenterY() + 12);
    }

    /** Mitred corner cut, `d` mm beyond the board corner (cx, cy), turn-in t, outward direction sx, sy. */
    private void mitre(Graphics2D g, double cx, double cy, double sx, double sy, double t, double d) {
        double k = t - d * Math.sqrt(2);
        if (k <= 0) return;
        Path2D tri = new Path2D.Double();
        tri.moveTo(X(cx + sx * t), Y(cy - sy * k));
        tri.lineTo(X(cx - sx * k), Y(cy + sy * t));
        tri.lineTo(X(cx + sx * t), Y(cy + sy * t));
        tri.closePath();
        g.setColor(G.alpha(G.CUT, 40));
        g.fill(tri);
        g.setColor(G.CUT);
        g.setStroke(G.dashed(1.3f));
        g.draw(new Line2D.Double(X(cx + sx * t), Y(cy - sy * k), X(cx - sx * k), Y(cy + sy * t)));
    }

    private void drawCase(Graphics2D g, Units u) {
        double t = spec.turnInMm(), bw = CoverCalc.caseBoardW(spec, geo), bh = CoverCalc.caseBoardH(spec, geo);
        double gap = CoverCalc.caseGap(spec), spw = CoverCalc.caseSpineW(spec, geo);
        double cw = CoverCalc.caseClothW(spec, geo), ch = CoverCalc.caseClothH(spec, geo);
        paper(g, r(0, 0, cw, ch), G.mix(spec.coverColor(), Color.WHITE, 0.45));
        double xFront = t, xSpine = t + bw + gap, xBack = xSpine + spw + gap;
        boardRect(g, r(xFront, t, bw, bh), "Front board", u.size(bw, bh));
        Rectangle2D sp = r(xSpine, t, spw, bh);
        g.setColor(new Color(0xD9CDB5));
        g.fill(sp);
        g.setColor(G.alpha(G.INK, 120));
        g.draw(sp);
        boardRect(g, r(xBack, t, bw, bh), "Back board", u.size(bw, bh));
        double d = 1.5 * spec.boardMm();
        mitre(g, xFront, t, -1, -1, t, d);
        mitre(g, xFront, t + bh, -1, 1, t, d);
        mitre(g, xBack + bw, t, 1, -1, t, d);
        mitre(g, xBack + bw, t + bh, 1, 1, t, d);
        G.dimH(g, X(0), X(cw), Y(ch) + 22, "cloth " + u.len(cw));
        G.dimV(g, X(0) - 22, Y(0), Y(ch), "cloth " + u.len(ch));
        G.dimH(g, X(xSpine), X(xSpine + spw), Y(0) - 14, "spine " + u.len(spw));
        G.dimV(g, X(cw) + 22, Y(0), Y(t), u.len(t));
        g.setFont(G.sans(11f, Font.ITALIC));
        g.setColor(G.INK);
        G.centre(g, "inside view, cloth face down · joint gaps " + u.len(gap) + " · dashed red = mitre cuts",
                X(cw / 2), Y(ch) + 44);
    }

    private void drawCoptic(Graphics2D g, Units u) {
        double t = spec.turnInMm(), bw = CoverCalc.copticBoardW(spec, geo), bh = CoverCalc.copticBoardH(spec, geo);
        double pw = bw + 2 * t, ph = bh + 2 * t;
        double d = 1.5 * spec.boardMm();
        for (int i = 0; i < 2; i++) {
            double x = i * (pw + 20);
            paper(g, r(x, 0, pw, ph), G.mix(spec.coverColor(), Color.WHITE, 0.45));
            boardRect(g, r(x + t, t, bw, bh), i == 0 ? "Front board" : "Back board", u.size(bw, bh));
            mitre(g, x + t, t, -1, -1, t, d);
            mitre(g, x + t, t + bh, -1, 1, t, d);
            mitre(g, x + t + bw, t, 1, -1, t, d);
            mitre(g, x + t + bw, t + bh, 1, 1, t, d);
            // holes: spine edge is on the right of the front (inside view) and left of the back
            double hx = i == 0 ? x + t + bw - spec.insetMm() : x + t + spec.insetMm();
            g.setColor(Color.WHITE);
            for (double yy : geo.stations()) {
                double py = Y(t + spec.squareMm() + yy);
                g.fill(new Ellipse2D.Double(X(hx) - 3, py - 3, 6, 6));
            }
        }
        G.dimH(g, X(0), X(pw), Y(ph) + 22, "covering paper " + u.len(pw));
        G.dimV(g, X(0) - 22, Y(0), Y(ph), u.len(ph));
        g.setFont(G.sans(11f, Font.ITALIC));
        g.setColor(G.INK);
        G.centre(g, "inside view · white dots = holes, " + u.len(spec.insetMm()) + " from the spine edge · dashed red = mitre cuts",
                X(pw + 10), Y(ph) + 44);
    }

    private void drawLimp(Graphics2D g, Units u) {
        double W = geo.leafW(), H = geo.leafH(), T = geo.blockT() + 2, flap = CoverCalc.limpFlap(geo);
        double cw = CoverCalc.limpW(geo);
        paper(g, r(0, 0, cw, H), spec.coverColor());
        double[] folds = { flap, flap + W, flap + W + T, flap + 2 * W + T };
        g.setColor(G.textOn(spec.coverColor()));
        g.setStroke(G.dashed(1.2f));
        for (double f : folds) g.draw(new Line2D.Double(X(f), Y(0), X(f), Y(H)));
        int N = spec.signatures();
        for (int i = 0; i < N; i++) {
            double cx = flap + W + 1 + (i + 0.5) * (T - 2) / N;
            for (double yy : geo.stations()) g.fill(new Ellipse2D.Double(X(cx) - 1.5, Y(yy) - 1.5, 3, 3));
        }
        g.setFont(G.sans(12f, Font.BOLD));
        G.centre(g, "flap", X(flap / 2), Y(H / 2));
        G.centre(g, "back", X(flap + W / 2), Y(H / 2));
        G.centre(g, "front", X(flap + W + T + W / 2), Y(H / 2));
        G.centre(g, "flap", X(cw - flap / 2), Y(H / 2));
        G.dimH(g, X(0), X(cw), Y(H) + 22, "cover " + u.len(cw));
        G.dimH(g, X(flap + W), X(flap + W + T), Y(0) - 14, "spine " + u.len(T));
        G.dimV(g, X(0) - 22, Y(0), Y(H), u.len(H));
        g.setFont(G.sans(11f, Font.ITALIC));
        g.setColor(G.INK);
        G.centre(g, "outside view · dashed = folds · dots = sewing holes, one column per signature", X(cw / 2), Y(H) + 44);
    }

    private void drawPamphlet(Graphics2D g, Units u) {
        double W = geo.leafW(), H = geo.leafH(), T = Math.max(geo.blockT(), 0.5);
        double cw = 2 * W + T;
        paper(g, r(0, 0, cw, H), spec.coverColor());
        g.setColor(G.textOn(spec.coverColor()));
        g.setStroke(G.dashed(1.2f));
        g.draw(new Line2D.Double(X(W + T / 2), Y(0), X(W + T / 2), Y(H)));
        for (double yy : geo.stations()) g.fill(new Ellipse2D.Double(X(W + T / 2) - 3, Y(yy) - 3, 6, 6));
        g.setFont(G.sans(12f, Font.BOLD));
        G.centre(g, "back", X(W / 2), Y(H / 2));
        G.centre(g, "front", X(W + T + W / 2), Y(H / 2));
        G.dimH(g, X(0), X(cw), Y(H) + 22, "cover " + u.len(cw));
        G.dimV(g, X(0) - 22, Y(0), Y(H), u.len(H));
        g.setFont(G.sans(11f, Font.ITALIC));
        g.setColor(G.INK);
        G.centre(g, "outside view · fold on the dashed line · punch the holes with the signature", X(cw / 2), Y(H) + 44);
    }

    private void drawStab(Graphics2D g, Units u) {
        double W = geo.leafW(), H = geo.leafH();
        for (int i = 0; i < 2; i++) {
            double x = i * (W + 20);
            paper(g, r(x, 0, W, H), spec.coverColor());
            double hx = i == 0 ? x + spec.insetMm() : x + W - spec.insetMm();
            g.setColor(G.textOn(spec.coverColor()));
            for (double yy : geo.stations()) g.fill(new Ellipse2D.Double(X(hx) - 3, Y(yy) - 3, 6, 6));
            g.setFont(G.sans(12f, Font.BOLD));
            G.centre(g, i == 0 ? "front cover" : "back cover", X(x + W / 2), Y(H / 2));
        }
        G.dimH(g, X(0), X(W), Y(H) + 22, u.len(W));
        G.dimV(g, X(0) - 22, Y(0), Y(H), u.len(H));
        g.setFont(G.sans(11f, Font.ITALIC));
        g.setColor(G.INK);
        G.centre(g, "outside views · dots = holes, " + u.len(spec.insetMm()) + " from the spine edge", X(W + 10), Y(H) + 44);
    }
}

// ---------------------------------------------------------------- Materials

final class MaterialsPanel extends StudioPanel {
    private final JEditorPane pane = new JEditorPane("text/html", "");

    MaterialsPanel() {
        setLayout(new BorderLayout());
        pane.setEditable(false);
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.setFont(G.sans(13f, Font.PLAIN));
        pane.setBorder(new EmptyBorder(16, 24, 16, 24));
        add(new JScrollPane(pane), BorderLayout.CENTER);
    }

    @Override JComponent exportComponent() { return pane; }

    private static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    @Override void dataChanged() {
        Units u = spec.units();
        BindingStyle b = spec.style().impl;
        double path = Sew.pathLength(steps), cut = Sew.cutLength(steps);
        StringBuilder h = new StringBuilder("<html><body style='font-family:sans-serif'>");
        h.append("<h2>").append(esc(spec.title().isBlank() ? "Untitled" : spec.title())).append(": materials &amp; bench list</h2>");
        h.append("<p><i>").append(esc(b.name())).append(" · ").append(geo.pages()).append(" pages · leaf ")
                .append(u.size(geo.leafW(), geo.leafH())).append("</i></p>");

        h.append("<h3>Text block</h3><ul>");
        String sheetName = spec.paper() == PaperSize.CUSTOM ? "custom" : spec.paper().label;
        h.append("<li>").append(geo.totalSheets()).append(" sheets of ").append(esc(sheetName)).append(" paper (")
                .append(u.size(geo.sheetW(), geo.sheetH())).append("), ").append(spec.gsm()).append(" gsm</li>");
        if (b.usesSignatures())
            h.append("<li>Fold into ").append(spec.signatures()).append(spec.signatures() == 1 ? " signature" : " signatures")
                    .append(" of ").append(spec.sheetsPerSig()).append(" sheets (").append(spec.sheetsPerSig() * 4)
                    .append(" pages each)</li>");
        else
            h.append("<li>Fold each sheet in half; stack the ").append(spec.sheetsPerSig())
                    .append(" leaves with the folds at the fore-edge</li>");
        h.append("<li>Block thickness ≈ ").append(u.len(geo.blockT())).append(" (paper caliper ≈ ")
                .append(String.format(Locale.UK, "%.3f", geo.caliper())).append(" mm per sheet)</li>");
        h.append("<li>Grain: ").append(geo.grainOk()
                ? "runs parallel to the spine ✓"
                : "<b style='color:#B23121'>runs across the spine. Choose " + (geo.sheetH() >= geo.sheetW() ? "long" : "short")
                + "-grain paper for this sheet orientation.</b>").append("</li>");
        h.append("</ul>");

        h.append("<h3>Thread</h3><ul>");
        h.append("<li>").append(esc(spec.thread().label.replaceAll("\\s+", " "))).append("</li>");
        h.append("<li><b>Cut ≈ ").append(u.thread(cut)).append("</b> (sewing path ").append(u.thread(path))
                .append(", plus 10% slack and two 8 cm tails)</li>");
        String rot = b.ruleOfThumb(spec, geo);
        if (rot != null) h.append("<li>Traditional rule of thumb: ").append(esc(rot)).append("</li>");
        if (cut > 2000)
            h.append("<li>That's a long thread and it will tangle. Sew with about 1.5 m at a time and join new lengths "
                    + "with a weaver's knot inside a signature.</li>");
        h.append("<li>Draw unwaxed linen over beeswax two or three times before sewing.</li>");
        h.append("</ul>");

        h.append("<h3>Cover</h3><ul>");
        for (Piece p : CoverCalc.pieces(spec, geo))
            h.append("<li>").append(esc(p.name())).append(": ").append(u.size(p.w(), p.h()))
                    .append(p.qty() > 1 ? " × " + p.qty() : "").append(" <i>(").append(esc(p.note())).append(")</i></li>");
        h.append("</ul>");

        h.append("<h3>Tools</h3><ul>");
        h.append("<li>").append(esc(b.needle())).append("</li>");
        h.append("<li>Awl or piercing tool, ideally with a punching cradle</li>");
        h.append("<li>Bone folder, steel rule, pencil, cutting mat and a sharp knife</li>");
        h.append("<li>Bulldog clips to hold signatures while punching</li>");
        switch (spec.style()) {
            case KETTLE -> h.append("<li>PVA, glue brush, waste paper, and a press (or boards and heavy books)</li>");
            case COPTIC -> h.append("<li>Drill or Japanese screw punch for the boards; PVA for covering them</li>");
            case STAB -> h.append("<li>Clamp or bulldog clips and a drill or awl to pierce the whole block</li>");
            case LONG_STITCH -> h.append("<li>Ruler and awl to pierce the cover spine from the guide</li>");
            default -> { }
        }
        h.append("</ul>");

        h.append("<h3>Order of work</h3><ol>");
        h.append("<li>Print the text block if needed (see the Signatures tab for page order), then fold");
        h.append(b.usesSignatures() ? " and nest the signatures.</li>" : " the leaves.</li>");
        h.append("<li>Print the template at 100%, check the 50 mm bar, and punch.</li>");
        switch (spec.style()) {
            case KETTLE -> h.append("<li>Sew (Sewing tab), glue up the spine, add mull, then make the case and case in.</li>");
            case COPTIC -> h.append("<li>Cover and drill the boards, then sew boards and signatures together (Sewing tab).</li>");
            case LONG_STITCH -> h.append("<li>Fold and pierce the cover, then sew each signature through it (Sewing tab).</li>");
            case PAMPHLET -> h.append("<li>Fold the cover round the signature, punch through both, and sew (Sewing tab).</li>");
            case STAB -> h.append("<li>Clamp block and covers, drill the holes, and sew (Sewing tab).</li>");
        }
        h.append("</ol>");
        h.append("<p style='color:#6B6255'><i>Thread lengths and sizes are bench estimates. Check them against your own materials.</i></p>");
        h.append("</body></html>");
        pane.setText(h.toString());
        pane.setCaretPosition(0);
    }
}
