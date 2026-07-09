package com.practice._03httpmethods.console;

import com.practice._03httpmethods.model.Course;
import com.practice._03httpmethods.model.Role;
import com.practice._03httpmethods.model.User;
import com.practice._03httpmethods.store.FileStore;

import java.util.List;
import java.util.Scanner;

/**
 * The <b>console application</b> — the second of the two programs in this project.
 *
 * <p>It does exactly what the REST API does (log in, create courses/students, enroll,
 * list courses, filter by "active"), but the input arrives from a person typing at a menu
 * instead of from an HTTP request. It reads and writes the <b>same</b> {@code data/} files
 * through the <b>same</b> {@link FileStore}, so a course created in the console shows up in
 * the API, and vice-versa.</p>
 *
 * <p>The point of running both is to see that "an endpoint" is really just a function:
 * the HTTP method + path is one way to call it; a menu choice is another. The rules in the
 * middle are identical.</p>
 */
public class ConsoleApp {

    private final FileStore store = new FileStore();
    private final Scanner in = new Scanner(System.in);

    public static void main(String[] args) {
        new ConsoleApp().run();
    }

    private void run() {
        System.out.println("=== Student / Course console ===");
        System.out.println("(Built-in admin: " + FileStore.ADMIN_USERNAME + " / " + FileStore.ADMIN_PASSWORD + ")");

        User current = loginLoop();
        if (current == null) {
            System.out.println("Goodbye.");
            return;
        }

        if (current.getRole() == Role.ADMIN) {
            adminMenu(current);
        } else {
            studentMenu(current);
        }
        System.out.println("Logged out. Bye!");
    }

    // ---------------------------------------------------------------------
    // Login — the console equivalent of sending the X-Username/X-Password headers.
    // ---------------------------------------------------------------------

    private User loginLoop() {
        while (true) {
            System.out.print("\nUsername (blank to quit): ");
            String username = in.nextLine().trim();
            if (username.isEmpty()) {
                return null;
            }
            System.out.print("Password: ");
            String password = in.nextLine();

            User user = authenticate(username, password);
            if (user != null) {
                System.out.println("Welcome, " + user.getUsername() + " (" + user.getRole() + ")");
                return user;
            }
            System.out.println("Bad username or password. Try again.");
        }
    }

    private User authenticate(String username, String password) {
        return store.loadUsers().stream()
                .filter(u -> u.getUsername().equals(username) && u.getPassword().equals(password))
                .findFirst()
                .orElse(null);
    }

    // ---------------------------------------------------------------------
    // Admin menu — mirrors the admin-only API endpoints.
    // ---------------------------------------------------------------------

    private void adminMenu(User admin) {
        while (true) {
            System.out.println("""
                    \n--- ADMIN MENU ---
                    1) Create course          (like POST /api/courses)
                    2) List courses           (like GET  /api/courses?active=...)
                    3) Update course          (like PUT  /api/courses/{id})
                    4) Delete course          (like DELETE /api/courses/{id})
                    5) Create student         (like POST /api/users)
                    6) List users             (like GET  /api/users)
                    0) Log out""");
            System.out.print("Choose: ");
            switch (in.nextLine().trim()) {
                case "1" -> createCourse();
                case "2" -> listCourses();
                case "3" -> updateCourse();
                case "4" -> deleteCourse();
                case "5" -> createStudent();
                case "6" -> listUsers();
                case "0" -> { return; }
                default -> System.out.println("Unknown option.");
            }
        }
    }

    private void createCourse() {
        System.out.print("Course name: ");
        String name = in.nextLine().trim();
        if (name.isEmpty()) {
            System.out.println("Name is required.");
            return;
        }
        boolean active = askYesNo("Active? (y/n): ");
        List<Course> courses = store.loadCourses();
        Course course = new Course(FileStore.nextCourseId(courses), name, active);
        courses.add(course);
        store.saveCourses(courses);
        System.out.println("Created course #" + course.getId() + " '" + course.getName() + "' active=" + course.isActive());
    }

    private void updateCourse() {
        List<Course> courses = store.loadCourses();
        listCoursesTable(courses);
        System.out.print("Id to update: ");
        String id = in.nextLine().trim();
        Course course = courses.stream().filter(c -> c.getId().equals(id)).findFirst().orElse(null);
        if (course == null) {
            System.out.println("No course with id " + id);
            return;
        }
        System.out.print("New name (blank = keep '" + course.getName() + "'): ");
        String name = in.nextLine().trim();
        if (!name.isEmpty()) {
            course.setName(name);
        }
        course.setActive(askYesNo("Active? (y/n): "));
        store.saveCourses(courses);
        System.out.println("Updated.");
    }

    private void deleteCourse() {
        List<Course> courses = store.loadCourses();
        listCoursesTable(courses);
        System.out.print("Id to delete: ");
        String id = in.nextLine().trim();
        if (!courses.removeIf(c -> c.getId().equals(id))) {
            System.out.println("No course with id " + id);
            return;
        }
        store.saveCourses(courses);
        // Same clean-up the DELETE endpoint does: remove the course from every enrollment.
        List<User> users = store.loadUsers();
        users.forEach(u -> u.getEnrolledCourseIds().remove(id));
        store.saveUsers(users);
        System.out.println("Deleted course " + id + ".");
    }

