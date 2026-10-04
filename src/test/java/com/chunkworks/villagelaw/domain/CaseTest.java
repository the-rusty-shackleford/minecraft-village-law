/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw.domain;

import com.chunkworks.villagelaw.domain.Case.State;
import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import static com.chunkworks.villagelaw.domain.Case.State.*;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions:
 * <ul>
 * <li>state: each of the seven;</li>
 * <li>event: opened, crime, served, lapsed, left, paid, ticked, died;</li>
 * <li>time against a FLEEING or BANISHED deadline: before it, at it, after it;</li>
 * <li>the player inside the vicinity or out of it;</li>
 * <li>queries (open, hostile, holdsBack, payable, ticksLeft) per state, inside and out;</li>
 * <li>the rep invariant: fine and crimes at their least and below, until outside its two states;</li>
 * <li>fines and crime counts at the int range's edge.</li>
 * </ul> */
final class CaseTest {
    private static final long NOW = 10_000, LEAVE = 1_200, BANISH = 48_000;

    /** effects: a case in that state, fined 8 for one medium crime, with a deadline at
     * {@code NOW + 100} where the state has one. */
    private static Case in(State state) {
        long until = state == FLEEING || state == BANISHED ? NOW + 100 : 0;
        return new Case(state, 8, Severity.MEDIUM, 1, until);
    }

    @Test void openedIsWantedForOneCrime() {
        assertEquals(new Case(WANTED, 15, Severity.HEAVY, 1, 0), Case.opened(Severity.HEAVY, 15));
    }

    @Test void aCrimeAddsUpAndKeepsTheStateButDebtIsWantedAgain() {
        for (var s : EnumSet.complementOf(EnumSet.of(CLEARED, DEBT))) {
            var c = in(s).withCrime(Severity.HEAVY, 15);
            assertEquals(s, c.state(), "kept: " + s);
            assertEquals(23, c.fine(), "eight and fifteen");
            assertEquals(2, c.crimes());
            assertEquals(Severity.HEAVY, c.worst(), "the graver grade");
            assertEquals(in(s).until(), c.until(), "the deadline stands");
        }
        assertEquals(new Case(WANTED, 11, Severity.MEDIUM, 2, 0), in(DEBT).withCrime(Severity.LIGHT, 3), "an officer must come anew; the lighter grade does not lower the worst");
        assertThrows(IllegalStateException.class, () -> in(CLEARED).withCrime(Severity.LIGHT, 3));
        assertThrows(IllegalArgumentException.class, () -> in(WANTED).withCrime(Severity.LIGHT, 0));
    }

    @Test void finesAndCountsSaturateAtTheIntRange() {
        var c = new Case(WANTED, Integer.MAX_VALUE - 1, Severity.LIGHT, Integer.MAX_VALUE, 0).withCrime(Severity.LIGHT, 3);
        assertEquals(Integer.MAX_VALUE, c.fine());
        assertEquals(Integer.MAX_VALUE, c.crimes());
    }

    @Test void servedOnlyFromWanted() {
        assertEquals(in(SUMMONED), in(WANTED).served());
        for (var s : EnumSet.complementOf(EnumSet.of(WANTED))) assertEquals(in(s), in(s).served(), "unchanged: " + s);
    }

    @Test void lapsedOnlyFromSummoned() {
        assertEquals(in(WANTED), in(SUMMONED).lapsed());
        for (var s : EnumSet.complementOf(EnumSet.of(SUMMONED))) assertEquals(in(s), in(s).lapsed(), "unchanged: " + s);
    }

    @Test void leftOnlyFromSummonedWithTheDeadlineSet() {
        assertEquals(new Case(FLEEING, 8, Severity.MEDIUM, 1, NOW + LEAVE), in(SUMMONED).left(NOW, LEAVE));
        assertEquals(new Case(FLEEING, 8, Severity.MEDIUM, 1, NOW), in(SUMMONED).left(NOW, 0), "no time at all");
        for (var s : EnumSet.complementOf(EnumSet.of(SUMMONED))) assertEquals(in(s), in(s).left(NOW, LEAVE), "unchanged: " + s);
        assertThrows(IllegalArgumentException.class, () -> in(SUMMONED).left(NOW, -1));
    }

    @Test void paidOnlyFromSummonedOrDebt() {
        assertEquals(in(CLEARED), in(SUMMONED).paid());
        assertEquals(in(CLEARED), in(DEBT).paid());
        for (var s : EnumSet.of(WANTED, FLEEING, HOSTILE, BANISHED, CLEARED)) assertEquals(in(s), in(s).paid(), "unchanged: " + s);
    }

