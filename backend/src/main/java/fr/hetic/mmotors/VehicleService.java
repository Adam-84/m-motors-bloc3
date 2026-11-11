package fr.hetic.mmotors;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catalogue : recherche publique, ajout de véhicule et bascule achat/location par l'admin. */
@Service
public class VehicleService {
    private static final Logger LOG = LoggerFactory.getLogger(VehicleService.class);

    private static final int MAX_VEHICLES = 200;
    private static final Set<String> OFFERS = Set.of("ACHAT", "LOCATION");

    private final JdbcTemplate db;

    public VehicleService(JdbcTemplate db) {
        this.db = db;
    }

    public List<Map<String, Object>> vehicles(
            String offer, String search, BigDecimal maxPrice, boolean includeUnavailable) {
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

    public Map<String, Object> vehicle(String id) {
        return db.query("SELECT * FROM vehicle WHERE id = ?", this::vehicleRow, id)
                .stream()
                .findFirst()
                .orElseThrow(Failures::missing);
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
        lock(id);
        if (countActiveDossiers(id) > 0) {
            throw Failures.conflict("Un dossier actif existe pour ce véhicule. La bascule est bloquée.");
        }
        db.update("UPDATE vehicle SET offer = ? WHERE id = ?", offer, id);
        LOG.info("event=offer_changed vehicle={} offer={}", id, offer);
        return vehicle(id);
    }

    /** Verrouille la ligne du véhicule jusqu'à la fin de la transaction en cours. */
    void lock(String id) {
        List<String> found = db.queryForList("SELECT id FROM vehicle WHERE id = ? FOR UPDATE", String.class, id);
        if (found.isEmpty()) {
            throw Failures.missing();
        }
    }

    /** Réservation atomique : un seul dossier peut obtenir le véhicule, même en cas de validations concurrentes. */
    void reserve(String vehicleId) {
        int updated = db.update("UPDATE vehicle SET available = FALSE WHERE id = ? AND available = TRUE", vehicleId);
        if (updated != 1) {
            throw Failures.conflict("Le véhicule a déjà été attribué à un autre dossier.");
        }
    }

    private int countActiveDossiers(String vehicleId) {
        return db.queryForObject(
                "SELECT COUNT(*) FROM dossier WHERE vehicle_id = ? AND status <> ?",
                Integer.class,
                vehicleId,
                DossierService.REFUSED);
    }

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

    /** Neutralise les jokers SQL de la recherche (le caractère d'échappement est « ! »). */
    private static String escapeLike(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
