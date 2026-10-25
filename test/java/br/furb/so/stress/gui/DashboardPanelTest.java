package br.furb.so.stress.gui;

import br.furb.so.stress.gui.model.CoreMetric;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Exercises input validation and presentation on the EDT, without a display. */
public final class DashboardPanelTest {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            validatesConfigurationAndPreservesValuesAfterStop();
            updatesMetricsAndClearsCoresMissingFromTheNextSample();
            supportsManyLogicalProcessors();
        });
        System.out.println("DashboardPanelTest passed");
    }

    private static void validatesConfigurationAndPreservesValuesAfterStop() {
        var starts = new AtomicInteger();
        var stops = new AtomicInteger();
        var panel = new ConfigurationPanel(2, starts::incrementAndGet, stops::incrementAndGet);
        var first = find(panel, "core-0", JCheckBox.class);
        var second = find(panel, "core-1", JCheckBox.class);
        var start = find(panel, "start", JButton.class);
        var stop = find(panel, "stop", JButton.class);
        var load = find(panel, "load", JSlider.class);
        var duration = find(panel, "duration", JTextField.class);

        first.doClick();
        second.doClick();
        assert !start.isEnabled() : "An empty core selection must prevent starting";
        second.doClick();
        assert start.isEnabled();
        load.setValue(1);
        assert panel.readConfiguration().loadPercent() == 1;
        load.setValue(100);
        assert panel.readConfiguration().loadPercent() == 100;
        assert panel.readConfiguration().selectedCores().equals(Set.of(1));
        assert panel.readConfiguration().durationSeconds().isEmpty();

        for (String invalid : List.of("0", "-1", "text", "1.5", "2147483648")) {
            duration.setText(invalid);
            assert !start.isEnabled() : "Invalid duration accepted: " + invalid;
        }
        duration.setText("2");
        assert start.isEnabled();
        assert panel.readConfiguration().durationSeconds().getAsInt() == 2;
        start.doClick();
        assert starts.get() == 1;
        var before = panel.readConfiguration();
        panel.setRunning(true);
        assert !start.isEnabled() && stop.isEnabled();
        assert !first.isEnabled() && !second.isEnabled();
        assert !duration.isEnabled() && !load.isEnabled();
        stop.doClick();
        assert stops.get() == 1;
        panel.setRunning(false);
        assert start.isEnabled() && !stop.isEnabled();
        assert first.isEnabled() && second.isEnabled();
        assert duration.isEnabled() && load.isEnabled();
        assert panel.readConfiguration().equals(before) : "Stopping must preserve the inputs";
    }

    private static void updatesMetricsAndClearsCoresMissingFromTheNextSample() {
        var panel = new MonitoringPanel(4);
        panel.updateMetrics(List.of(new CoreMetric(0, 40), new CoreMetric(2, 80)), 65_000);
        assert find(panel, "usage-total", JProgressBar.class).getValue() == 30;
        assert find(panel, "usage-core-0", JProgressBar.class).getValue() == 40;
        assert find(panel, "usage-core-2", JProgressBar.class).getValue() == 80;
        assert find(panel, "elapsed", JLabel.class).getText().contains("00:01:05");
        panel.updateMetrics(List.of(new CoreMetric(1, 100)), 500);
        assert find(panel, "usage-core-0", JProgressBar.class).getValue() == 0;
        assert find(panel, "usage-core-2", JProgressBar.class).getValue() == 0;
        assert find(panel, "usage-core-1", JProgressBar.class).getValue() == 100;
        assert find(panel, "usage-total", JProgressBar.class).getValue() == 25;
    }

    private static void supportsManyLogicalProcessors() {
        var configuration = new ConfigurationPanel(128, () -> { }, () -> { });
        assert configuration.readConfiguration().selectedCores().size() == 128;
        assert find(configuration, "core-127", JCheckBox.class).isSelected();
        var monitoring = new MonitoringPanel(128);
        monitoring.updateMetrics(List.of(new CoreMetric(127, 75)), 0);
        assert find(monitoring, "usage-core-127", JProgressBar.class).getValue() == 75;
    }

    static <T extends Component> T find(Container root, String name, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName()) && type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof Container container) {
                T result = findOrNull(container, name, type);
                if (result != null) {
                    return result;
                }
            }
        }
        throw new AssertionError("Missing component: " + name);
    }

    private static <T extends Component> T findOrNull(Container root, String name, Class<T> type) {
        try {
            return find(root, name, type);
        } catch (AssertionError missing) {
            return null;
        }
    }
}
