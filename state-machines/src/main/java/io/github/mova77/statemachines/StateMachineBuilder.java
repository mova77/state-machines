/*
 * SPDX-FileCopyrightText: 2026 Marco Vanadia
 * SPDX-License-Identifier: MIT
 */
package io.github.mova77.statemachines;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/**
 * Fluent builder for {@link StateMachine} instances.
 *
 * <p>Typical usage:</p>
 * <pre>{@code
 * StateMachine<TrafficLight, Signal> machine =
 *     StateMachineBuilder.<TrafficLight, Signal>create()
 *         .transition(RED,    Signal.GO,   GREEN)
 *         .transition(GREEN,  Signal.SLOW, AMBER)
 *         .transition(AMBER,  Signal.STOP, RED)
 *         .terminal(RED)
 *         .build(RED);
 * }</pre>
 *
 * <p>Each {@code (from, event)} pair may have at most one transition: registering a
 * second one for the same pair makes {@link #build} throw
 * {@link DuplicateTransitionException}, rather than letting the later transition silently
 * replace the earlier one.</p>
 *
 * <p>The builder is <em>not</em> reusable: call {@link #build} exactly once.
 * Subsequent calls to {@link #build} on the same instance are unsupported.</p>
 *
 * @param <S> state type
 * @param <E> event type
 */
public final class StateMachineBuilder<S, E> {

    private final List<Transition<S, E>> transitions = new ArrayList<>();
    private final Set<S> terminalStates = new HashSet<>();

    private StateMachineBuilder() {
    }

    /**
     * Entry point — returns a fresh builder with type parameters inferred at
     * the call site.
     */
    public static <S, E> StateMachineBuilder<S, E> create() {
        return new StateMachineBuilder<>();
    }

    /**
     * Adds a transition with no guard and no action.
     */
    public StateMachineBuilder<S, E> transition(S from, E event, S to) {
        transitions.add(new Transition<>(from, event, to));
        return this;
    }

    /**
     * Adds a transition with a guard. The guard is evaluated at trigger-time;
     * if it returns {@code false} a {@link GuardRejectedException} is thrown.
     */
    public StateMachineBuilder<S, E> transition(S from, E event, S to, BooleanSupplier guard) {
        transitions.add(new Transition<>(from, event, to, guard));
        return this;
    }

    /**
     * Adds a transition with both a guard and a post-transition callback.
     *
     * @param onTransit called with {@code (from, to)} after the state is updated;
     *                  MUST NOT throw
     */
    public StateMachineBuilder<S, E> transition(
            S from, E event, S to, BooleanSupplier guard, BiConsumer<S, S> onTransit) {
        transitions.add(new Transition<>(from, event, to, guard, onTransit));
        return this;
    }

    /**
     * Marks one or more states as terminal (accepting). Terminal states are
     * recognised by {@link StateMachine#isTerminal(Object)}.
     */
    @SafeVarargs
    public final StateMachineBuilder<S, E> terminal(S... states) {
        for (S s : states) {
            terminalStates.add(s);
        }
        return this;
    }

    /**
     * Builds and returns a new {@link StateMachine} starting in
     * {@code initialState}.
     *
     * <p>Each {@code (from, event)} pair may have at most one transition. The same event
     * from different states, and different events from the same state, are allowed.</p>
     *
     * @param initialState the state the machine occupies before any event is fired
     * @throws IllegalArgumentException     if no transitions have been registered
     * @throws DuplicateTransitionException if two transitions share the same
     *                                      {@code (from, event)} pair
     */
    public StateMachine<S, E> build(S initialState) {
        if (transitions.isEmpty()) {
            throw new IllegalArgumentException(
                    "StateMachine must have at least one transition.");
        }
        Map<S, Set<E>> eventsByState = new HashMap<>();
        for (Transition<S, E> t : transitions) {
            if (!eventsByState.computeIfAbsent(t.from(), k -> new HashSet<>()).add(t.event())) {
                throw new DuplicateTransitionException(t.from(), t.event());
            }
        }
        return new DefaultStateMachine<>(
                List.copyOf(transitions),
                Set.copyOf(terminalStates),
                initialState);
    }
}
