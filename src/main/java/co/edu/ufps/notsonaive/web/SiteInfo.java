package co.edu.ufps.notsonaive.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Project data shown in the footer of every page and on the Algorithm page.
 * All values live in {@code application.properties} under {@code app.site.*},
 * so each one is edited in a single place.
 */
@ConfigurationProperties("app.site")
public record SiteInfo(
        String student,
        String email,
        String date,
        String course,
        String program,
        String university,
        String location,
        String professor,
        String repository,
        String techLine) {
}
