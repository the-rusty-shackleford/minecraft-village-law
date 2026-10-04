/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagelaw.domain;

import java.util.Locale;

/** How grave a crime is: Thief's three grades, under the same names as its {@code Crime}, least
 * grave first. */
public enum Severity {
    LIGHT, MEDIUM, HEAVY;

    /** effects: the graver of this and {@code other}. */
    public Severity max(Severity other) { return compareTo(other) >= 0 ? this : other; }
    /** effects: the grade's name in lower case, as language keys and Thief's config spell it. */
    public String key() { return name().toLowerCase(Locale.ROOT); }
}
