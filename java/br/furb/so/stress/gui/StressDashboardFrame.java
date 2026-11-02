package br.furb.so.stress.gui;

import br.furb.so.stress.gui.model.RealStressMetrics;
import br.furb.so.stress.gui.model.StressConfiguration;
import br.furb.so.stress.metrics.StressStats;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Single-window dashboard for a real JNI-backed stress run. */
public final class StressDashboardFrame extends JFrame {
    private final ConfigurationPanel configuration;
    private final MonitoringPanel monitoring;
    private final StressExecutionController controller;
    private StressConfiguration activeConfiguration;
    private long startedNanos;

    public StressDashboardFrame() {
        super("CPU Stress");
        int coreCount = Runtime.getRuntime().availableProcessors();
        configuration = new ConfigurationPanel(coreCount, this::startStress, this::stopStress);
        monitoring = new MonitoringPanel(coreCount);
        controller = new StressExecutionController(this::onStats, this::onFailure, this::onStopped);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout());
        configuration.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, 1, javax.swing.UIManager.getColor("Separator.foreground")),
            configuration.getBorder()));
        content.add(configuration, BorderLayout.WEST);
        content.add(monitoring, BorderLayout.CENTER);
        setContentPane(content);
        setMinimumSize(new Dimension(900, 640));
        setSize(1100, 740);
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                stopStress();
            }

            @Override
            public void windowClosed(WindowEvent event) {
                stopStress();
            }
        });
    }

    private void startStress() {
        try {
            activeConfiguration = configuration.readConfiguration();
            configuration.setExecutionError(null);
            configuration.setStarting(true);
            controller.start(activeConfiguration).whenComplete((run, failure) -> SwingUtilities.invokeLater(() -> {
                if (failure != null) {
                    configuration.setStarting(false);
                    Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                    configuration.setExecutionError("Erro: " + cause.getMessage());
                    return;
                }
                configuration.setRunning(true);
                monitoring.setRunning(true, run.affinityFixed());
            }));
        } catch (RuntimeException invalid) {
            configuration.setExecutionError("Erro: " + invalid.getMessage());
        }
    }

    private void onStats(StressStats stats) {
        SwingUtilities.invokeLater(() -> {
            if (activeConfiguration != null) {
                monitoring.updateMetrics(RealStressMetrics.from(activeConfiguration, stats), stats.elapsedMillis());
            }
        });
    }

    private void stopStress() {
        controller.stop();
    }

    private void onStopped() {
        SwingUtilities.invokeLater(() -> {
            activeConfiguration = null;
            configuration.setRunning(false);
            monitoring.setRunning(false);
        });
    }

    private void onFailure(Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            configuration.setExecutionError("Erro: " + failure.getMessage());
            configuration.setStarting(false);
        });
    }

    @Override
    public void dispose() {
        controller.close();
        super.dispose();
    }
}
