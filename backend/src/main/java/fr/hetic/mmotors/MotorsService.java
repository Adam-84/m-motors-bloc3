package fr.hetic.mmotors;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MotorsService {
    private final JdbcTemplate db;
    private final PasswordEncoder encoder;
    private static final Logger LOG=LoggerFactory.getLogger(MotorsService.class);
    public MotorsService(JdbcTemplate db, PasswordEncoder encoder) { this.db=db; this.encoder=encoder; }
    static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Ressource introuvable."); }
    static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
    static boolean isAdmin(Authentication auth) { return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")); }

    public Map<String,Object> me(String email) {
        return db.query("SELECT id,email,display_name,role FROM app_user WHERE email=?",(rs,n) -> Map.<String,Object>of(
            "id",rs.getString(1),"email",rs.getString(2),"name",rs.getString(3),"role",rs.getString(4)),email)
            .stream().findFirst().orElseThrow(MotorsService::missing);
    }
    @Transactional
    public Map<String,Object> register(Models.Registration input) {
        if(input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) throw new IllegalArgumentException("Le mot de passe dépasse 72 octets UTF-8. Réduisez sa longueur.");
        String email=input.email().trim().toLowerCase(Locale.ROOT);
        db.update("INSERT INTO app_user VALUES (?,?,?,?,?)",UUID.randomUUID().toString(),email,
            encoder.encode(input.password()),input.name().trim(),"CLIENT");
        return me(email);
    }
    private Map<String,Object> vehicleRow(ResultSet rs,int index) throws SQLException {
        Map<String,Object> row=new LinkedHashMap<>();
        for(String key:List.of("id","brand","model","fuel","transmission","offer")) row.put(key,rs.getString(key));
        row.put("year",rs.getInt("model_year")); row.put("mileage",rs.getInt("mileage"));
        row.put("price",rs.getBigDecimal("price")); row.put("monthly",rs.getBigDecimal("monthly"));
        row.put("available",rs.getBoolean("available")); return row;
    }
    public List<Map<String,Object>> vehicles(String offer,String search,BigDecimal maxPrice, boolean all) {
        if (offer!=null && !Set.of("ACHAT","LOCATION").contains(offer)) throw new IllegalArgumentException("Offre invalide.");
        if (maxPrice!=null && maxPrice.signum()<0) throw new IllegalArgumentException("Le budget doit être positif.");
        StringBuilder sql=new StringBuilder("SELECT * FROM vehicle WHERE 1=1"); List<Object> args=new ArrayList<>();
        if(!all) sql.append(" AND available=TRUE");
        if(offer!=null) { sql.append(" AND offer=?"); args.add(offer); }
        if(search!=null && !search.isBlank()) { sql.append(" AND LOWER(CONCAT(brand,' ',model,' ',fuel)) LIKE ? ESCAPE '!'"); args.add("%"+search.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%"); }
        if(maxPrice!=null) { sql.append(" AND CASE WHEN offer='LOCATION' THEN monthly ELSE price END <= ?"); args.add(maxPrice); }
        sql.append(" ORDER BY brand,model LIMIT 200");
        return db.query(sql.toString(),this::vehicleRow,args.toArray());
    }
    public Map<String,Object> vehicle(String id) {
        return db.query("SELECT * FROM vehicle WHERE id=?",this::vehicleRow,id).stream().findFirst().orElseThrow(MotorsService::missing);
    }
    private void lockVehicle(String id) {
        if(db.queryForList("SELECT id FROM vehicle WHERE id=? FOR UPDATE",String.class,id).isEmpty()) throw missing();
    }
    @Transactional
    public Map<String,Object> addVehicle(Models.VehicleInput v) {
        String id=UUID.randomUUID().toString();
        db.update("INSERT INTO vehicle VALUES (?,?,?,?,?,?,?,?,?,?,TRUE)",id,v.brand().trim(),v.model().trim(),v.year(),v.mileage(),v.fuel(),v.transmission(),v.price(),v.monthly(),v.offer());
        LOG.info("event=vehicle_created vehicle={}",id); return vehicle(id);
    }
    @Transactional
    public Map<String,Object> switchOffer(String id,String offer) {
        lockVehicle(id);
        if(db.queryForObject("SELECT COUNT(*) FROM dossier WHERE vehicle_id=? AND status<>'REFUSE'",Integer.class,id)>0) {
            throw conflict("Un dossier actif existe pour ce véhicule. La bascule est bloquée.");
        }
        db.update("UPDATE vehicle SET offer=? WHERE id=?",offer,id);
        LOG.info("event=offer_changed vehicle={} offer={}",id,offer); return vehicle(id);
    }
    @Transactional
    public Map<String,Object> createDossier(String email,Models.DossierInput input) {
        lockVehicle(input.vehicleId()); Map<String,Object> car=vehicle(input.vehicleId());
        if(!(boolean)car.get("available")) throw conflict("Ce véhicule n'est plus disponible.");
        String offer=(String)car.get("offer"); BusinessRules.validateOptions(offer,input.options());
        String owner=(String)me(email).get("id");
        if(db.queryForObject("SELECT COUNT(*) FROM dossier WHERE user_id=? AND vehicle_id=? AND status<>'REFUSE'",Integer.class,owner,input.vehicleId())>0) {
            throw conflict("Vous avez déjà un dossier actif pour ce véhicule.");
        }
        String id=UUID.randomUUID().toString();
        BigDecimal monthly=offer.equals("LOCATION")?BusinessRules.monthlyTotal((BigDecimal)car.get("monthly"),input.options()):BigDecimal.ZERO;
        db.update("INSERT INTO dossier (id,user_id,vehicle_id,offer,price_snapshot,monthly_snapshot,status) VALUES (?,?,?,?,?,?,?)",id,owner,input.vehicleId(),offer,car.get("price"),monthly,"BROUILLON");
        for(String option:input.options()) db.update("INSERT INTO dossier_option VALUES (?,?)",id,option);
        LOG.info("event=dossier_created dossier={}",id); return dossierUnchecked(id);
    }
    private Map<String,Object> dossierRow(ResultSet rs,int n) throws SQLException {
        Map<String,Object> result=new LinkedHashMap<>();
        for(String key:List.of("id","user_id","vehicle_id","offer","status","reason","brand","model","email")) result.put(key,rs.getString(key));
        result.put("createdAt",rs.getTimestamp("created_at").toInstant().toString());
        result.put("price",rs.getBigDecimal("price_snapshot")); result.put("monthly",rs.getBigDecimal("monthly_snapshot"));
        return result;
    }
    private static final String DOSSIER_SELECT="SELECT d.*,v.brand,v.model,u.email FROM dossier d JOIN vehicle v ON d.vehicle_id=v.id JOIN app_user u ON d.user_id=u.id ";
    private Map<String,Object> dossierUnchecked(String id) {
        Map<String,Object> result=db.query(DOSSIER_SELECT+"WHERE d.id=?",this::dossierRow,id).stream().findFirst().orElseThrow(MotorsService::missing);
        result.put("options",db.queryForList("SELECT code FROM dossier_option WHERE dossier_id=? ORDER BY code",String.class,id));
        result.put("documents",db.query("SELECT id,kind,content_type FROM document WHERE dossier_id=? ORDER BY kind",
            (rs,n) -> Map.of("id",rs.getString(1),"kind",rs.getString(2),"contentType",rs.getString(3)),id));
        return result;
    }
    public Map<String,Object> dossier(String id,Authentication auth) {
        Map<String,Object> result=dossierUnchecked(id);
        if(!isAdmin(auth) && !result.get("email").equals(auth.getName())) throw missing();
        return result;
    }
    public List<Map<String,Object>> dossiers(Authentication auth,boolean admin) {
        // Périmètre pédagogique : tous les dossiers restent accessibles. Paginer avant montée en charge.
        return admin?db.query(DOSSIER_SELECT+"ORDER BY d.created_at DESC",this::dossierRow):
            db.query(DOSSIER_SELECT+"WHERE u.email=? ORDER BY d.created_at DESC",this::dossierRow,auth.getName());
    }
    private void lockDossier(String id) {
        if(db.queryForList("SELECT id FROM dossier WHERE id=? FOR UPDATE",String.class,id).isEmpty()) throw missing();
    }
    @Transactional
    public Map<String,Object> upload(String id,Authentication auth,String kind,byte[] data) {
        lockDossier(id); Map<String,Object> dossier=dossier(id,auth);
        if(!dossier.get("status").equals("BROUILLON")) throw conflict("Les pièces d'un dossier envoyé ne sont plus modifiables.");
        if(!Set.of("IDENTITE","DOMICILE").contains(kind)) throw new IllegalArgumentException("Type de pièce invalide.");
        if(data.length>5*1024*1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Limite de 5 Mo.");
        String type=BusinessRules.fileType(data);
        db.update("DELETE FROM document WHERE dossier_id=? AND kind=?",id,kind);
        db.update("INSERT INTO document VALUES (?,?,?,?,?)",UUID.randomUUID().toString(),id,kind,type,data);
        LOG.info("event=document_uploaded dossier={} kind={}",id,kind); return dossierUnchecked(id);
    }
    public Map<String,Object> document(String docId,Authentication auth) {
        Map<String,Object> doc=db.query("SELECT dossier_id,kind,content_type,content FROM document WHERE id=?",
            (rs,n) -> Map.<String,Object>of("dossierId",rs.getString(1),"kind",rs.getString(2),"contentType",rs.getString(3),"content",rs.getBytes(4)),docId)
            .stream().findFirst().orElseThrow(MotorsService::missing);
        dossier((String)doc.get("dossierId"),auth); return doc;
    }
    @Transactional
    public Map<String,Object> submit(String id,Authentication auth) {
        lockDossier(id); Map<String,Object> dossier=dossier(id,auth);
        if(!dossier.get("status").equals("BROUILLON")) throw conflict("Ce dossier a déjà été envoyé.");
        BusinessRules.requireComplete(new HashSet<>(db.queryForList("SELECT kind FROM document WHERE dossier_id=?",String.class,id)));
        db.update("UPDATE dossier SET status='SOUMIS' WHERE id=?",id);
        LOG.info("event=dossier_submitted dossier={}",id); return dossierUnchecked(id);
    }
    @Transactional
    public Map<String,Object> decide(String id,Models.DecisionInput input) {
        lockDossier(id); Map<String,Object> dossier=dossierUnchecked(id);
        BusinessRules.validateDecision((String)dossier.get("status"),input.status(),input.reason());
        if(input.status().equals("VALIDE") && db.update("UPDATE vehicle SET available=FALSE WHERE id=? AND available=TRUE",dossier.get("vehicle_id"))!=1) {
            throw conflict("Le véhicule a déjà été attribué à un autre dossier.");
        }
        db.update("UPDATE dossier SET status=?,reason=? WHERE id=?",input.status(),input.reason().trim(),id);
        LOG.info("event=dossier_decided dossier={} status={}",id,input.status()); return dossierUnchecked(id);
    }
}
