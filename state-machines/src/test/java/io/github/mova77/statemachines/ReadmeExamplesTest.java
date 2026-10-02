/*
 * SPDX-FileCopyrightText: 2026 Marco Vanadia
 * SPDX-License-Identifier: MIT
 */
package io.github.mova77.statemachines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Runs every example in README.md. Each README Java block appears verbatim below (up to
 * indentation), and {@link #readmeBlocksAppearVerbatim()} fails if the two drift apart.
 * Assertions follow each block to check the results its comments claim.
 */
@DisplayName("README examples")
class ReadmeExamplesTest {

    private static final Path README = Path.of("..", "README.md");
    private static final Path THIS_FILE = Path.of(
            "src", "test", "java", "io", "github", "mova77", "statemachines",
            "ReadmeExamplesTest.java");
    private static final int README_JAVA_BLOCKS = 12;

    @Test
    @DisplayName("every Java block in the README appears verbatim in this test")
    void readmeBlocksAppearVerbatim() throws IOException {
        List<String> source = normalise(Files.readAllLines(THIS_FILE));
        List<List<String>> blocks = javaBlocks(Files.readAllLines(README));

        assertThat(blocks).hasSize(README_JAVA_BLOCKS);
        for (List<String> block : blocks) {
            assertThat(Collections.indexOfSubList(source, normalise(block)))
                    .as("README block not found in this test:%n%s", String.join("\n", block))
                    .isNotNegative();
        }
    }

    private static List<List<String>> javaBlocks(List<String> markdown) {
        List<List<String>> blocks = new ArrayList<>();
        List<String> current = null;
        for (String line : markdown) {
            if (current == null && line.equals("```java")) {
                current = new ArrayList<>();
            } else if (current != null && line.equals("```")) {
                blocks.add(current);
                current = null;
            } else if (current != null) {
                current.add(line);
            }
        }
        return blocks;
    }

    /** Strips indentation and trailing spaces, and drops blank lines. */
    private static List<String> normalise(List<String> lines) {
        return lines.stream().map(String::strip).filter(line -> !line.isEmpty()).toList();
    }

    @Nested
    @DisplayName("turnstile")
    class TurnstileExample {

        enum Turnstile { LOCKED, UNLOCKED }
        enum Input { COIN, PUSH }

        @Test
        void runs() {
            EnumStateMachine<Turnstile, Input> turnstile =
                    EnumStateMachine.<Turnstile, Input>builder(Turnstile.class)
                            .transition(Turnstile.LOCKED,   Input.COIN, Turnstile.UNLOCKED)
                            .transition(Turnstile.LOCKED,   Input.PUSH, Turnstile.LOCKED)
                            .transition(Turnstile.UNLOCKED, Input.COIN, Turnstile.UNLOCKED)
                            .transition(Turnstile.UNLOCKED, Input.PUSH, Turnstile.LOCKED)
                            .buildEnum(Turnstile.LOCKED);

            turnstile.trigger(Input.COIN);   // UNLOCKED
            turnstile.trigger(Input.PUSH);   // LOCKED
            turnstile.coverage();            // 1.0: every state takes part in a transition

            assertThat(turnstile.currentState()).isEqualTo(Turnstile.LOCKED);
            assertThat(turnstile.coverage()).isEqualTo(1.0);
            assertThat(turnstile.trigger(Input.COIN)).isEqualTo(Turnstile.UNLOCKED);
        }
    }

    @Nested
    @DisplayName("order lifecycle")
    class OrderExample {

        enum Order { PLACED, PAID, SHIPPED, DELIVERED, CANCELLED }
        enum Event { PAY, SHIP, DELIVER, CANCEL }

        @Test
        void runs() {
            AtomicBoolean paymentVerified = new AtomicBoolean(true);

            EnumStateMachine<Order, Event> order = EnumStateMachine.<Order, Event>builder(Order.class)
                    .transition(Order.PLACED,  Event.PAY,     Order.PAID, paymentVerified::get)
                    .transition(Order.PLACED,  Event.CANCEL,  Order.CANCELLED)
                    .transition(Order.PAID,    Event.SHIP,    Order.SHIPPED)
                    .transition(Order.SHIPPED, Event.DELIVER, Order.DELIVERED)
                    .terminal(Order.DELIVERED, Order.CANCELLED)
                    .buildEnum(Order.PLACED);

            boolean canPay = order.canTrigger(Event.PAY);   // true: the transition exists and its guard passes
            order.trigger(Event.PAY);          // PAID (GuardRejectedException if the guard failed)
            try {
                order.trigger(Event.DELIVER);  // there is no DELIVER transition from PAID
            } catch (IllegalTransitionException e) {
                e.availableEvents();           // [SHIP]
            }
            order.deadEndStates();             // []: every non-terminal state has a way out

            assertThat(canPay).isTrue();
            assertThat(order.currentState()).isEqualTo(Order.PAID);
            assertThat(order.deadEndStates()).isEmpty();
            assertThatThrownBy(() -> order.trigger(Event.DELIVER))
                    .isInstanceOfSatisfying(IllegalTransitionException.class,
                            e -> assertThat(e.availableEvents()).isEqualTo(java.util.Set.of(Event.SHIP)));
        }
    }

    @Nested
    @DisplayName("kanban work item")
    class KanbanExample {

        enum Column { TODO, IN_PROGRESS, IN_REVIEW, DONE }
        enum Move { START, SUBMIT, APPROVE, REJECT }

        @Test
        void runs() {
            int wipLimit = 2;
            AtomicInteger inProgress = new AtomicInteger(2);   // items already in progress on the board
            List<String> log = new ArrayList<>();
            BiConsumer<Column, Column> logMove = (from, to) -> log.add(from + " -> " + to);

            StateMachine<Column, Move> item = StateMachineBuilder.<Column, Move>create()
                    .transition(Column.TODO,        Move.START,   Column.IN_PROGRESS,
                            () -> inProgress.get() < wipLimit, logMove)
                    .transition(Column.IN_PROGRESS, Move.SUBMIT,  Column.IN_REVIEW,   () -> true, logMove)
                    .transition(Column.IN_REVIEW,   Move.REJECT,  Column.IN_PROGRESS, () -> true, logMove)
                    .transition(Column.IN_REVIEW,   Move.APPROVE, Column.DONE,        () -> true, logMove)
                    .terminal(Column.DONE)
                    .build(Column.TODO);

            boolean canStart = item.canTrigger(Move.START);   // false: the WIP limit is reached
            inProgress.decrementAndGet();  // another item leaves the column
            item.trigger(Move.START);      // IN_PROGRESS
            item.trigger(Move.SUBMIT);     // IN_REVIEW
            item.trigger(Move.APPROVE);    // DONE
            item.isTerminal();             // true
            // log: [TODO -> IN_PROGRESS, IN_PROGRESS -> IN_REVIEW, IN_REVIEW -> DONE]

            assertThat(canStart).isFalse();
            assertThat(item.currentState()).isEqualTo(Column.DONE);
            assertThat(item.isTerminal()).isTrue();
            assertThat(log).containsExactly(
                    "TODO -> IN_PROGRESS", "IN_PROGRESS -> IN_REVIEW", "IN_REVIEW -> DONE");
        }
    }

    @Nested
    @DisplayName("login flow")
    class LoginExample {

        sealed interface Login permits Identify, VerifyPassword, Authenticated, Denied {}
        record Identify() implements Login {}
        record VerifyPassword(String user, int attempts) implements Login {}
        record Authenticated(String user) implements Login {}
        record Denied(String reason) implements Login {}

        enum LoginEvent { SUBMIT, GOOD_PASSWORD, BAD_PASSWORD }

        @Test
        void runs() {
            TypedStateMachine<Login, LoginEvent, String> login =
                    TypedStateMachineBuilder.<Login, LoginEvent, String>create(Login.class)
                            .on(Identify.class, (s, e) -> Reduction.to(new VerifyPassword("alice", 0)))
                            .on(VerifyPassword.class,
                                    (VerifyPassword s, LoginEvent e) -> s.attempts() < 3,   // guard
                                    (VerifyPassword s, LoginEvent e) -> e == LoginEvent.GOOD_PASSWORD
                                            ? Reduction.of(new Authenticated(s.user()), "welcome")
                                            : Reduction.of(new VerifyPassword(s.user(), s.attempts() + 1), "retry"))
                            .terminal(Authenticated.class, Denied.class)
                            .buildStrict();   // fails if any permitted variant is neither handled nor terminal

            Reduction<Login, String> r = login.step(new VerifyPassword("alice", 0), LoginEvent.GOOD_PASSWORD);
            r.nextState();   // Authenticated[user=alice]
            r.output();      // "welcome"

            assertThat(r.nextState()).isEqualTo(new Authenticated("alice"));
            assertThat(r.output()).isEqualTo("welcome");
        }
    }

    @Nested
    @DisplayName("vending machine (Mealy)")
    class VendingExample {

        sealed interface Vending permits Idle, HasCredit {}
        record Idle() implements Vending {}
        record HasCredit(int cents) implements Vending {}

        sealed interface Action permits Coin, Select, Refund {}
        record Coin(int cents) implements Action {}
        record Select(String item, int price) implements Action {}
        record Refund() implements Action {}

        sealed interface Output permits Vend, Change {}
        record Vend(String item, int change) implements Output {}
        record Change(int cents) implements Output {}

        @Test
        void runs() {
            TypedStateMachine<Vending, Action, Output> vending =
                    TypedStateMachineBuilder.<Vending, Action, Output>create(Vending.class)
                            .on(Idle.class, (s, e) -> switch (e) {
                                case Coin coin -> Reduction.to(new HasCredit(coin.cents()));
                                case Select select -> Reduction.to(s);   // no credit, nothing happens
                                case Refund refund -> Reduction.to(s);
                            })
                            .on(HasCredit.class, (s, e) -> switch (e) {
                                case Coin coin -> Reduction.to(new HasCredit(s.cents() + coin.cents()));
                                case Select select when select.price() <= s.cents() -> Reduction.of(
                                        new Idle(), new Vend(select.item(), s.cents() - select.price()));
                                case Select select -> Reduction.to(s);   // not enough credit yet
                                case Refund refund -> Reduction.of(new Idle(), new Change(s.cents()));
                            })
                            .buildStrict();

            Vending state = new Idle();
            state = vending.step(state, new Coin(100)).nextState();   // HasCredit[cents=100]
            state = vending.step(state, new Coin(50)).nextState();    // HasCredit[cents=150]
            Reduction<Vending, Output> sale = vending.step(state, new Select("water", 120));
            sale.nextState();   // Idle[]
            sale.output();      // Vend[item=water, change=30]

            assertThat(state).isEqualTo(new HasCredit(150));
            assertThat(sale.nextState()).isEqualTo(new Idle());
            assertThat(sale.output()).isEqualTo(new Vend("water", 30));
            assertThat(vending.step(new HasCredit(40), new Select("water", 120)).nextState())
                    .isEqualTo(new HasCredit(40));
            assertThat(vending.step(new HasCredit(40), new Refund()).output())
                    .isEqualTo(new Change(40));
        }
    }

    @Nested
    @DisplayName("diagnostics")
    class DiagnosticsExample {

        enum Order { PLACED, PAID, SHIPPED, DELIVERED, CANCELLED }
        enum Event { PAY, SHIP, DELIVER, CANCEL }

        sealed interface Vending permits Idle, HasCredit {}
        record Idle() implements Vending {}
        record HasCredit(int cents) implements Vending {}

        sealed interface Action permits Coin, Select, Refund {}
        record Coin(int cents) implements Action {}
        record Select(String item, int price) implements Action {}
        record Refund() implements Action {}

        sealed interface Output permits Vend, Change {}
        record Vend(String item, int change) implements Output {}
        record Change(int cents) implements Output {}

        @Test
        void enumTopology() {
            EnumStateMachine<Order, Event> draft = EnumStateMachine.<Order, Event>builder(Order.class)
                    .transition(Order.PLACED, Event.PAY,  Order.PAID)
                    .transition(Order.PAID,   Event.SHIP, Order.SHIPPED)
                    .terminal(Order.DELIVERED)
                    .buildEnum(Order.PLACED);

            draft.unreachableStates();   // [DELIVERED, CANCELLED]: no transition leads to them
            draft.deadEndStates();       // [SHIPPED, CANCELLED]: not terminal, and no way out
            draft.coverage();            // 0.6: three of the five states take part in a transition

            assertThat(draft.unreachableStates()).containsExactly(Order.DELIVERED, Order.CANCELLED);
            assertThat(draft.deadEndStates()).containsExactly(Order.SHIPPED, Order.CANCELLED);
            assertThat(draft.coverage()).isEqualTo(0.6);
        }

        @Test
        void typedCompleteness() {
            TypedStateMachine<Vending, Action, Output> partial =
                    TypedStateMachineBuilder.<Vending, Action, Output>create(Vending.class)
                            .on(Idle.class, (s, e) -> Reduction.to(s))
                            .build();

            partial.uncoveredVariants();   // [class HasCredit]: neither handled nor declared terminal

            assertThat(partial.uncoveredVariants()).containsExactly(HasCredit.class);
            assertThatThrownBy(() -> TypedStateMachineBuilder.<Vending, Action, Output>create(Vending.class)
                    .on(Idle.class, (s, e) -> Reduction.to(s))
                    .buildStrict())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("HasCredit");
        }
    }
}
