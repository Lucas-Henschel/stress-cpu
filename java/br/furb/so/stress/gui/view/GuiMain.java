package br.furb.so.stress.gui.view;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.SwingUtilities;

public final class GuiMain {
    private GuiMain() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FlatLightLaf.setup();
            new StressDashboardFrame().setVisible(true);
        });
    }
}
