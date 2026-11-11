package fr.hetic.mmotors;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Dossiers : création, pièces justificatives, envoi puis décision de l'administrateur. */
@Service
public class DossierService {
    private static final Logger LOG = LoggerFactory.getLogger(DossierService.class);

    private static final int MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
    private static final Set<String> DOCUMENT_KINDS = Set.of("IDENTITE", "DOMICILE");

    private static final String DRAFT = "BROUILLON";
    private static final String SUBMITTED = "SOUMIS";
    static final String REFUSED = "REFUSE";
    private static final String APPROVED = "VALIDE";

    private static final String DOSSIER_SELECT =
            "SELECT d.*, v.brand, v.model, u.email FROM dossier d "
                    + "JOIN vehicle v ON d.vehicle_id = v.id "
                    + "JOIN app_user u ON d.user_id = u.id ";

    private static final List<String> TEXT_COLUMNS =
            List.of("id", "user_id", "vehicle_id", "offer", "status", "reason", "brand", "model", "email");

    private final JdbcTemplate db;
    private final AccountService accounts;
    private final VehicleService vehicles;

    public DossierService(JdbcTemplate db, AccountService accounts, VehicleService vehicles) {
        this.db = db;
        this.accounts = accounts;
        this.vehicles = vehicles;
    }

    // ---------------------------------------------------------------- création

    @Transactional
    public Map<String, Object> createDossier(String email, Models.DossierInput input) {
        vehicles.lock(input.vehicleId());
        Map<String, Object> car = vehicles.vehicle(input.vehicleId());
        if (!(boolean) car.get("available")) {
            throw Failures.conflict("Ce véhicule n'est plus disponible.");
        }

        String offer = (String) car.get("offer");
        BusinessRules.validateOptions(offer, input.options());

        String ownerId = (String) accounts.me(email).get("id");
        if (hasActiveDossier(ownerId, input.vehicleId())) {
            throw Failures.conflict("Vous avez déjà un dossier actif pour ce véhicule.");
        }

        BigDecimal monthly = offer.equals("LOCATION")
                ? BusinessRules.monthlyTotal((BigDecimal) car.get("monthly"), input.options())
                : BigDecimal.ZERO;

        String id = UUID.randomUUID().toString();
        db.update(
                "INSERT INTO dossier (id, user_id, vehicle_id, offer, price_snapshot, monthly_snapshot, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,
                ownerId,
                input.vehicleId(),
                offer,
                car.get("price"),
                monthly,
                DRAFT);
        for (String option : input.options()) {
            db.update("INSERT INTO dossier_option VALUES (?, ?)", id, option);
        }
        LOG.info("event=dossier_created dossier={}", id);
        return dossierUnchecked(id);
    }

    // ----------------------------------------------------------------- lecture

    /** Lit un dossier pour l'utilisateur connecté : un client ne voit que les siens. */
    public Map<String, Object> dossier(String id, Authentication auth) {
        Map<String, Object> dossier = dossierUnchecked(id);
        boolean owner = dossier.get("email").equals(auth.getName());
        if (!Failures.isAdmin(auth) && !owner) {
            throw Failures.missing();
        }
        return dossier;
    }

    public List<Map<String, Object>> dossiers(Authentication auth, boolean allClients) {
        // Périmètre pédagogique : tous les dossiers restent accessibles. Paginer avant montée en charge.
        if (allClients) {
            return db.query(DOSSIER_SELECT + "ORDER BY d.created_at DESC", this::dossierRow);
        }
        return db.query(
                DOSSIER_SELECT + "WHERE u.email = ? ORDER BY d.created_at DESC",
                this::dossierRow,
                auth.getName());
    }

    // ------------------------------------------------------------------ pièces

    @Transactional
    public Map<String, Object> upload(String id, Authentication auth, String kind, byte[] data) {
        lockDossier(id);
        Map<String, Object> dossier = dossier(id, auth);
        if (!dossier.get("status").equals(DRAFT)) {
            throw Failures.conflict("Les pièces d'un dossier envoyé ne sont plus modifiables.");
        }
        if (!DOCUMENT_KINDS.contains(kind)) {
            throw new IllegalArgumentException("Type de pièce invalide.");
        }
        if (data.length > MAX_UPLOAD_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Limite de 5 Mo.");
        }

        String contentType = BusinessRules.fileType(data);
        db.update("DELETE FROM document WHERE dossier_id = ? AND kind = ?", id, kind);
        db.update(
                "INSERT INTO document VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(),
                id,
                kind,
                contentType,
                data);
        LOG.info("event=document_uploaded dossier={} kind={}", id, kind);
        return dossierUnchecked(id);
    }

