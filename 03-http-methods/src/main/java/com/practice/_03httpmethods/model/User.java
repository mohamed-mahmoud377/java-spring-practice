package com.practice._03httpmethods.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A person who can use the system.
 *
 * <p>This is a plain, mutable "data holder" (a POJO with getters/setters and a no-arg
 * constructor). Both applications (the Spring API and the console app) share this exact
 * class, and the {@code FileStore} turns it into / rebuilds it from a line of text.</p>
 *
 * <p>Note the password is stored in plain text here. That is fine for a learning project
 * but you would <b>never</b> do this in a real system — passwords must be hashed.</p>
 */
public class User {

    private String username;
    private String password;
    private Role role;

    /** Ids of the courses this user is enrolled in. Only meaningful for students. */
    private List<String> enrolledCourseIds = new ArrayList<>();

    public User() {
    }

    public User(String username, String password, Role role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public List<String> getEnrolledCourseIds() {
        return enrolledCourseIds;
    }

    public void setEnrolledCourseIds(List<String> enrolledCourseIds) {
        this.enrolledCourseIds = enrolledCourseIds;
    }
}
