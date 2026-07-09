package com.practice._03httpmethods;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS (Cross-Origin Resource Sharing) configuration.
 *
 * <p>A browser will refuse to let a page on one origin (say IntelliJ's preview server at
 * {@code http://localhost:63343}) call an API on a different origin ({@code http://localhost:8081})
 * unless the API explicitly says "that's allowed". This class is that explicit permission.</p>
 *
 * <p>Because our auth uses the custom {@code X-Username} / {@code X-Password} headers, the
 * browser first sends a "preflight" {@code OPTIONS} request to check they're permitted —
 * {@code allowedHeaders("*")} lets them through.</p>
 *
 * <p>This wide-open policy (any origin, any method, any header) is fine for a local learning
 * project. A real API would list only the specific front-end origins it trusts.</p>
 *
 * <p>Note: if you open the UI the intended way — served by Spring at
 * {@code http://localhost:8081/} — it's same-origin and CORS never even comes into play.
 * This config only matters when the page is served from somewhere else.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
