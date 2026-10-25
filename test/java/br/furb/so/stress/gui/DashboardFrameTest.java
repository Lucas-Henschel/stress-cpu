package br.furb.so.stress.gui;

import java.awt.GraphicsEnvironment;
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
                find(frame, "start", JButton.class).doClick();
                assert !find(frame, "duration", JTextField.class).isEnabled();
                lastSample.set(find(frame, "usage-core-0", JProgressBar.class).getString());
            });
            Thread.sleep(1_300);
            SwingUtilities.invokeAndWait(() -> {
                var frame = reference.get();
                assert !lastSample.get().equals(find(frame, "usage-core-0", JProgressBar.class).getString())
                    : "The running timer must deliver new samples";
                find(frame, "stop", JButton.class).doClick();
                assert find(frame, "start", JButton.class).isEnabled();
                assert find(frame, "duration", JTextField.class).getText().isEmpty();
                lastSample.set(find(frame, "usage-core-0", JProgressBar.class).getString());
            });
            Thread.sleep(750);
            SwingUtilities.invokeAndWait(() -> {
                var frame = reference.get();
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

    private static <T extends java.awt.Component> T find(StressDashboardFrame frame, String name, Class<T> type) {
        return DashboardPanelTest.find(frame.getContentPane(), name, type);
    }
}
