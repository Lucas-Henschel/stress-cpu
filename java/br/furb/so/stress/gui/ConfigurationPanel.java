package br.furb.so.stress.gui;

import br.furb.so.stress.gui.model.StressConfiguration;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Inputs for the mock simulation. Values remain in place between runs. */
public final class ConfigurationPanel extends JPanel {
    private final List<JCheckBox> cores = new ArrayList<>();
    private final JSlider load = new JSlider(1, 100, 50);
    private final JLabel loadValue = new JLabel();
    private final JTextField duration = new JTextField();
    private final JLabel selectionSummary = new JLabel();
    private final JLabel validation = new JLabel(" ");
    private final JButton selectAll = new JButton("Selecionar todos");
    private final JButton clear = new JButton("Limpar");
    private final JButton start = new JButton("Iniciar teste");
    private final JButton stop = new JButton("Parar teste");
    private boolean running;

    public ConfigurationPanel(int coreCount, Runnable onStart, Runnable onStop) {
        if (coreCount < 1) {
            throw new IllegalArgumentException("At least one logical processor is required");
        }
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setPreferredSize(new Dimension(340, 560));
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(heading("Configuração", 20));
        content.add(Box.createVerticalStrut(20));
        content.add(heading("Processadores lógicos", 14));
        content.add(Box.createVerticalStrut(8));

        JPanel coreGrid = new JPanel(new GridLayout(0, 2, 8, 6));
        for (int index = 0; index < coreCount; index++) {
            JCheckBox core = new JCheckBox("Núcleo " + index, true);
            core.setName("core-" + index);
            core.addActionListener(event -> refreshValidation());
            cores.add(core);
            coreGrid.add(core);
        }
        JPanel coreWrapper = new JPanel(new BorderLayout());
        coreWrapper.add(coreGrid, BorderLayout.NORTH);
        JScrollPane coreScroll = new JScrollPane(coreWrapper);
        coreScroll.setPreferredSize(new Dimension(290, 140));
        coreScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        coreScroll.getVerticalScrollBar().setUnitIncrement(24);
        coreScroll.setAlignmentX(LEFT_ALIGNMENT);
        content.add(coreScroll);
        JPanel selectionActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 8));
        selectionActions.add(selectAll);
        selectionActions.add(Box.createHorizontalStrut(8));
        selectionActions.add(clear);
        selectionActions.setAlignmentX(LEFT_ALIGNMENT);
        selectionActions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        selectAll.addActionListener(event -> selectCores(true));
        clear.addActionListener(event -> selectCores(false));
        content.add(selectionActions);
        content.add(selectionSummary);
        content.add(Box.createVerticalStrut(24));
        content.add(heading("Carga por núcleo", 14));
        load.setName("load");
        load.setMajorTickSpacing(99);
        load.setPaintLabels(true);
        load.setAlignmentX(LEFT_ALIGNMENT);
        load.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        load.addChangeListener(event -> loadValue.setText(load.getValue() + "%"));
        loadValue.setText(load.getValue() + "%");
        content.add(loadValue);
        content.add(load);
        content.add(Box.createVerticalStrut(16));
        JLabel durationLabel = heading("Duração em segundos (opcional)", 14);
        durationLabel.setLabelFor(duration);
        content.add(durationLabel);
        content.add(Box.createVerticalStrut(8));
        duration.setName("duration");
        duration.putClientProperty("JTextField.placeholderText", "Vazio para executar até parar");
        duration.setAlignmentX(LEFT_ALIGNMENT);
        duration.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        duration.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { refreshValidation(); }
            public void removeUpdate(DocumentEvent event) { refreshValidation(); }
            public void changedUpdate(DocumentEvent event) { refreshValidation(); }
        });
        content.add(duration);
        content.add(Box.createVerticalStrut(8));
        validation.setFont(validation.getFont().deriveFont(12f));
        content.add(validation);
        content.add(Box.createVerticalGlue());
        content.add(Box.createVerticalStrut(20));
        JPanel buttons = new JPanel(new GridLayout(1, 2, 8, 0));
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        start.setName("start");
        stop.setName("stop");
        start.putClientProperty("JButton.buttonType", "roundRect");
        start.addActionListener(event -> onStart.run());
        stop.addActionListener(event -> onStop.run());
        buttons.add(start);
        buttons.add(stop);
        content.add(buttons);
        add(content);
        refreshValidation();
    }

    public StressConfiguration readConfiguration() {
        var selected = new LinkedHashSet<Integer>();
        for (int index = 0; index < cores.size(); index++) {
            if (cores.get(index).isSelected()) {
                selected.add(index);
            }
        }
        return new StressConfiguration(selected, load.getValue(), readDuration());
    }

    public void setRunning(boolean running) {
        this.running = running;
        cores.forEach(core -> core.setEnabled(!running));
        load.setEnabled(!running);
        duration.setEnabled(!running);
        selectAll.setEnabled(!running);
        clear.setEnabled(!running);
        refreshValidation();
    }

    private OptionalInt readDuration() {
        String text = duration.getText().trim();
        if (text.isEmpty()) {
            return OptionalInt.empty();
        }
        if (!text.matches("[0-9]+")) {
            throw new IllegalArgumentException("Use segundos inteiros positivos");
        }
        try {
            int seconds = Integer.parseInt(text);
            if (seconds > 0) {
                return OptionalInt.of(seconds);
            }
        } catch (NumberFormatException invalid) {
            // The same input hint also covers values above the supported range.
        }
        throw new IllegalArgumentException("Use de 1 a 2147483647 segundos");
    }

    private void refreshValidation() {
        long count = cores.stream().filter(JCheckBox::isSelected).count();
        selectionSummary.setText(count + " de " + cores.size() + " núcleos selecionados");
        boolean valid = count > 0;
        String message = valid ? " " : "Selecione ao menos um núcleo";
        try {
            readDuration();
        } catch (IllegalArgumentException invalid) {
            valid = false;
            message = invalid.getMessage();
        }
        validation.setText(message);
        start.setEnabled(!running && valid);
        stop.setEnabled(running);
    }

    private void selectCores(boolean selected) {
        cores.forEach(core -> core.setSelected(selected));
        refreshValidation();
    }

    private static JLabel heading(String text, int size) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD, size));
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }
}
