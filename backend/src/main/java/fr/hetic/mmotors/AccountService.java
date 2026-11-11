package fr.hetic.mmotors;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Comptes : inscription d'un client et lecture du profil connecté. */
@Service
public class AccountService {
    /** BCrypt ignore tout ce qui dépasse 72 octets : on refuse plutôt que de tronquer en silence. */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final JdbcTemplate db;
    private final PasswordEncoder encoder;

    public AccountService(JdbcTemplate db, PasswordEncoder encoder) {
        this.db = db;
        this.encoder = encoder;
    }

    public Map<String, Object> me(String email) {
        return db.query(
                        "SELECT id, email, display_name, role FROM app_user WHERE email = ?",
                        (rs, rowNum) -> Map.<String, Object>of(
                                "id", rs.getString(1),
                                "email", rs.getString(2),
                                "name", rs.getString(3),
                                "role", rs.getString(4)),
                        email)
                .stream()
                .findFirst()
                .orElseThrow(Failures::missing);
    }

    @Transactional
    public Map<String, Object> register(Models.Registration input) {
        int passwordBytes = input.password().getBytes(StandardCharsets.UTF_8).length;
        if (passwordBytes > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException("Le mot de passe dépasse 72 octets UTF-8. Réduisez sa longueur.");
        }
        String email = input.email().trim().toLowerCase(Locale.ROOT);
        db.update(
                "INSERT INTO app_user VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(),
                email,
                encoder.encode(input.password()),
                input.name().trim(),
                "CLIENT");
        return me(email);
    }
}
