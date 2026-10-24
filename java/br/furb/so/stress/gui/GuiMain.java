package br.furb.so.stress.gui;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

public final class GuiMain {
    private GuiMain() {
    }

    public static void main(String[] args) {
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("CPU Stress");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(new JLabel("CPU Stress", SwingConstants.CENTER));
            frame.setSize(640, 400);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
