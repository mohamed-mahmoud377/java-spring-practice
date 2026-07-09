package com.practice._03httpmethods.controller;

import com.practice._03httpmethods.model.Course;
import com.practice._03httpmethods.model.Role;
import com.practice._03httpmethods.model.User;
import com.practice._03httpmethods.store.FileStore;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * The whole student/course API lives in this one controller — on purpose.
 *
 * <p>This is a teaching project about the HTTP "verbs" and the four different places a
 * request can carry data. There is deliberately <b>no service layer</b>: every rule
 * (authentication, who can do what, enrolling) sits right here so you can read one file
 * top to bottom. In a real app you would split this up.</p>
 *
 * <h2>The map you are meant to learn</h2>
 * <pre>
 *   HTTP method  →  intent
 *   ----------------------------------------
 *   GET          →  read, never changes data
 *   POST         →  create something new
 *   PUT          →  replace / update an existing thing
 *   DELETE       →  remove a thing
 *
 *   Where data comes in     →  annotation            →  example
 *   -------------------------------------------------------------------------
 *   Path variable           @PathVariable            /api/courses/{id}
 *   Query parameter         @RequestParam            /api/courses?active=true
 *   Header                  @RequestHeader           X-Username / X-Password
 *   JSON body               @RequestBody             { "name": "Maths" }
 * </pre>
 *
 * <h2>Authentication</h2>
 * There are no sessions or tokens. Every protected call must send two request headers:
 * {@code X-Username} and {@code X-Password}. We look the user up in the file on each call.
 */
@RestController
@RequestMapping("/api")
public class LearningController {

    /** Our "database". Created once; every handler reads/writes through it. */
    private final FileStore store = new FileStore();

    // =====================================================================
    // Request bodies (what @RequestBody deserializes the incoming JSON into).
    // Java records are a compact way to declare an immutable data carrier.
    // =====================================================================

    /** JSON body for creating/updating a course, e.g. {@code {"name":"Maths","active":true}}. */
    public record CourseRequest(String name, Boolean active) {
    }

    /** JSON body for creating a student, e.g. {@code {"username":"amir","password":"pw"}}. */
    public record UserRequest(String username, String password) {
    }

    /** A safe view of a user for responses — deliberately has no password field. */
    public record UserView(String username, Role role, List<String> enrolledCourseIds) {
        static UserView of(User u) {
            return new UserView(u.getUsername(), u.getRole(), u.getEnrolledCourseIds());
        }
    }

    // =====================================================================
    // LOGIN — the simplest possible use of HEADERS.
    // =====================================================================

    /**
     * GET /api/login
     *
     * <p>Reads the {@code X-Username} / {@code X-Password} <b>headers</b>, checks them, and
     * returns who you are. Useful as a "does my login work?" probe. It changes nothing, so
     * it is a GET.</p>
     */
    @GetMapping("/login")
    public UserView login(@RequestHeader("X-Username") String username,
                          @RequestHeader("X-Password") String password) {
        return UserView.of(authenticate(username, password));
    }

    // =====================================================================
    // COURSES — the main tour of methods, path variables and query params.
    // =====================================================================

    /**
     * GET /api/courses  (optionally ?active=true)
     *
     * <p>Lists courses. The optional {@code active} <b>query parameter</b> filters the list:
     * {@code ?active=true} shows only open courses, {@code ?active=false} only closed ones,
     * and leaving it off shows everything. Any logged-in user may call this.</p>
     */
    @GetMapping("/courses")
    public List<Course> listCourses(@RequestHeader("X-Username") String username,
                                    @RequestHeader("X-Password") String password,
                                    @RequestParam(value = "active", required = false) Boolean active) {
        authenticate(username, password); // any valid user is allowed to browse
        List<Course> courses = store.loadCourses();
        if (active == null) {
            return courses;
        }
        return courses.stream()
                .filter(c -> c.isActive() == active)
                .toList();
    }

    /**
     * GET /api/courses/{id}
     *
     * <p>Fetches one course by its id, taken from the URL <b>path variable</b>.
     * Returns 404 if there is no such course.</p>
     */
    @GetMapping("/courses/{id}")
    public Course getCourse(@RequestHeader("X-Username") String username,
                            @RequestHeader("X-Password") String password,
                            @PathVariable("id") String id) {
        authenticate(username, password);
        return findCourse(store.loadCourses(), id);
    }

    /**
     * POST /api/courses
     *
     * <p>Creates a course from the JSON <b>request body</b>. Admin only. Returns 201 Created
     * with the new course (including its generated id).</p>
     */
    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    public Course createCourse(@RequestHeader("X-Username") String username,
                               @RequestHeader("X-Password") String password,
                               @RequestBody CourseRequest body) {
        requireAdmin(username, password);
        if (body.name() == null || body.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course name is required");
        }
        List<Course> courses = store.loadCourses();
        // active defaults to true when the body leaves it out.
        boolean active = body.active() == null || body.active();
        Course course = new Course(FileStore.nextCourseId(courses), body.name().trim(), active);
        courses.add(course);
        store.saveCourses(courses);
        return course;
    }
    /**
     * PUT /api/courses/{id}
     *
     * <p>Updates an existing course: id comes from the <b>path variable</b>, the new values
     * from the JSON <b>body</b>. Admin only. Fields left out of the body are kept as-is.</p>
     */
    @PutMapping("/courses/{id}")
    public Course updateCourse(@RequestHeader("X-Username") String username,
                               @RequestHeader("X-Password") String password,
                               @PathVariable("id") String id,
                               @RequestBody CourseRequest body) {
        requireAdmin(username, password);
        List<Course> courses = store.loadCourses();
        Course course = findCourse(courses, id);
        if (body.name() != null && !body.name().isBlank()) {
            course.setName(body.name().trim());
        }
        if (body.active() != null) {
            course.setActive(body.active());
        }
        store.saveCourses(courses);
        return course;
    }

