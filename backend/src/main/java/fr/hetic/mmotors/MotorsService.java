package fr.hetic.mmotors;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Cas d'usage de l'application : comptes, catalogue, dossiers, pièces et décisions. */
@Service
public class MotorsService {

    private static final Logger LOG = LoggerFactory.getLogger(MotorsService.class);

    private static final int MAX_PASSWORD_BYTES = 72;
    private static final int MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
    private static final int MAX_VEHICLES = 200;
    private static final Set<String> OFFERS = Set.of("ACHAT", "LOCATION");
    private static final Set<String> DOCUMENT_KINDS = Set.of("IDENTITE", "DOMICILE");

    private static final String DRAFT = "BROUILLON";
    private static final String SUBMITTED = "SOUMIS";
    private static final String REFUSED = "REFUSE";
    private static final String APPROVED = "VALIDE";

    private static final String DOSSIER_SELECT =
        "SELECT d.*, v.brand, v.model, u.email FROM dossier d "
            + "JOIN vehicle v ON d.vehicle_id = v.id "
            + "JOIN app_user u ON d.user_id = u.id ";

    private final JdbcTemplate db;
    private final PasswordEncoder encoder;

    public MotorsService(JdbcTemplate db, PasswordEncoder encoder) {
        this.db = db;
        this.encoder = encoder;
    }

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

    // ---------------------------------------------------------------- comptes

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
            .orElseThrow(MotorsService::missing);
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

    // -------------------------------------------------------------- catalogue

    private Map<String, Object> vehicleRow(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        for (String column : List.of("id", "brand", "model", "fuel", "transmission", "offer")) {
            row.put(column, rs.getString(column));
        }
        row.put("year", rs.getInt("model_year"));
        row.put("mileage", rs.getInt("mileage"));
        row.put("price", rs.getBigDecimal("price"));
        row.put("monthly", rs.getBigDecimal("monthly"));
        row.put("available", rs.getBoolean("available"));
        return row;
    }

    public List<Map<String, Object>> vehicles(String offer, String search, BigDecimal maxPrice, boolean includeUnavailable) {
        if (offer != null && !OFFERS.contains(offer)) {
            throw new IllegalArgumentException("Offre invalide.");
        }
        if (maxPrice != null && maxPrice.signum() < 0) {
            throw new IllegalArgumentException("Le budget doit être positif.");
        }

        StringBuilder sql = new StringBuilder("SELECT * FROM vehicle WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!includeUnavailable) {
            sql.append(" AND available = TRUE");
        }
        if (offer != null) {
            sql.append(" AND offer = ?");
            args.add(offer);
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND LOWER(CONCAT(brand, ' ', model, ' ', fuel)) LIKE ? ESCAPE '!'");
            args.add("%" + escapeLike(search.toLowerCase(Locale.ROOT)) + "%");
        }
        if (maxPrice != null) {
            sql.append(" AND CASE WHEN offer = 'LOCATION' THEN monthly ELSE price END <= ?");
            args.add(maxPrice);
        }
        sql.append(" ORDER BY brand, model LIMIT ").append(MAX_VEHICLES);
        return db.query(sql.toString(), this::vehicleRow, args.toArray());
    }

