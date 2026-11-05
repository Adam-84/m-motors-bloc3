package fr.hetic.mmotors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import java.util.Locale;

@Configuration
class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean UserDetailsService userDetailsService(JdbcTemplate db) {
        return email -> db.query("SELECT email,password_hash,role FROM app_user WHERE email=?",
            (rs,n) -> User.withUsername(rs.getString(1)).password(rs.getString(2)).roles(rs.getString(3)).build(),
            email.trim().toLowerCase(Locale.ROOT)).stream().findFirst()
            .orElseThrow(() -> new UsernameNotFoundException("Identifiants invalides"));
    }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/", "/index.html", "/assets/**", "/favicon.svg", "/api/csrf", "/api/register", "/api/login", "/actuator/health").permitAll()
            .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/vehicles", "/api/vehicles/*", "/api/services").permitAll()
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated())
            .formLogin(form -> form.loginProcessingUrl("/api/login").usernameParameter("email")
                .successHandler((req,res,auth) -> { res.setContentType("application/json"); res.getWriter().write("{\"ok\":true}"); })
                .failureHandler((req,res,error) -> { res.setStatus(401); res.setContentType("application/json"); res.getWriter().write("{\"message\":\"Identifiants invalides\"}"); }))
            .logout(logout -> logout.logoutUrl("/api/logout").logoutSuccessHandler((req,res,auth) -> res.setStatus(204)))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((req,res,error) -> res.sendError(401))
                .accessDeniedHandler((req,res,error) -> res.sendError(403)))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'"))
                .referrerPolicy(policy -> policy.policy(ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }
}
