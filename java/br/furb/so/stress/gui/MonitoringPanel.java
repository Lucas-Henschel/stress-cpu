package br.furb.so.stress.gui;

import br.furb.so.stress.gui.model.CoreMetric;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;

/** All values displayed here are simulated, including unselected cores at zero. */
public final class MonitoringPanel extends JPanel {
    private final JLabel state = new JLabel("Estado: ocioso");
    private final JLabel elapsed = new JLabel("Tempo decorrido: 00:00:00");
    private final JProgressBar total = new JProgressBar(0, 100);
    private final List<JProgressBar> coreBars = new ArrayList<>();

    public MonitoringPanel(int coreCount) {
        if (coreCount < 1) {
            throw new IllegalArgumentException("At least one logical processor is required");
        }
        setLayout(new BorderLayout(0, 20));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        JPanel summary = new JPanel();
        summary.setLayout(new BoxLayout(summary, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Monitoramento");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20));
        summary.add(title);
        summary.add(Box.createVerticalStrut(8));
        JLabel simulation = new JLabel("SIMULAÇÃO • Dados fictícios, sem carga real na CPU");
        simulation.setFont(simulation.getFont().deriveFont(Font.BOLD, 12));
        summary.add(simulation);
        summary.add(Box.createVerticalStrut(20));
        state.setName("state");
        elapsed.setName("elapsed");
        summary.add(state);
        summary.add(Box.createVerticalStrut(6));
        summary.add(elapsed);
        summary.add(Box.createVerticalStrut(16));
        summary.add(new JLabel("Uso total simulado"));
        summary.add(Box.createVerticalStrut(8));
        total.setName("usage-total");
        total.setStringPainted(true);
        total.setString("0,0%");
        total.setAlignmentX(LEFT_ALIGNMENT);
        total.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        total.setToolTipText("Média de todos os processadores lógicos; não selecionados em zero");
        summary.add(total);
        add(summary, BorderLayout.NORTH);

        JPanel cards = new JPanel(new GridLayout(0, 3, 12, 12));
        for (int index = 0; index < coreCount; index++) {
            JPanel card = new JPanel(new BorderLayout(0, 12));
            card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Núcleo " + index),
                BorderFactory.createEmptyBorder(8, 10, 12, 10)));
            card.setPreferredSize(new Dimension(160, 94));
            card.add(new JLabel("Uso simulado"), BorderLayout.NORTH);
            JProgressBar progress = new JProgressBar(0, 100);
            progress.setName("usage-core-" + index);
            progress.setStringPainted(true);
            progress.setString("0,0%");
            coreBars.add(progress);
            card.add(progress, BorderLayout.CENTER);
            cards.add(card);
        }
        JPanel gridWrapper = new JPanel(new BorderLayout());
        gridWrapper.add(cards, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(gridWrapper);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        add(scroll, BorderLayout.CENTER);
    }

    public void setRunning(boolean running) {
        state.setText(running ? "Estado: em execução (simulação)" : "Estado: ocioso");
    }

    public void updateMetrics(List<CoreMetric> metrics, long elapsedMillis) {
        coreBars.forEach(progress -> updateProgress(progress, 0));
        double sum = 0;
        for (CoreMetric metric : metrics) {
            updateProgress(coreBars.get(metric.coreIndex()), metric.utilizationPercent());
            sum += metric.utilizationPercent();
        }
        updateProgress(total, sum / coreBars.size());
        long seconds = Math.max(0, elapsedMillis / 1000);
        elapsed.setText(String.format(Locale.ROOT, "Tempo decorrido: %02d:%02d:%02d",
            seconds / 3600, (seconds / 60) % 60, seconds % 60));
    }

    private static void updateProgress(JProgressBar progress, double value) {
        progress.setValue((int) Math.round(value));
        progress.setString(String.format(Locale.forLanguageTag("pt-BR"), "%.1f%%", value));
    }
}
