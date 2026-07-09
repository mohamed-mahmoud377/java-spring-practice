package com.practice._03httpmethods.model;

/**
 * Who a user is allowed to be.
 *
 * <ul>
 *   <li>{@link #ADMIN} — the built-in account. Can create courses and students.</li>
 *   <li>{@link #STUDENT} — created by an admin. Can browse courses and enroll in them.</li>
 * </ul>
 */
public enum Role {
    ADMIN,
    STUDENT
}
