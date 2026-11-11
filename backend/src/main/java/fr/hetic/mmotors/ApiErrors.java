package fr.hetic.mmotors;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Traduit chaque exception en réponse JSON {"message": ...} sans jamais exposer de détail technique. */
@RestControllerAdvice
class ApiErrors {
    private static final Logger LOG = LoggerFactory.getLogger(ApiErrors.class);

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<?> notFound(Exception e) {
        return reply(HttpStatus.NOT_FOUND, "Ressource introuvable.");
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    ResponseEntity<?> badParameter(Exception e) {
        return reply(HttpStatus.BAD_REQUEST, "Paramètre invalide ou manquant.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> invalid(IllegalArgumentException e) {
        return reply(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<?> validation(Exception e) {
        return reply(HttpStatus.BAD_REQUEST, "Vérifiez les champs et les formats demandés.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict(Exception e) {
        return reply(HttpStatus.CONFLICT, "Cette valeur existe déjà ou entre en conflit avec les données.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException e) {
        String message = e.getReason() == null ? "Requête refusée" : e.getReason();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", message));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<?> tooLarge(Exception e) {
        return reply(HttpStatus.PAYLOAD_TOO_LARGE, "La pièce dépasse la limite de 5 Mo.");
    }

    /** Erreur inattendue : le détail reste dans les logs, le client reçoit seulement une référence. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception e) {
        String reference = UUID.randomUUID().toString();
        LOG.error("incident={} type={}", reference, e.getClass().getSimpleName());
        return reply(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur est survenue. Référence : " + reference);
    }

    private static ResponseEntity<?> reply(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }
}
