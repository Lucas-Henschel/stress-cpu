package br.furb.so.stress.gui.model;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;

public final class StressScheduleTest {
    public static void main(String[] args) {
        staticModesHaveASingleStep();
        circularRotatesOneCoreAtATime();
        escalonadoAddsOneCorePerStepAndThenStops();
        escalonadoWithSingleCoreIsStatic();
        oscilanteAlternatesBetweenFullAndQuarterLoad();
        oscilanteWithMinimumLoadIsStatic();
        aleatorioNeverRepeatsTheCurrentCore();
        aleatorioWithSingleCoreIsStatic();
        System.out.println("StressScheduleTest passed");
    }

    private static void staticModesHaveASingleStep() {
        for (var mode : List.of(StressMode.TODOS, StressMode.PARES, StressMode.IMPARES)) {
            var schedule = schedule(Set.of(0, 1, 2, 3), 60, mode);
            assert !schedule.isDynamic() : mode + " must not change over time";
        }
        var pares = schedule(Set.of(3, 2, 1, 0), 60, StressMode.PARES);
        assert pares.current().equals(new StressSchedule.Step(List.of(0, 2), 60));
    }

    private static void circularRotatesOneCoreAtATime() {
        var schedule = schedule(Set.of(4, 0, 2), 50, StressMode.CIRCULAR);
        assert schedule.isDynamic();
        assert steps(schedule, 4).equals(List.of(
            new StressSchedule.Step(List.of(0), 50),
            new StressSchedule.Step(List.of(2), 50),
            new StressSchedule.Step(List.of(4), 50),
            new StressSchedule.Step(List.of(0), 50)));
    }

    private static void escalonadoAddsOneCorePerStepAndThenStops() {
        var schedule = schedule(Set.of(5, 1, 3), 70, StressMode.ESCALONADO);
        assert schedule.current().equals(new StressSchedule.Step(List.of(1), 70));
        assert schedule.isDynamic();
        assert schedule.advance().equals(new StressSchedule.Step(List.of(1, 3), 70));
        assert schedule.isDynamic();
        assert schedule.advance().equals(new StressSchedule.Step(List.of(1, 3, 5), 70));
        assert !schedule.isDynamic() : "The ramp ends once every core is stressed";
        assert schedule.advance().equals(new StressSchedule.Step(List.of(1, 3, 5), 70))
            : "Advancing past the end keeps the full set";
    }

    private static void escalonadoWithSingleCoreIsStatic() {
        assert !schedule(Set.of(2), 70, StressMode.ESCALONADO).isDynamic();
    }

    private static void oscilanteAlternatesBetweenFullAndQuarterLoad() {
        var schedule = schedule(Set.of(1, 0), 80, StressMode.OSCILANTE);
        assert schedule.isDynamic();
        var loads = steps(schedule, 10).stream().map(StressSchedule.Step::loadPercent).toList();
        assert loads.equals(List.of(80, 65, 50, 35, 20, 35, 50, 65, 80, 65)) : loads;

        var top = schedule(Set.of(0), 99, StressMode.OSCILANTE);
        var topLoads = steps(top, 9).stream().map(StressSchedule.Step::loadPercent).toList();
        assert topLoads.equals(List.of(99, 80, 62, 43, 24, 43, 62, 80, 99)) : topLoads;

        var low = schedule(Set.of(0), 3, StressMode.OSCILANTE);
        var lowLoads = steps(low, 5).stream().map(StressSchedule.Step::loadPercent).toList();
        assert lowLoads.equals(List.of(3, 2, 1, 2, 3)) : "Low phase never drops below 1%: " + lowLoads;
    }

    private static void oscilanteWithMinimumLoadIsStatic() {
        assert !schedule(Set.of(0, 1), 1, StressMode.OSCILANTE).isDynamic()
            : "At 1% both phases are equal";
    }

    private static void aleatorioNeverRepeatsTheCurrentCore() {
        var schedule = new StressSchedule(
            new StressConfiguration(Set.of(0, 1, 2, 3), 50, OptionalInt.empty(), StressMode.ALEATORIO), new Random(42));
        assert schedule.isDynamic();
        var seen = new java.util.HashSet<Integer>();
        var previous = schedule.current();
        assert previous.cores().size() == 1;
        for (int i = 0; i < 200; i++) {
            var next = schedule.advance();
            assert next.cores().size() == 1 : "One core at a time";
            assert !next.cores().equals(previous.cores()) : "Repeated core " + next.cores();
            assert next.loadPercent() == 50;
            seen.addAll(next.cores());
            previous = next;
        }
        assert seen.equals(Set.of(0, 1, 2, 3)) : "Every selected core must eventually be chosen: " + seen;
    }

    private static void aleatorioWithSingleCoreIsStatic() {
        var schedule = schedule(Set.of(7), 50, StressMode.ALEATORIO);
        assert !schedule.isDynamic();
        assert schedule.current().cores().equals(List.of(7));
    }

    private static StressSchedule schedule(Set<Integer> cores, int load, StressMode mode) {
        return new StressSchedule(new StressConfiguration(cores, load, OptionalInt.empty(), mode), new Random(1));
    }

    private static List<StressSchedule.Step> steps(StressSchedule schedule, int count) {
        var steps = new ArrayList<StressSchedule.Step>();
        steps.add(schedule.current());
        while (steps.size() < count) {
            steps.add(schedule.advance());
        }
        return steps;
    }
}