    private void createStudent() {
        System.out.print("New student username: ");
        String username = in.nextLine().trim();
        System.out.print("Password: ");
        String password = in.nextLine();
        if (username.isEmpty() || password.isEmpty()) {
            System.out.println("username and password are required.");
            return;
        }
        List<User> users = store.loadUsers();
        if (users.stream().anyMatch(u -> u.getUsername().equalsIgnoreCase(username))) {
            System.out.println("Username already taken.");
            return;
        }
        users.add(new User(username, password, Role.STUDENT));
        store.saveUsers(users);
        System.out.println("Created student '" + username + "'.");
    }

    private void listUsers() {
        System.out.println("Users:");
        for (User u : store.loadUsers()) {
            System.out.println("  - " + u.getUsername() + " (" + u.getRole() + "), enrolled in " + u.getEnrolledCourseIds());
        }
    }

    // ---------------------------------------------------------------------
    // Student menu — mirrors the student-facing API endpoints.
    // ---------------------------------------------------------------------

    private void studentMenu(User student) {
        while (true) {
            System.out.println("""
                    \n--- STUDENT MENU ---
                    1) List courses           (like GET  /api/courses?active=...)
                    2) Enroll in a course     (like POST /api/courses/{id}/enroll)
                    3) Drop a course          (like DELETE /api/courses/{id}/enroll)
                    4) My courses             (like GET  /api/me/courses)
                    0) Log out""");
            System.out.print("Choose: ");
            switch (in.nextLine().trim()) {
                case "1" -> listCourses();
                case "2" -> enroll(student);
                case "3" -> drop(student);
                case "4" -> myCourses(student);
                case "0" -> { return; }
                default -> System.out.println("Unknown option.");
            }
        }
    }

    private void enroll(User student) {
        List<Course> courses = store.loadCourses();
        listCoursesTable(courses);
        System.out.print("Course id to enroll in: ");
        String id = in.nextLine().trim();
        Course course = courses.stream().filter(c -> c.getId().equals(id)).findFirst().orElse(null);
        if (course == null) {
            System.out.println("No course with id " + id);
            return;
        }
        if (!course.isActive()) {
            System.out.println("That course is not active — cannot enroll.");
            return;
        }
        // Re-load the user so we edit the stored copy, then persist.
        List<User> users = store.loadUsers();
        User stored = users.stream().filter(u -> u.getUsername().equals(student.getUsername())).findFirst().orElseThrow();
        if (stored.getEnrolledCourseIds().contains(id)) {
            System.out.println("Already enrolled.");
            return;
        }
        stored.getEnrolledCourseIds().add(id);
        store.saveUsers(users);
        student.getEnrolledCourseIds().add(id); // keep our in-memory copy in sync
        System.out.println("Enrolled in '" + course.getName() + "'.");
    }

    private void drop(User student) {
        System.out.print("Course id to drop: ");
        String id = in.nextLine().trim();
        List<User> users = store.loadUsers();
        User stored = users.stream().filter(u -> u.getUsername().equals(student.getUsername())).findFirst().orElseThrow();
        if (stored.getEnrolledCourseIds().remove(id)) {
            store.saveUsers(users);
            student.getEnrolledCourseIds().remove(id);
            System.out.println("Dropped course " + id + ".");
        } else {
            System.out.println("You are not enrolled in course " + id + ".");
        }
    }

    private void myCourses(User student) {
        List<String> mine = student.getEnrolledCourseIds();
        List<Course> courses = store.loadCourses().stream().filter(c -> mine.contains(c.getId())).toList();
        if (courses.isEmpty()) {
            System.out.println("You are not enrolled in any courses.");
            return;
        }
        System.out.println("Your courses:");
        listCoursesTable(courses);
    }

    // ---------------------------------------------------------------------
    // Shared listing (with the optional "active" filter — the query-param idea).
    // ---------------------------------------------------------------------

    private void listCourses() {
        System.out.print("Filter — (a)ll / (o)pen active / (c)losed inactive [a]: ");
        String choice = in.nextLine().trim().toLowerCase();
        List<Course> courses = store.loadCourses();
        List<Course> filtered = switch (choice) {
            case "o" -> courses.stream().filter(Course::isActive).toList();
            case "c" -> courses.stream().filter(c -> !c.isActive()).toList();
            default -> courses;
        };
        listCoursesTable(filtered);
    }

    private void listCoursesTable(List<Course> courses) {
        if (courses.isEmpty()) {
            System.out.println("  (no courses)");
            return;
        }
        for (Course c : courses) {
            System.out.printf("  #%s  %-25s %s%n", c.getId(), c.getName(), c.isActive() ? "[active]" : "[inactive]");
        }
    }

    private boolean askYesNo(String prompt) {
        System.out.print(prompt);
        String answer = in.nextLine().trim().toLowerCase();
        return answer.startsWith("y");
    }
}
