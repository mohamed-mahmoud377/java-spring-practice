package com.practice._03httpmethods;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the <b>API application</b>.
 *
 * <p>Starts an embedded web server (default: http://localhost:8081) and exposes the
 * endpoints defined in {@code LearningController} and {@code BodyExampleController}. This is
 * one of the two programs in this project; the other is {@code console.ConsoleApp}, which
 * does the same student/course logic through a text menu instead of HTTP.</p>
 */
@SpringBootApplication
public class SpringApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringApiApplication.class, args);
    }
}
