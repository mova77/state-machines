/*
 * SPDX-FileCopyrightText: 2026 Marco Vanadia
 * SPDX-License-Identifier: MIT
 */
/**
 * Generic, framework-free state machines.
 *
 * <p><b>Value/enum machine</b> — for states that are fixed values:
 * {@link io.github.mova77.statemachines.StateMachine} (interface),
 * {@link io.github.mova77.statemachines.StateMachineBuilder} (fluent builder),
 * {@link io.github.mova77.statemachines.EnumStateMachine} (enum-specialised
 * wrapper with diagnostics), and supporting type
 * {@link io.github.mova77.statemachines.Transition}. Matched by value equality with
 * constant transition targets; mutable current state.</p>
 *
 * <p><b>Typed/record machine</b> — for states modelled as a {@code sealed} hierarchy of
 * payload-carrying {@code record} variants:
 * {@link io.github.mova77.statemachines.TypedStateMachine} (interface),
 * {@link io.github.mova77.statemachines.TypedStateMachineBuilder} (fluent builder), and
 * {@link io.github.mova77.statemachines.Reduction} (next state + output). Routed by runtime
 * variant {@link java.lang.Class}, with reducer transitions that compute a fresh next
 * state; immutable and thread-safe. Completeness over the sealed variants is checkable via
 * {@link io.github.mova77.statemachines.TypedStateMachine#uncoveredVariants()}.</p>
 *
 * <p>Both share {@link io.github.mova77.statemachines.IllegalTransitionException} and
 * {@link io.github.mova77.statemachines.GuardRejectedException}.</p>
 *
 * <p>Depends only on the Java standard library. The value/enum machine is not thread-safe;
 * the typed machine is immutable and thread-safe.</p>
 */
package io.github.mova77.statemachines;
