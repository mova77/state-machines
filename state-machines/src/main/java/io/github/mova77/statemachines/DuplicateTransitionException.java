/*
 * SPDX-FileCopyrightText: 2026 Marco Vanadia
 * SPDX-License-Identifier: MIT
 */
package io.github.mova77.statemachines;

import java.io.Serial;

/**
 * Thrown by {@link StateMachineBuilder#build} when two transitions are registered for the
 * same {@code (fromState, event)} pair. Each pair may have at most one transition.
 */
public final class DuplicateTransitionException extends RuntimeException {

    @Serial private static final long serialVersionUID = 1L;

    private final transient Object fromState;
    private final transient Object event;

    public <S, E> DuplicateTransitionException(S fromState, E event) {
        super(String.format(
                "Duplicate transition for state '%s' on event '%s'.", fromState, event));
        this.fromState = fromState;
        this.event = event;
    }

    /** The source state of the duplicated transition. */
    public Object fromState() {
        return fromState;
    }

    /** The event of the duplicated transition. */
    public Object event() {
        return event;
    }
}
