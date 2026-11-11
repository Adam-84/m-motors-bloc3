package fr.hetic.mmotors;

import java.io.IOException;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
class SecurityConfig {
    private static final int BCRYPT_STRENGTH = 12;

    /** Politique CSP stricte : rien ne se charge hors de l'origine, aucun script ni style en ligne. */
    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self'",
            "img-src 'self' data:",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "frame-ancestors 'none'",
            "form-action 'self'");

    private static final String[] PUBLIC_PAGES = {
        "/", "/index.html", "/assets/**", "/favicon.svg", "/og-image.png", "/actuator/health"
    };
    private static final String[] PUBLIC_API = {"/api/csrf", "/api/register", "/api/login"};
    private static final String[] PUBLIC_CATALOGUE = {"/api/vehicles", "/api/vehicles/*", "/api/services"};

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    UserDetailsService userDetailsService(JdbcTemplate db) {
        return email -> db.query(
                        "SELECT email,password_hash,role FROM app_user WHERE email=?",
                        (rs, n) -> User.withUsername(rs.getString(1))
                                .password(rs.getString(2))
                                .roles(rs.getString(3))
                                .build(),
                        email.trim().toLowerCase(Locale.ROOT))
                .stream()
                .findFirst()
                .orElseThrow(() -> new UsernameNotFoundException("Identifiants invalides"));
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PAGES).permitAll()
                        .requestMatchers(PUBLIC_API).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_CATALOGUE).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginProcessingUrl("/api/login")
                        .usernameParameter("email")
                        .successHandler((req, res, auth) -> writeJson(res, 200, "{\"ok\":true}"))
                        .failureHandler((req, res, error) ->
                                writeJson(res, 401, "{\"message\":\"Identifiants invalides\"}")))
                .logout(logout -> logout
                        .logoutUrl("/api/logout")
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((req, res, error) -> res.sendError(401))
                        .accessDeniedHandler((req, res, error) -> res.sendError(403)))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(policy -> policy.policy(ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }

    private static void writeJson(HttpServletResponse res, int status, String body) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.getWriter().write(body);
    }
}