    @Test void outOfTheVicinityTheChaseIsOverAndTheBanishmentBegins() {
        for (var s : EnumSet.of(WANTED, SUMMONED, FLEEING, HOSTILE)) {
            assertEquals(new Case(BANISHED, 8, Severity.MEDIUM, 1, NOW + BANISH), in(s).ticked(NOW, false, BANISH), "out: " + s);
        }
        for (var s : EnumSet.of(WANTED, SUMMONED, HOSTILE)) assertEquals(in(s), in(s).ticked(NOW, true, BANISH), "inside: " + s);
    }

    @Test void fleeingAgainstTheDeadline() {
        var fleeing = in(FLEEING); // due out at NOW + 100
        assertEquals(fleeing, fleeing.ticked(NOW + 99, true, BANISH), "inside, before the deadline");
        assertEquals(in(HOSTILE), fleeing.ticked(NOW + 100, true, BANISH), "inside, at the deadline");
        assertEquals(in(HOSTILE), fleeing.ticked(NOW + 5_000, true, BANISH), "inside, after it");
        assertEquals(new Case(BANISHED, 8, Severity.MEDIUM, 1, NOW + 99 + BANISH), fleeing.ticked(NOW + 99, false, BANISH), "out before the deadline");
        assertEquals(new Case(BANISHED, 8, Severity.MEDIUM, 1, NOW + 100 + BANISH), fleeing.ticked(NOW + 100, false, BANISH), "out at the deadline: out wins");
    }

    @Test void banishmentRunsOutWhereverThePlayerIs() {
        var banished = in(BANISHED); // ends at NOW + 100
        for (boolean inside : new boolean[] { true, false }) {
            assertEquals(banished, banished.ticked(NOW + 99, inside, BANISH), "before its end, inside " + inside);
            assertEquals(in(DEBT), banished.ticked(NOW + 100, inside, BANISH), "at its end, inside " + inside);
            assertEquals(in(DEBT), banished.ticked(NOW + 101, inside, BANISH), "after it, inside " + inside);
        }
    }

    @Test void debtAndClearedIgnoreThePatrol() {
        for (var s : EnumSet.of(DEBT, CLEARED)) for (boolean inside : new boolean[] { true, false })
            assertEquals(in(s), in(s).ticked(NOW + BANISH * 10, inside, BANISH), s + " inside " + inside);
        assertThrows(IllegalArgumentException.class, () -> in(WANTED).ticked(NOW, true, -1));
    }

    @Test void dyingOnlyEndsHostility() {
        assertEquals(new Case(BANISHED, 8, Severity.MEDIUM, 1, NOW + BANISH), in(HOSTILE).died(NOW, BANISH));
        for (var s : EnumSet.complementOf(EnumSet.of(HOSTILE))) assertEquals(in(s), in(s).died(NOW, BANISH), "unchanged: " + s);
        assertThrows(IllegalArgumentException.class, () -> in(HOSTILE).died(NOW, -1));
    }

    @Test void hostilityAndHoldingBack() {
        for (var s : State.values()) for (boolean inside : new boolean[] { true, false }) {
            boolean hostile = s == HOSTILE || s == BANISHED && inside;
            assertEquals(hostile, in(s).hostile(inside), s + " hostile, inside " + inside);
            assertEquals(s != CLEARED && !hostile, in(s).holdsBack(inside), s + " holds back, inside " + inside);
        }
    }

    @Test void openPayableAndTicksLeft() {
        for (var s : State.values()) {
            assertEquals(s != CLEARED, in(s).open(), "open: " + s);
            assertEquals(s == SUMMONED || s == DEBT, in(s).payable(), "payable: " + s);
        }
        assertEquals(100, in(FLEEING).ticksLeft(NOW));
        assertEquals(0, in(FLEEING).ticksLeft(NOW + 100), "at the deadline");
        assertEquals(0, in(FLEEING).ticksLeft(NOW + 500), "never below zero");
        assertEquals(0, in(BANISHED).ticksLeft(NOW), "only a FLEEING player is on the clock");
    }

    @Test void repInvariant() {
        assertThrows(IllegalArgumentException.class, () -> new Case(WANTED, 0, Severity.LIGHT, 1, 0), "fine below one");
        assertThrows(IllegalArgumentException.class, () -> new Case(WANTED, 1, Severity.LIGHT, 0, 0), "no crimes");
        assertThrows(IllegalArgumentException.class, () -> new Case(WANTED, 1, Severity.LIGHT, 1, 5), "a deadline outside FLEEING and BANISHED");
        assertThrows(IllegalArgumentException.class, () -> new Case(null, 1, Severity.LIGHT, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Case(WANTED, 1, null, 1, 0));
        assertDoesNotThrow(() -> new Case(WANTED, 1, Severity.LIGHT, 1, 0), "fine and crimes at their least");
        assertDoesNotThrow(() -> new Case(FLEEING, 1, Severity.LIGHT, 1, 0), "a deadline at tick zero");
    }
}
