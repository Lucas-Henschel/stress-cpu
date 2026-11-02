package br.furb.so.stress;

import java.util.ArrayList;
import java.util.List;

/** Seleciona a interface grafica padrao ou o modo de console explicito. */
public record LaunchMode(boolean console, List<String> applicationArgs) {
    public static LaunchMode parse(String[] args) {
        boolean console = false;
        List<String> applicationArgs = new ArrayList<>();

        for (String arg : args) {
            if ("--console".equals(arg)) {
                console = true;
            } else {
                applicationArgs.add(arg);
            }
        }

        return new LaunchMode(console, List.copyOf(applicationArgs));
    }
}
