package fr.hetic.mmotors;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @Transactional
class DemoDataTest {
    private static final String LONG_PASSWORD = "MotDePasseDemo-2024";

    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder encoder;

    private int demoAccounts() {
        return db.queryForObject("SELECT COUNT(*) FROM app_user WHERE email LIKE '%@mmotors.example'", Integer.class);
    }

    @Test void doesNothingWhenDemoModeIsOff() throws Exception {
        new DemoData().seed(db, encoder, false, "", "").run(null);
        assertThat(demoAccounts()).isZero();
    }
    @Test void refusesShortPasswords() {
        var runner = new DemoData().seed(db, encoder, true, "court", LONG_PASSWORD);
        assertThatThrownBy(() -> runner.run(null)).isInstanceOf(IllegalArgumentException.class);
        var otherRunner = new DemoData().seed(db, encoder, true, LONG_PASSWORD, "court");
        assertThatThrownBy(() -> otherRunner.run(null)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void createsAdminAndClientOnceOnly() throws Exception {
        var runner = new DemoData().seed(db, encoder, true, LONG_PASSWORD, LONG_PASSWORD);
        runner.run(null);
        runner.run(null);
        assertThat(demoAccounts()).isEqualTo(2);
        assertThat(db.queryForObject("SELECT role FROM app_user WHERE email='admin@mmotors.example'", String.class)).isEqualTo("ADMIN");
        assertThat(db.queryForObject("SELECT role FROM app_user WHERE email='client@mmotors.example'", String.class)).isEqualTo("CLIENT");
        assertThat(db.queryForObject("SELECT password_hash FROM app_user WHERE email='admin@mmotors.example'", String.class))
            .isNotEqualTo(LONG_PASSWORD).startsWith("$2");
    }
}