    public Map<String, Object> document(String documentId, Authentication auth) {
        Map<String, Object> document = db.query(
                        "SELECT dossier_id, kind, content_type, content FROM document WHERE id = ?",
                        (rs, rowNum) -> Map.<String, Object>of(
                                "dossierId", rs.getString(1),
                                "kind", rs.getString(2),
                                "contentType", rs.getString(3),
                                "content", rs.getBytes(4)),
                        documentId)
                .stream()
                .findFirst()
                .orElseThrow(Failures::missing);
        dossier((String) document.get("dossierId"), auth); // contrôle d'accès
        return document;
    }

    // --------------------------------------------------------------- workflow

    @Transactional
    public Map<String, Object> submit(String id, Authentication auth) {
        lockDossier(id);
        Map<String, Object> dossier = dossier(id, auth);
        if (!dossier.get("status").equals(DRAFT)) {
            throw Failures.conflict("Ce dossier a déjà été envoyé.");
        }
        List<String> kinds = db.queryForList("SELECT kind FROM document WHERE dossier_id = ?", String.class, id);
        BusinessRules.requireComplete(new HashSet<>(kinds));
        db.update("UPDATE dossier SET status = ? WHERE id = ?", SUBMITTED, id);
        LOG.info("event=dossier_submitted dossier={}", id);
        return dossierUnchecked(id);
    }

    @Transactional
    public Map<String, Object> decide(String id, Models.DecisionInput input) {
        lockDossier(id);
        Map<String, Object> dossier = dossierUnchecked(id);
        BusinessRules.validateDecision((String) dossier.get("status"), input.status(), input.reason());

        if (input.status().equals(APPROVED)) {
            vehicles.reserve((String) dossier.get("vehicle_id"));
        }
        db.update(
                "UPDATE dossier SET status = ?, reason = ? WHERE id = ?",
                input.status(),
                input.reason().trim(),
                id);
        LOG.info("event=dossier_decided dossier={} status={}", id, input.status());
        return dossierUnchecked(id);
    }

    // ---------------------------------------------------------------- interne

    private boolean hasActiveDossier(String userId, String vehicleId) {
        Integer count = db.queryForObject(
                "SELECT COUNT(*) FROM dossier WHERE user_id = ? AND vehicle_id = ? AND status <> ?",
                Integer.class,
                userId,
                vehicleId,
                REFUSED);
        return count != null && count > 0;
    }

    private void lockDossier(String id) {
        List<String> found = db.queryForList("SELECT id FROM dossier WHERE id = ? FOR UPDATE", String.class, id);
        if (found.isEmpty()) {
            throw Failures.missing();
        }
    }

    /** Lit un dossier sans contrôle d'accès : réservé aux méthodes qui l'ont déjà fait. */
    private Map<String, Object> dossierUnchecked(String id) {
        Map<String, Object> dossier = db.query(DOSSIER_SELECT + "WHERE d.id = ?", this::dossierRow, id)
                .stream()
                .findFirst()
                .orElseThrow(Failures::missing);
        dossier.put("options", db.queryForList(
                "SELECT code FROM dossier_option WHERE dossier_id = ? ORDER BY code", String.class, id));
        dossier.put("documents", db.query(
                "SELECT id, kind, content_type FROM document WHERE dossier_id = ? ORDER BY kind",
                (rs, rowNum) -> Map.of(
                        "id", rs.getString(1),
                        "kind", rs.getString(2),
                        "contentType", rs.getString(3)),
                id));
        return dossier;
    }

    private Map<String, Object> dossierRow(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        for (String column : TEXT_COLUMNS) {
            row.put(column, rs.getString(column));
        }
        row.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
        row.put("price", rs.getBigDecimal("price_snapshot"));
        row.put("monthly", rs.getBigDecimal("monthly_snapshot"));
        return row;
    }
}
