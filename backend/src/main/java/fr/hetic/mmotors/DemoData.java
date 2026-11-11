package fr.hetic.mmotors;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Crée les deux comptes de démonstration quand app.demo=true, avec des mots de passe fournis par l'environnement. */
@Configuration
class DemoData {
    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final String ADMIN_EMAIL = "admin@mmotors.example";
    private static final String CLIENT_EMAIL = "client@mmotors.example";

    @Bean
    ApplicationRunner seed(JdbcTemplate db, PasswordEncoder encoder,
            @Value("${app.demo:false}") boolean enabled,
            @Value("${app.admin-password:}") String adminPassword,
            @Value("${app.user-password:}") String userPassword) {
        return args -> {
            if (!enabled) {
                return;
            }
            if (adminPassword.length() < MIN_PASSWORD_LENGTH || userPassword.length() < MIN_PASSWORD_LENGTH) {
                throw new IllegalArgumentException(
                        "La démo exige ADMIN_PASSWORD et USER_PASSWORD d'au moins 12 caractères.");
            }
            createIfMissing(db, encoder, ADMIN_EMAIL, adminPassword, "ADMIN");
            createIfMissing(db, encoder, CLIENT_EMAIL, userPassword, "CLIENT");
        };
    }

    private static void createIfMissing(JdbcTemplate db, PasswordEncoder encoder,
            String email, String password, String role) {
        Integer existing = db.queryForObject("SELECT COUNT(*) FROM app_user WHERE email=?", Integer.class, email);
        if (existing != 0) {
            return;
        }
        db.update("INSERT INTO app_user VALUES (?,?,?,?,?)",
                UUID.randomUUID().toString(), email, encoder.encode(password), "Compte démo " + role, role);
    }
}
