package br.furb.so.stress.gui;

import br.furb.so.stress.gui.mock.MockStressDataSource;
import br.furb.so.stress.gui.model.StressConfiguration;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;

/** Single-window mock dashboard; its simulation lifecycle belongs to the EDT. */
public final class StressDashboardFrame extends JFrame {
    private final ConfigurationPanel configuration;
    private final MonitoringPanel monitoring;
    private final Timer timer;
    private MockStressDataSource source;
    private StressConfiguration activeConfiguration;
    private long startedNanos;

    public StressDashboardFrame() {
        super("CPU Stress — Simulação");
        int coreCount = Runtime.getRuntime().availableProcessors();
        configuration = new ConfigurationPanel(coreCount, this::startSimulation, this::stopSimulation);
        monitoring = new MonitoringPanel(coreCount);
        timer = new Timer(500, event -> updateSimulation());
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
                stopSimulation();
            }

            @Override
            public void windowClosed(WindowEvent event) {
                stopSimulation();
            }
        });
    }

    private void startSimulation() {
        if (timer.isRunning()) {
            return;
        }
        activeConfiguration = configuration.readConfiguration();
        source = new MockStressDataSource();
        startedNanos = System.nanoTime();
        configuration.setRunning(true);
        monitoring.setRunning(true);
        updateSimulation();
        timer.start();
    }

    private void updateSimulation() {
        long elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000;
        monitoring.updateMetrics(source.next(activeConfiguration), elapsedMillis);
        if (activeConfiguration.durationSeconds().isPresent()
            && elapsedMillis >= activeConfiguration.durationSeconds().getAsInt() * 1_000L) {
            stopSimulation();
        }
    }

    private void stopSimulation() {
        timer.stop();
        configuration.setRunning(false);
        monitoring.setRunning(false);
    }

    @Override
    public void dispose() {
        stopSimulation();
        super.dispose();
    }
}
