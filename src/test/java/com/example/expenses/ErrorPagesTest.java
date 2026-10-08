package com.example.expenses;

import com.example.expenses.model.User;
import com.example.expenses.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real HTTP requests, so Spring Boot's error handling runs for real (MockMvc skips it):
 * friendly pages in the shared layout, and nothing internal reaches the browser.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ErrorPagesTest.BrokenController.class)
class ErrorPagesTest {

    /** A page that always fails with a message that must never be shown. Only exists in this test. */
    @Controller
    static class BrokenController {

        @GetMapping("/test-only/broken")
        String broken() {
            throw new IllegalStateException("db-password=hunter2 while saving amount 4500.00");
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private HttpClient browser;

    @BeforeEach
    void signIn() throws Exception {
        if (userRepository.findByUsername("erin").isEmpty()) {
            userRepository.save(new User("erin", passwordEncoder.encode("erin-password")));
        }
        browser = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        Matcher csrf = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(get("/login").body());
        assertThat(csrf.find()).isTrue();
        String form = "username=erin&password=erin-password&_csrf=" + URLEncoder.encode(csrf.group(1), StandardCharsets.UTF_8);
        HttpResponse<String> login = browser.send(HttpRequest.newBuilder(uri("/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(login.headers().firstValue("Location")).hasValueSatisfying(l -> assertThat(l).endsWith("/expenses"));
    }

    @Test
    void unknownPageGetsAFriendly404InTheLayout() throws Exception {
        HttpResponse<String> response = get("/no-such-page");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body())
                .contains("Page not found", "Back to expenses", "class=\"site-header\"")
                .doesNotContain("Whitelabel", "trace", "Exception");
    }

    @Test
    void crashGetsAFriendly500WithoutAnyDetails() throws Exception {
        HttpResponse<String> response = get("/test-only/broken");

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body())
                .contains("Something went wrong", "class=\"site-header\"")
                .doesNotContain("hunter2", "4500.00", "IllegalStateException", "Exception", "at com.example", "trace");
    }

    @Test
    void missingExpenseGetsTheFriendlyNotFoundPage() throws Exception {
        HttpResponse<String> response = get("/expenses/999999/edit");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("Expense not found").doesNotContain("Exception");
    }

    @Test
    void nonBrowserClientsGetJsonWithoutDetailsToo() throws Exception {
        HttpResponse<String> response = browser.send(HttpRequest.newBuilder(uri("/test-only/broken"))
                .header("Accept", "application/json").GET().build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body()).contains("\"status\":500")
                .doesNotContain("hunter2", "4500.00", "Exception", "trace", "message");
    }

    /** Like a browser: asks for HTML. */
    private HttpResponse<String> get(String path) throws Exception {
        return browser.send(HttpRequest.newBuilder(uri(path)).header("Accept", "text/html").GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
