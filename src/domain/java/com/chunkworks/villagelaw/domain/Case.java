/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw.domain;

/** One player's case with one village's law: what they owe it and where the matter stands.
 * Immutable. Every transition is a pure function of an event and the game time, in ticks; an event
 * that does not apply to the current state returns the case unchanged.
 *
 * <pre>
 *  opened             → WANTED
 *  WANTED   served    → SUMMONED        SUMMONED lapsed     → WANTED
 *  SUMMONED paid      → CLEARED         SUMMONED left       → FLEEING (due out at now + leave)
 *  FLEEING  inside at the deadline → HOSTILE
 *  WANTED, SUMMONED, FLEEING, HOSTILE out of the vicinity → BANISHED (until now + banishment)
 *  HOSTILE  died      → BANISHED        BANISHED over       → DEBT
 *  DEBT     paid      → CLEARED         DEBT     crime      → WANTED
 * </pre>
 * A crime in any other open state adds to the fine and keeps the state. Walking away from the
 * summons is leaving it, so a WANTED or SUMMONED player out of the vicinity is banished too.
 *
 * <p>Abstraction function: a player who has committed {@code crimes} crimes in the village, the
 * gravest of them {@code worst}, and owes it {@code fine} emeralds; the matter stands at
 * {@code state}; {@code until} is the tick by which a FLEEING player must be out, or the tick a
 * BANISHED player's banishment ends.
 * <p>Rep invariant: fine &ge; 1; crimes &ge; 1; until is 0 unless the state is FLEEING or BANISHED. */
public record Case(State state, int fine, Severity worst, int crimes, long until) {
    public enum State {
        /** A crime was seen by an officer, and one is on the way to the player. */
        WANTED,
        /** The officer stands before the player with the choice on their screen. */
        SUMMONED,
        /** The player chose to leave and is on the clock. */
        FLEEING,
        /** The player stayed past the deadline: the village's guards and golems attack. */
        HOSTILE,
        /** The player left; they are attacked if they come back before {@code until}. */
        BANISHED,
        /** The banishment is over and the fine still owed. */
        DEBT,
        /** The fine is paid and the matter closed. */
        CLEARED
    }

    public Case {
        if (state == null || worst == null) throw new IllegalArgumentException("state and worst");
        if (fine < 1) throw new IllegalArgumentException("fine " + fine);
        if (crimes < 1) throw new IllegalArgumentException("crimes " + crimes);
        if (until != 0 && state != State.FLEEING && state != State.BANISHED) throw new IllegalArgumentException("until is only a FLEEING or BANISHED deadline");
    }

    /** requires: fine &ge; 1; effects: a new case for one crime, WANTED. */
    public static Case opened(Severity severity, int fine) { return new Case(State.WANTED, fine, severity, 1, 0); }

    /** requires: the case is open, fine &ge; 1; effects: the case with one more crime: the fines
     * add up (capped at Integer.MAX_VALUE), the gravest grade kept. A player in DEBT is WANTED
     * again, since an officer must come to them anew; every other state is kept. */
    public Case withCrime(Severity severity, int fine) {
        if (state == State.CLEARED) throw new IllegalStateException("a cleared case takes no crimes; open a new one");
        if (fine < 1) throw new IllegalArgumentException("fine " + fine);
        int total = (int) Math.min(Integer.MAX_VALUE, (long) this.fine + fine);
        var next = state == State.DEBT ? State.WANTED : state;
        return new Case(next, total, worst.max(severity), crimes == Integer.MAX_VALUE ? crimes : crimes + 1, until);
    }
    /** effects: WANTED becomes SUMMONED: the officer reached the player. */
    public Case served() { return state == State.WANTED ? with(State.SUMMONED, 0) : this; }
    /** effects: SUMMONED becomes WANTED: the choice left the player's screen unanswered (they
     * logged out), so an officer must come again. */
    public Case lapsed() { return state == State.SUMMONED ? with(State.WANTED, 0) : this; }
    /** requires: leaveTicks &ge; 0; effects: SUMMONED becomes FLEEING, due out at
     * {@code now + leaveTicks}. */
    public Case left(long now, long leaveTicks) {
        if (leaveTicks < 0) throw new IllegalArgumentException("leaveTicks " + leaveTicks);
        return state == State.SUMMONED ? with(State.FLEEING, now + leaveTicks) : this;
    }
    /** effects: SUMMONED or DEBT becomes CLEARED: the fine is paid. */
    public Case paid() { return payable() ? with(State.CLEARED, 0) : this; }
    /** requires: banishTicks &ge; 0; effects: the case once the patrol has looked at the player
     * at {@code now}, {@code inside} the village's vicinity or not: out of it, WANTED, SUMMONED,
     * FLEEING and HOSTILE become BANISHED until {@code now + banishTicks}; inside it at or past
     * the deadline, FLEEING becomes HOSTILE; at or past its end, wherever the player is, BANISHED
     * becomes DEBT. */
    public Case ticked(long now, boolean inside, long banishTicks) {
        if (banishTicks < 0) throw new IllegalArgumentException("banishTicks " + banishTicks);
        return switch (state) {
            case WANTED, SUMMONED, HOSTILE -> inside ? this : with(State.BANISHED, now + banishTicks);
            case FLEEING -> !inside ? with(State.BANISHED, now + banishTicks) : now >= until ? with(State.HOSTILE, 0) : this;
            case BANISHED -> now >= until ? with(State.DEBT, 0) : this;
            case DEBT, CLEARED -> this;
        };
    }
    /** requires: banishTicks &ge; 0; effects: HOSTILE becomes BANISHED until
     * {@code now + banishTicks}: the player died to the village's law, and the banishment runs from
     * that moment. */
    public Case died(long now, long banishTicks) {
        if (banishTicks < 0) throw new IllegalArgumentException("banishTicks " + banishTicks);
        return state == State.HOSTILE ? with(State.BANISHED, now + banishTicks) : this;
    }

    /** effects: true until the fine is paid. */
    public boolean open() { return state != State.CLEARED; }
    /** effects: whether the village's guards and golems attack the player, {@code inside} its
     * vicinity or not: always when HOSTILE, and when BANISHED and back inside. */
    public boolean hostile(boolean inside) { return state == State.HOSTILE || state == State.BANISHED && inside; }
    /** effects: whether the village's guards and golems are held back from targeting the player:
     * while the case is open and not hostile. */
    public boolean holdsBack(boolean inside) { return open() && !hostile(inside); }
    /** effects: whether paying now settles the case: SUMMONED or DEBT. */
    public boolean payable() { return state == State.SUMMONED || state == State.DEBT; }
    /** effects: the ticks a FLEEING player has left at {@code now}, never below zero; 0 in every
     * other state. */
    public long ticksLeft(long now) { return state == State.FLEEING ? Math.max(0, until - now) : 0; }

    private Case with(State next, long deadline) { return new Case(next, fine, worst, crimes, deadline); }
}
