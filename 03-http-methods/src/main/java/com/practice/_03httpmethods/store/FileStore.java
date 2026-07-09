package com.practice._03httpmethods.store;

import com.practice._03httpmethods.model.Course;
import com.practice._03httpmethods.model.Role;
import com.practice._03httpmethods.model.User;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The "database": two plain text files on disk, {@code users.txt} and {@code courses.txt},
 * inside a {@code data/} folder next to wherever you run the program from.
 *
 * <p>We store the data as simple text — one record per line, fields separated by a pipe
 * {@code |} — so you can open the files in any editor and read them without knowing JSON.
 * (JSON still shows up in this project, but only where it belongs: the HTTP request and
 * response bodies. That is a separate idea from how we save data to disk.)</p>
 *
 * <h2>The file format</h2>
 * <pre>
 *   courses.txt   →   id|name|active
 *                     1|Intro to Spring|true
 *                     2|Advanced Databases|false
 *
 *   users.txt     →   username|password|role|enrolledCourseIds(comma-separated)
 *                     admin|admin123|ADMIN|
 *                     amir|secret|STUDENT|1,3
 * </pre>
 *
 * <p>This class does <b>only</b> raw persistence — turn a list into lines, turn lines back
 * into a list. It holds no business rules; those live in the controller and the console app.</p>
 *
 * <p>The very first time it runs, if there is no users file yet, it seeds the built-in
 * admin account ({@code admin} / {@code admin123}).</p>
 */
public class FileStore {

    /** Built-in administrator, created automatically on first run. */
    public static final String ADMIN_USERNAME = "admin";
    public static final String ADMIN_PASSWORD = "admin123";

    /** The single character that separates the fields on a line. */
    private static final String SEP = "|";

    private final Path usersFile;
    private final Path coursesFile;

    /** Uses a {@code data/} directory relative to the current working directory. */
    public FileStore() {
        this(Path.of("data"));
    }

    public FileStore(Path dataDir) {
        try {
            Files.createDirectories(dataDir);
            this.usersFile = dataDir.resolve("users.txt");
            this.coursesFile = dataDir.resolve("courses.txt");
            seedIfEmpty();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not initialise data directory", e);
        }
    }

    // ---------------------------------------------------------------------
    // Users
    // ---------------------------------------------------------------------

    public synchronized List<User> loadUsers() {
        List<User> users = new ArrayList<>();
        for (String line : readLines(usersFile)) {
            users.add(parseUser(line));
        }
        return users;
    }

    public synchronized void saveUsers(List<User> users) {
        List<String> lines = new ArrayList<>();
        for (User u : users) {
            lines.add(formatUser(u));
        }
        writeLines(usersFile, lines);
    }

    // ---------------------------------------------------------------------
    // Courses
    // ---------------------------------------------------------------------

    public synchronized List<Course> loadCourses() {
        List<Course> courses = new ArrayList<>();
        for (String line : readLines(coursesFile)) {
            courses.add(parseCourse(line));
        }
        return courses;
    }

    public synchronized void saveCourses(List<Course> courses) {
        List<String> lines = new ArrayList<>();
        for (Course c : courses) {
            lines.add(formatCourse(c));
        }
        writeLines(coursesFile, lines);
    }

    /**
     * Produces the next course id as a simple increasing number ("1", "2", "3", ...).
     * Handy so both apps generate ids the same way.
     */
    public static String nextCourseId(List<Course> existing) {
        int max = 0;
        for (Course c : existing) {
            try {
                max = Math.max(max, Integer.parseInt(c.getId()));
            } catch (NumberFormatException ignored) {
                // Non-numeric id (shouldn't happen) — just skip it.
            }
        }
        return String.valueOf(max + 1);
    }

    // ---------------------------------------------------------------------
    // Turning objects into lines and back again
    // ---------------------------------------------------------------------

    /** {@code id|name|active} */
    private String formatCourse(Course c) {
        return String.join(SEP,
                c.getId(),
                clean(c.getName()),
                String.valueOf(c.isActive()));
    }

    private Course parseCourse(String line) {
        // -1 keeps trailing empty fields (e.g. an empty enrollment list) instead of dropping them.
        String[] parts = line.split("\\" + SEP, -1);
        String id = parts[0];
        String name = parts[1];
        boolean active = Boolean.parseBoolean(parts[2]);
        return new Course(id, name, active);
    }

    /** {@code username|password|role|id1,id2,...} */
    private String formatUser(User u) {
        return String.join(SEP,
                clean(u.getUsername()),
                clean(u.getPassword()),
                u.getRole().name(),
                String.join(",", u.getEnrolledCourseIds()));
    }

    private User parseUser(String line) {
        String[] parts = line.split("\\" + SEP, -1);
        User user = new User(parts[0], parts[1], Role.valueOf(parts[2]));
        String enrolled = parts[3];
        if (!enrolled.isEmpty()) {
            for (String id : enrolled.split(",")) {
                user.getEnrolledCourseIds().add(id);
            }
        }
        return user;
    }

    /**
     * Removes the characters that would break our one-record-per-line, pipe-separated
     * format (the pipe itself and any line breaks). Keeps the files parseable.
     */
    private String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(SEP, "/").replace("\n", " ").replace("\r", " ");
    }

    // ---------------------------------------------------------------------
    // Low-level file IO
    // ---------------------------------------------------------------------

    private void seedIfEmpty() {
        if (!Files.exists(usersFile)) {
            List<User> users = new ArrayList<>();
            users.add(new User(ADMIN_USERNAME, ADMIN_PASSWORD, Role.ADMIN));
            saveUsers(users);
        }
        if (!Files.exists(coursesFile)) {
            saveCourses(new ArrayList<>());
        }
    }

    private List<String> readLines(Path file) {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try {
            List<String> lines = new ArrayList<>();
            for (String line : Files.readAllLines(file)) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
            return lines;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }

    private void writeLines(Path file, List<String> lines) {
        try {
            Files.write(file, lines);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + file, e);
        }
    }
}