    /**
     * DELETE /api/courses/{id}
     *
     * <p>Removes a course (id from the <b>path variable</b>) and also un-enrolls every
     * student from it. Admin only. Returns 204 No Content.</p>
     */
    @DeleteMapping("/courses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCourse(@RequestHeader("X-Username") String username,
                             @RequestHeader("X-Password") String password,
                             @PathVariable("id") String id) {
        requireAdmin(username, password);
        List<Course> courses = store.loadCourses();
        boolean removed = courses.removeIf(c -> c.getId().equals(id));
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No course with id " + id);
        }
        store.saveCourses(courses);

        // Clean up: drop this course from every student's enrollment list.
        List<User> users = store.loadUsers();
        for (User u : users) {
            u.getEnrolledCourseIds().remove(id);
        }
        store.saveUsers(users);
    }

    // =====================================================================
    // USERS (students) — admin-only management.
    // =====================================================================

    /**
     * POST /api/users
     *
     * <p>Admin creates a student account from the JSON <b>body</b>. Returns 201 Created.</p>
     */
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView createStudent(@RequestHeader("X-Username") String username,
                                  @RequestHeader("X-Password") String password,
                                  @RequestBody UserRequest body) {
        requireAdmin(username, password);
        if (body.username() == null || body.username().isBlank()
                || body.password() == null || body.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username and password are required");
        }
        List<User> users = store.loadUsers();
        boolean exists = users.stream().anyMatch(u -> u.getUsername().equalsIgnoreCase(body.username()));
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");
        }
        User student = new User(body.username().trim(), body.password(), Role.STUDENT);
        users.add(student);
        store.saveUsers(users);
        return UserView.of(student);
    }

    /**
     * GET /api/users
     *
     * <p>Lists all users (passwords stripped out via {@link UserView}). Admin only.</p>
     */
    @GetMapping("/users")
    public List<UserView> listUsers(@RequestHeader("X-Username") String username,
                                    @RequestHeader("X-Password") String password) {
        requireAdmin(username, password);
        return store.loadUsers().stream().map(UserView::of).toList();
    }

    // =====================================================================
    // ENROLLMENT — a student acting on themselves.
    // =====================================================================

    /**
     * POST /api/courses/{courseId}/enroll
     *
     * <p>The logged-in student (from the <b>headers</b>) enrolls in the course named by the
     * <b>path variable</b>. You cannot enroll in an inactive course.</p>
     */
    @PostMapping("/courses/{courseId}/enroll")
    public UserView enroll(@RequestHeader("X-Username") String username,
                           @RequestHeader("X-Password") String password,
                           @PathVariable("courseId") String courseId) {
        User me = requireStudent(username, password);
        Course course = findCourse(store.loadCourses(), courseId);
        if (!course.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Course is not active");
        }

        List<User> users = store.loadUsers();
        User stored = users.stream()
                .filter(u -> u.getUsername().equals(me.getUsername()))
                .findFirst()
                .orElseThrow();
        if (!stored.getEnrolledCourseIds().contains(courseId)) {
            stored.getEnrolledCourseIds().add(courseId);
            store.saveUsers(users);
        }
        return UserView.of(stored);
    }

    /**
     * DELETE /api/courses/{courseId}/enroll
     *
     * <p>The logged-in student drops the course named by the <b>path variable</b>.</p>
     */
    @DeleteMapping("/courses/{courseId}/enroll")
    public UserView unenroll(@RequestHeader("X-Username") String username,
                             @RequestHeader("X-Password") String password,
                             @PathVariable("courseId") String courseId) {
        User me = requireStudent(username, password);
        List<User> users = store.loadUsers();
        User stored = users.stream()
                .filter(u -> u.getUsername().equals(me.getUsername()))
                .findFirst()
                .orElseThrow();
        stored.getEnrolledCourseIds().remove(courseId);
        store.saveUsers(users);
        return UserView.of(stored);
    }

    /**
     * GET /api/me/courses
     *
     * <p>Returns the full course objects the logged-in student is enrolled in.</p>
     */
    @GetMapping("/me/courses")
    public List<Course> myCourses(@RequestHeader("X-Username") String username,
                                  @RequestHeader("X-Password") String password) {
        User me = requireStudent(username, password);
        List<String> mine = me.getEnrolledCourseIds();
        return store.loadCourses().stream()
                .filter(c -> mine.contains(c.getId()))
                .toList();
    }

    // =====================================================================
    // Shared helpers — the "logic" the endpoints lean on.
    // =====================================================================

    /** Looks the user up by the header credentials, or fails with 401 Unauthorized. */
    private User authenticate(String username, String password) {
        if (username == null || password == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing X-Username / X-Password headers");
        }
        return store.loadUsers().stream()
                .filter(u -> u.getUsername().equals(username) && u.getPassword().equals(password))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bad username or password"));
    }

    /** Authenticates, then requires the ADMIN role (403 Forbidden otherwise). */
    private User requireAdmin(String username, String password) {
        User user = authenticate(username, password);
        if (user.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only");
        }
        return user;
    }

    /** Authenticates, then requires the STUDENT role (403 Forbidden otherwise). */
    private User requireStudent(String username, String password) {
        User user = authenticate(username, password);
        if (user.getRole() != Role.STUDENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Students only");
        }
        return user;
    }

    /** Finds a course by id or throws 404 Not Found. */
    private Course findCourse(List<Course> courses, String id) {
        return courses.stream()
                .filter(c -> c.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No course with id " + id));
    }
}
