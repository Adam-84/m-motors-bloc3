package fr.hetic.mmotors;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

/** Erreurs HTTP et test de rôle partagés par les services. */
final class Failures {
    private Failures() {}

    static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Ressource introuvable.");
    }

    static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
