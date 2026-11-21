package br.furb.so.stress.gui.view;

import br.furb.so.stress.metrics.StressStats;
import java.awt.GraphicsEnvironment;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Integration test with the real Swing timer; requires a graphical session. */
public final class DashboardFrameTest {
    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("DashboardFrameTest skipped: no graphical session");
            System.exit(77);
        }
        var reference = new AtomicReference<StressDashboardFrame>();
        var lastSample = new AtomicReference<String>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                var frame = new StressDashboardFrame();
                reference.set(frame);
                assert find(frame, "execution-error", JLabel.class).getText().isBlank();
                find(frame, "start", JButton.class).doClick();
                lastSample.set(find(frame, "usage-core-0", JProgressBar.class).getString());
            });
            SwingUtilities.invokeAndWait(DashboardFrameTest::circularMetricsFollowTheActiveCore);
            Thread.sleep(3_000);
            SwingUtilities.invokeAndWait(() -> {
                var frame = reference.get();
                assert !find(frame, "duration", JTextField.class).isEnabled();
                assert !lastSample.get().equals(find(frame, "usage-core-0", JProgressBar.class).getString())
                    : "The running timer must deliver new samples";
                find(frame, "stop", JButton.class).doClick();
                lastSample.set(find(frame, "usage-core-0", JProgressBar.class).getString());
            });
            Thread.sleep(750);
            SwingUtilities.invokeAndWait(() -> {
                var frame = reference.get();
                assert find(frame, "start", JButton.class).isEnabled();
                assert find(frame, "duration", JTextField.class).getText().isEmpty();
                assert lastSample.get().equals(find(frame, "usage-core-0", JProgressBar.class).getString())
                    : "Manual stop must stop sampling";
                find(frame, "duration", JTextField.class).setText("1");
                find(frame, "start", JButton.class).doClick();
            });
            Thread.sleep(1_500);
            SwingUtilities.invokeAndWait(() -> {
                var frame = reference.get();
                assert find(frame, "start", JButton.class).isEnabled() : "Duration must stop the simulation";
                assert !find(frame, "stop", JButton.class).isEnabled();
                assert find(frame, "duration", JTextField.class).getText().equals("1");
                assert find(frame, "state", JLabel.class).getText().contains("ocioso");
                find(frame, "duration", JTextField.class).setText("");
                find(frame, "start", JButton.class).doClick();
                frame.dispose();
                lastSample.set(find(frame, "usage-core-0", JProgressBar.class).getString());
            });
            Thread.sleep(750);
            SwingUtilities.invokeAndWait(() -> {
                assert lastSample.get().equals(find(reference.get(), "usage-core-0", JProgressBar.class).getString())
                    : "Closing the window must stop sampling";
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (reference.get() != null) {
                    reference.get().dispose();
                }
            });
        }
        System.out.println("DashboardFrameTest passed");
    }

    @SuppressWarnings("unchecked")
    private static void circularMetricsFollowTheActiveCore() {
        // A separate, never-started frame keeps the real run's callbacks out of this check.
        var frame = new StressDashboardFrame();
        try {
            checkCircularMapping(frame);
        } finally {
            frame.dispose();
        }
    }

    private static void checkCircularMapping(StressDashboardFrame frame) {
        frame.applyActiveCores(Set.of(0));
        frame.applyStats(new StressStats(1000, 1, new double[] {80}, new double[] {1}, 0.1));
        assert find(frame, "usage-core-0", JProgressBar.class).getValue() == 80;
        if (Runtime.getRuntime().availableProcessors() >= 2) {
            frame.applyActiveCores(Set.of(1));
            frame.applyStats(new StressStats(2000, 1, new double[] {60}, new double[] {1}, 0.1));
            assert find(frame, "usage-core-1", JProgressBar.class).getValue() == 60
                : "Metric must land on the active core card";
            assert find(frame, "usage-core-0", JProgressBar.class).getValue() == 0
                : "Previously active core must return to zero";
        }
    }

    private static <T extends java.awt.Component> T find(StressDashboardFrame frame, String name, Class<T> type) {
        return DashboardPanelTest.find(frame.getContentPane(), name, type);
    }
}
