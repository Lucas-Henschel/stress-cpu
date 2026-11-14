package br.furb.so.stress.gui.model;

import java.util.List;
import java.util.Set;

public final class CoreSelectionTest {
    public static void main(String[] args) {
        todosKeepsAllCoresSorted();
        paresKeepsEvenIndexes();
        imparesKeepsOddIndexes();
        circularKeepsAllCoresSorted();
        rejectsFilterWithoutResult();
        rejectsNulls();
        labelsAreFriendly();
        System.out.println("CoreSelectionTest passed");
    }

    private static void todosKeepsAllCoresSorted() {
        assert CoreSelection.resolve(Set.of(3, 0, 2, 1), StressMode.TODOS).equals(List.of(0, 1, 2, 3));
    }

    private static void paresKeepsEvenIndexes() {
        assert CoreSelection.resolve(Set.of(5, 4, 0, 1, 2), StressMode.PARES).equals(List.of(0, 2, 4));
    }

    private static void imparesKeepsOddIndexes() {
        assert CoreSelection.resolve(Set.of(5, 4, 0, 1, 3), StressMode.IMPARES).equals(List.of(1, 3, 5));
    }

    private static void circularKeepsAllCoresSorted() {
        assert CoreSelection.resolve(Set.of(7, 2, 5), StressMode.CIRCULAR).equals(List.of(2, 5, 7));
    }

    private static void rejectsFilterWithoutResult() {
        rejects(() -> CoreSelection.resolve(Set.of(1, 3), StressMode.PARES));
        rejects(() -> CoreSelection.resolve(Set.of(0, 2), StressMode.IMPARES));
        rejects(() -> CoreSelection.resolve(Set.of(), StressMode.TODOS));
    }

    private static void rejectsNulls() {
        try {
            CoreSelection.resolve(null, StressMode.TODOS);
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException expected) {
            // ok
        }
        try {
            CoreSelection.resolve(Set.of(0), null);
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException expected) {
            // ok
        }
    }

    private static void labelsAreFriendly() {
        assert StressMode.TODOS.toString().equals("Todos");
        assert StressMode.PARES.toString().equals("Pares");
        assert StressMode.IMPARES.toString().equals("Ímpares");
        assert StressMode.CIRCULAR.toString().equals("Circular");
    }

    private static void rejects(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }
}
