package fr.hetic.mmotors;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.UUID;

@Configuration
class DemoData {
    @Bean ApplicationRunner seed(JdbcTemplate db, PasswordEncoder encoder,
        @Value("${app.demo:false}") boolean enabled,
        @Value("${app.admin-password:}") String adminPassword, @Value("${app.user-password:}") String userPassword) {
        return args -> {
            if (!enabled) return;
            if (adminPassword.length()<12 || userPassword.length()<12) {
                throw new IllegalArgumentException("La démo exige ADMIN_PASSWORD et USER_PASSWORD d'au moins 12 caractères.");
            }
            for (String role : new String[]{"ADMIN","CLIENT"}) {
                String email=role.equals("ADMIN")?"admin@mmotors.example":"client@mmotors.example";
                if (db.queryForObject("SELECT COUNT(*) FROM app_user WHERE email=?",Integer.class,email)==0) {
                    db.update("INSERT INTO app_user VALUES (?,?,?,?,?)",UUID.randomUUID().toString(),email,
                        encoder.encode(role.equals("ADMIN")?adminPassword:userPassword),"Compte démo "+role,role);
                }
            }
        };
    }
}
