package br.furb.so.stress;

import java.util.List;

public final class LaunchModeTest {
    public static void main(String[] args) {
        opensTheGuiByDefault();
        selectsConsoleAndRemovesTheModeFlag();
        System.out.println("LaunchModeTest passed");
    }

    private static void opensTheGuiByDefault() {
        var mode = LaunchMode.parse(new String[] {"--load=50"});
        assert !mode.console();
        assert mode.applicationArgs().equals(List.of("--load=50"));
    }

    private static void selectsConsoleAndRemovesTheModeFlag() {
        var mode = LaunchMode.parse(new String[] {"--console", "--load=50"});
        assert mode.console();
        assert mode.applicationArgs().equals(List.of("--load=50"));
    }
}
