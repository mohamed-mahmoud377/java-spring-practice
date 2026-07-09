package com.practice._03httpmethods.model;

/**
 * A course a student can enroll in.
 *
 * <p>{@code active} is the flag we later filter on with a query parameter
 * ({@code GET /api/courses?active=true}). An inactive course still exists but is
 * treated as "closed for enrollment / hidden from the default listing".</p>
 */
public class Course {

    private String id;
    private String name;
    private boolean active;

    public Course() {
    }

    public Course(String id, String name, boolean active) {
        this.id = id;
        this.name = name;
        this.active = active;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