    /** Neutralise les jokers SQL de la recherche (le caractère d'échappement est « ! »). */
    private static String escapeLike(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    public Map<String, Object> vehicle(String id) {
        return db.query("SELECT * FROM vehicle WHERE id = ?", this::vehicleRow, id)
            .stream()
            .findFirst()
            .orElseThrow(MotorsService::missing);
    }

    private void lockVehicle(String id) {
        List<String> found = db.queryForList("SELECT id FROM vehicle WHERE id = ? FOR UPDATE", String.class, id);
        if (found.isEmpty()) {
            throw missing();
        }
    }

    @Transactional
    public Map<String, Object> addVehicle(Models.VehicleInput v) {
        String id = UUID.randomUUID().toString();
        db.update(
            "INSERT INTO vehicle VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)",
            id,
            v.brand().trim(),
            v.model().trim(),
            v.year(),
            v.mileage(),
            v.fuel(),
            v.transmission(),
            v.price(),
            v.monthly(),
            v.offer());
        LOG.info("event=vehicle_created vehicle={}", id);
        return vehicle(id);
    }

    @Transactional
    public Map<String, Object> switchOffer(String id, String offer) {
        lockVehicle(id);
        if (countActiveDossiersForVehicle(id) > 0) {
            throw conflict("Un dossier actif existe pour ce véhicule. La bascule est bloquée.");
        }
        db.update("UPDATE vehicle SET offer = ? WHERE id = ?", offer, id);
        LOG.info("event=offer_changed vehicle={} offer={}", id, offer);
        return vehicle(id);
    }

    private int countActiveDossiersForVehicle(String vehicleId) {
        return db.queryForObject(
            "SELECT COUNT(*) FROM dossier WHERE vehicle_id = ? AND status <> ?",
            Integer.class,
            vehicleId,
            REFUSED);
    }

    // ---------------------------------------------------------------- dossiers

    @Transactional
    public Map<String, Object> createDossier(String email, Models.DossierInput input) {
        lockVehicle(input.vehicleId());
        Map<String, Object> car = vehicle(input.vehicleId());
        if (!(boolean) car.get("available")) {
            throw conflict("Ce véhicule n'est plus disponible.");
        }

        String offer = (String) car.get("offer");
        BusinessRules.validateOptions(offer, input.options());

        String ownerId = (String) me(email).get("id");
        if (hasActiveDossier(ownerId, input.vehicleId())) {
            throw conflict("Vous avez déjà un dossier actif pour ce véhicule.");
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

    private boolean hasActiveDossier(String userId, String vehicleId) {
        Integer count = db.queryForObject(
            "SELECT COUNT(*) FROM dossier WHERE user_id = ? AND vehicle_id = ? AND status <> ?",
            Integer.class,
            userId,
            vehicleId,
            REFUSED);
        return count != null && count > 0;
    }

    private Map<String, Object> dossierRow(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        for (String column : List.of("id", "user_id", "vehicle_id", "offer", "status", "reason", "brand", "model", "email")) {
            row.put(column, rs.getString(column));
        }
        row.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
        row.put("price", rs.getBigDecimal("price_snapshot"));
        row.put("monthly", rs.getBigDecimal("monthly_snapshot"));
        return row;
    }

    /** Lit un dossier sans contrôle d'accès : réservé aux méthodes qui l'ont déjà fait. */
    private Map<String, Object> dossierUnchecked(String id) {
        Map<String, Object> dossier = db.query(DOSSIER_SELECT + "WHERE d.id = ?", this::dossierRow, id)
            .stream()
            .findFirst()
            .orElseThrow(MotorsService::missing);
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

    /** Lit un dossier pour l'utilisateur connecté : un client ne voit que les siens. */
    public Map<String, Object> dossier(String id, Authentication auth) {
        Map<String, Object> dossier = dossierUnchecked(id);
        boolean owner = dossier.get("email").equals(auth.getName());
        if (!isAdmin(auth) && !owner) {
            throw missing();
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

    private void lockDossier(String id) {
        List<String> found = db.queryForList("SELECT id FROM dossier WHERE id = ? FOR UPDATE", String.class, id);
        if (found.isEmpty()) {
            throw missing();
        }
    }

    // ------------------------------------------------------------------ pièces

    @Transactional
    public Map<String, Object> upload(String id, Authentication auth, String kind, byte[] data) {
        lockDossier(id);
        Map<String, Object> dossier = dossier(id, auth);
        if (!dossier.get("status").equals(DRAFT)) {
            throw conflict("Les pièces d'un dossier envoyé ne sont plus modifiables.");
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
            .orElseThrow(MotorsService::missing);
        dossier((String) document.get("dossierId"), auth); // contrôle d'accès
        return document;
    }

    // --------------------------------------------------------------- workflow

    @Transactional
    public Map<String, Object> submit(String id, Authentication auth) {
        lockDossier(id);
        Map<String, Object> dossier = dossier(id, auth);
        if (!dossier.get("status").equals(DRAFT)) {
            throw conflict("Ce dossier a déjà été envoyé.");
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
            reserveVehicle((String) dossier.get("vehicle_id"));
        }
        db.update(
            "UPDATE dossier SET status = ?, reason = ? WHERE id = ?",
            input.status(),
            input.reason().trim(),
            id);
        LOG.info("event=dossier_decided dossier={} status={}", id, input.status());
        return dossierUnchecked(id);
    }

    /** Réservation atomique : un seul dossier peut obtenir le véhicule, même en cas de validations concurrentes. */
    private void reserveVehicle(String vehicleId) {
        int updated = db.update("UPDATE vehicle SET available = FALSE WHERE id = ? AND available = TRUE", vehicleId);
        if (updated != 1) {
            throw conflict("Le véhicule a déjà été attribué à un autre dossier.");
        }
    }
}
