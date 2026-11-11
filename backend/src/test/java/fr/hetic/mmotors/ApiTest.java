package fr.hetic.mmotors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests d'intégration de l'API : chaque scénario passe par HTTP (MockMvc), avec la vraie base H2 migrée par Flyway.
 * Les véhicules du jeu de démonstration sont : veh-001 (achat) et veh-002 (location), les autres sont inutilisés ici.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiTest {
    private static final String PASSWORD = "ExamplePassword123!";
    private static final String OWNER = "owner@example.test";
    private static final String NO_OPTIONS = "[]";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate db;

    @Autowired
    ObjectMapper json;

    @Autowired
    PasswordEncoder encoder;

    // ------------------------------------------------------------------ aides

    private static UserRequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }

    /** Crée un client directement en base et renvoie son identifiant. */
    private String account(String email) {
        String id = UUID.randomUUID().toString();
        db.update(
                "INSERT INTO app_user VALUES (?,?,?,?,?)",
                id,
                email,
                encoder.encode(PASSWORD),
                "Client test",
                "CLIENT");
        return id;
    }

    private ResultActions postJson(String url, UserRequestPostProcessor who, String body) throws Exception {
        return mvc.perform(post(url).with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions putOffer(UserRequestPostProcessor who, String vehicleId, String offer) throws Exception {
        return mvc.perform(put("/api/admin/vehicles/" + vehicleId + "/offer")
                .with(who)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"offer\":\"" + offer + "\"}"));
    }

    private ResultActions requestDossier(String email, String vehicleId, String options) throws Exception {
        String body = "{\"vehicleId\":\"" + vehicleId + "\",\"options\":" + options + "}";
        return postJson("/api/dossiers", user(email), body);
    }

    /** Crée un dossier (réponse 201 exigée) et renvoie son identifiant. */
    private String dossier(String email, String vehicleId, String options) throws Exception {
        String response = requestDossier(email, vehicleId, options)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(response).get("id").asText();
    }

    private ResultActions sendFile(
            String email, String dossierId, String kind, String contentType, byte[] data) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "piece", contentType, data);
        return mvc.perform(multipart("/api/dossiers/" + dossierId + "/documents")
                .file(file)
                .param("kind", kind)
                .with(user(email))
                .with(csrf()));
    }

    /** Dépose une vraie pièce PDF (réponse 201 exigée). */
    private void upload(String email, String dossierId, String kind) throws Exception {
        sendFile(email, dossierId, kind, "application/pdf", TestFiles.pdf()).andExpect(status().isCreated());
    }

    private ResultActions submit(String email, String dossierId) throws Exception {
        return mvc.perform(post("/api/dossiers/" + dossierId + "/submit").with(user(email)).with(csrf()));
    }

    private ResultActions decide(String dossierId, String status, String reason) throws Exception {
        String body = "{\"status\":\"" + status + "\",\"reason\":\"" + reason + "\"}";
        return postJson("/api/admin/dossiers/" + dossierId + "/decision", admin(), body);
    }

    private String documentId(String dossierId, String kind) {
        return db.queryForObject(
                "SELECT id FROM document WHERE dossier_id=? AND kind=?", String.class, dossierId, kind);
    }

    private void markAllSubmitted() {
        db.update("UPDATE dossier SET status='SOUMIS'");
    }

    // ------------------------------------------------------- catalogue public

    @Test
    void publicSearchFiltersOfferAndBudget() throws Exception {
        mvc.perform(get("/api/vehicles").param("offer", "LOCATION").param("maxPrice", "300"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].model").value("Clio V Techno"));
    }

    @Test
    void filteringTreatsSearchAsDataAndRejectsInvalidBudgets() throws Exception {
        mvc.perform(get("/api/vehicles").param("search", "' OR 1=1 --"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/vehicles").param("search", "renault"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/vehicles").param("offer", "OTHER")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/vehicles").param("maxPrice", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/vehicles").param("maxPrice", "oops")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        mvc.perform(get("/api/services")).andExpect(status().isOk()).andExpect(jsonPath("$.ASSURANCE").value(35));
    }

    @Test
    void blankSearchIsIgnored() throws Exception {
        mvc.perform(get("/api/vehicles").param("search", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));
    }

    // ------------------------------------------------- sécurité et inscription

    @Test
    void unauthorizedCannotReadDossiers() throws Exception {
        mvc.perform(get("/api/dossiers")).andExpect(status().isUnauthorized());
    }

    @Test
    void mutationRequiresCsrf() throws Exception {
        mvc.perform(post("/api/dossiers")
                        .with(user("test@example.test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientCannotAdministerCatalogue() throws Exception {
        mvc.perform(get("/api/admin/dossiers").with(user("client"))).andExpect(status().isForbidden());
        putOffer(user("client"), "veh-001", "LOCATION").andExpect(status().isForbidden());
    }

    @Test
    void registrationNormalizesEmailAndCannotGrantAdmin() throws Exception {
        String body = "{\"email\":\"Alice@Example.test\",\"name\":\"Alice\","
                + "\"password\":\"ExamplePassword123!\",\"role\":\"ADMIN\"}";
        mvc.perform(post("/api/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CLIENT"));

        String role = db.queryForObject(
                "SELECT role FROM app_user WHERE email=?", String.class, "alice@example.test");
        String hash = db.queryForObject(
                "SELECT password_hash FROM app_user WHERE email=?", String.class, "alice@example.test");
        assertThat(role).isEqualTo("CLIENT");
        assertThat(hash).startsWith("$2").isNotEqualTo(PASSWORD);
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        account("alice@example.test");
        String body = "{\"email\":\"alice@example.test\",\"name\":\"Alice\",\"password\":\"ExamplePassword123!\"}";
        mvc.perform(post("/api/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidRegistrationIsRejected() throws Exception {
        String body = "{\"email\":\"bad\",\"name\":\"\",\"password\":\"123\"}";
        mvc.perform(post("/api/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void multibytePasswordExceedingBcryptLimitReturns400() throws Exception {
        String body = json.writeValueAsString(
                Map.of("email", "unicode@example.test", "name", "Test", "password", "é".repeat(40)));
        mvc.perform(post("/api/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void actualPasswordLoginCreatesSessionAndLogoutClearsIt() throws Exception {
        account("login@example.test");
        var result = mvc.perform(post("/api/login")
                        .with(csrf())
                        .param("email", "login@example.test")
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);

        mvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("login@example.test"));
        mvc.perform(post("/api/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void incorrectPasswordReturns401() throws Exception {
        account("login@example.test");
        mvc.perform(post("/api/login")
                        .with(csrf())
                        .param("email", "login@example.test")
                        .param("password", "incorrect"))
                .andExpect(status().isUnauthorized());
    }

    // --------------------------------------------------------------- dossiers

    @Test
    void ownerCanCompleteDossierAndAdminCanApprove() throws Exception {
        account(OWNER);
        String id = dossier(OWNER, "veh-002", "[\"ASSURANCE\"]");
        upload(OWNER, id, "IDENTITE");
        upload(OWNER, id, "DOMICILE");
        submit(OWNER, id).andExpect(status().isOk());

        decide(id, "VALIDE", "Dossier conforme")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDE"));

        Boolean available = db.queryForObject("SELECT available FROM vehicle WHERE id='veh-002'", Boolean.class);
        assertThat(available).isFalse();
        mvc.perform(get("/api/dossiers").with(user(OWNER))).andExpect(jsonPath("$[0].monthly").value(314));
    }

    @Test
    void invalidOptionNeverCreatesDossier() throws Exception {
        account(OWNER);
        requestDossier(OWNER, "veh-001", "[\"ASSURANCE\"]").andExpect(status().isBadRequest());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM dossier", Integer.class)).isZero();
    }

    @Test
    void unknownVehicleCannotReceiveADossier() throws Exception {
        account(OWNER);
        requestDossier(OWNER, "veh-inconnu", NO_OPTIONS).andExpect(status().isNotFound());
    }

    @Test
    void unavailableVehicleCannotReceiveADossier() throws Exception {
        account(OWNER);
        db.update("UPDATE vehicle SET available=FALSE WHERE id='veh-001'");
        requestDossier(OWNER, "veh-001", NO_OPTIONS).andExpect(status().isConflict());
    }

    @Test
    void sameClientCannotOpenTwoActiveDossiersOnOneVehicleButCanRetryAfterRefusal() throws Exception {
        account(OWNER);
        String first = dossier(OWNER, "veh-001", NO_OPTIONS);
        requestDossier(OWNER, "veh-001", NO_OPTIONS).andExpect(status().isConflict());

        db.update("UPDATE dossier SET status='REFUSE' WHERE id=?", first);
        dossier(OWNER, "veh-001", NO_OPTIONS);
    }

    @Test
    void missingDocumentsPreventSubmission() throws Exception {
        account(OWNER);
        String id = dossier(OWNER, "veh-001", NO_OPTIONS);
        submit(OWNER, id).andExpect(status().isBadRequest());
    }

    @Test
    void submittedDossierCannotBeResubmittedOrHaveItsDocumentsReplaced() throws Exception {
        account(OWNER);
        String id = dossier(OWNER, "veh-001", NO_OPTIONS);
        upload(OWNER, id, "IDENTITE");
        upload(OWNER, id, "DOMICILE");

        submit(OWNER, id).andExpect(status().isOk());
        submit(OWNER, id).andExpect(status().isConflict());
        sendFile(OWNER, id, "IDENTITE", "application/pdf", TestFiles.pdf()).andExpect(status().isConflict());
    }

    @Test
    void adminHistoryDoesNotHideOldSubmittedDossiers() throws Exception {
        String ownerId = account(OWNER);
        for (int n = 0; n < 201; n++) {
            db.update(
                    "INSERT INTO dossier (id,user_id,vehicle_id,offer,price_snapshot,monthly_snapshot,status) "
                            + "VALUES (?,?,?,'ACHAT',14990,0,'SOUMIS')",
                    "history-" + n,
                    ownerId,
                    "veh-001");
        }
        mvc.perform(get("/api/admin/dossiers").with(admin())).andExpect(jsonPath("$.length()").value(201));
    }

    // ---------------------------------------------------------------- décision

    @Test
    void onlyOneSubmittedApplicantCanReceiveTheSameVehicle() throws Exception {
        account("first@example.test");
        account("second@example.test");
        String first = dossier("first@example.test", "veh-001", NO_OPTIONS);
        String second = dossier("second@example.test", "veh-001", NO_OPTIONS);
        markAllSubmitted();

        decide(first, "VALIDE", "").andExpect(status().isOk());
        decide(second, "VALIDE", "").andExpect(status().isConflict());

        Integer approved = db.queryForObject("SELECT COUNT(*) FROM dossier WHERE status='VALIDE'", Integer.class);
        assertThat(approved).isEqualTo(1);
        requestDossier("second@example.test", "veh-001", NO_OPTIONS).andExpect(status().isConflict());
    }

    @Test
    void refusedDossierKeepsTheVehicleAvailable() throws Exception {
        account(OWNER);
        String id = dossier(OWNER, "veh-001", NO_OPTIONS);
        markAllSubmitted();

        decide(id, "REFUSE", "Pièces illisibles")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUSE"));

        Boolean available = db.queryForObject("SELECT available FROM vehicle WHERE id='veh-001'", Boolean.class);
        assertThat(available).isTrue();
    }

    @Test
    void decisionOnUnknownDossierReturns404() throws Exception {
        decide("inconnu", "VALIDE", "").andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ pièces

    @Test
    void otherClientCannotReadOrUploadDocuments() throws Exception {
        account(OWNER);
        account("stranger@example.test");
        String id = dossier(OWNER, "veh-001", NO_OPTIONS);
        upload(OWNER, id, "IDENTITE");

        mvc.perform(get("/api/dossiers/" + id).with(user("stranger@example.test")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/documents/" + documentId(id, "IDENTITE")).with(user("stranger@example.test")))
                .andExpect(status().isNotFound());
        sendFile("stranger@example.test", id, "DOMICILE", "application/pdf", "%PDF-1.7".getBytes())
                .andExpect(status().isNotFound());
    }

    @Test
    void executableDisguisedAsPdfIsRejected() throws Exception {
        account(OWNER);
        String id = dossier(OWNER, "veh-001", NO_OPTIONS);
        sendFile(OWNER, id, "IDENTITE", "application/pdf", "MZexe".getBytes()).andExpect(status().isBadRequest());
    }

    @Test
    void documentKindMustBeKnown() throws Exception {
        account(OWNER);
        String id = dossier(OWNER, "veh-001", NO_OPTIONS);
        sendFile(OWNER, id, "PASSEPORT", "application/pdf", TestFiles.pdf()).andExpect(status().isBadRequest());
    }

    @Test
    void documentAboveFiveMegabytesReturns413() throws Exception {
        account(OWNER);
        String id = dossier(OWNER, "veh-001", NO_OPTIONS);
        sendFile(OWNER, id, "IDENTITE", "application/pdf", new byte[5 * 1024 * 1024 + 1])
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value("Limite de 5 Mo."));
    }

    @Test
    void uploadToUnknownDossierReturns404() throws Exception {
        account(OWNER);
        sendFile(OWNER, "inconnu", "IDENTITE", "application/pdf", TestFiles.pdf())
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerAndAdminCanDownloadPrivateDocumentButNotAfterItsDeletion() throws Exception {
        account(OWNER);
        String id = dossier(OWNER, "veh-001", NO_OPTIONS);
        upload(OWNER, id, "IDENTITE");
        String documentId = documentId(id, "IDENTITE");

        mvc.perform(get("/api/documents/" + documentId).with(user(OWNER)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"IDENTITE.pdf\""));
        mvc.perform(get("/api/documents/" + documentId).with(admin())).andExpect(status().isOk());
        mvc.perform(get("/api/documents/missing").with(user(OWNER))).andExpect(status().isNotFound());
    }

    @Test
    void downloadSetsExtensionFromTheRealContentType() throws Exception {
        account(OWNER);
        String id = dossier(OWNER, "veh-001", NO_OPTIONS);
        sendFile(OWNER, id, "IDENTITE", "image/png", TestFiles.image("png")).andExpect(status().isCreated());
        sendFile(OWNER, id, "DOMICILE", "image/png", TestFiles.image("jpeg")).andExpect(status().isCreated());

        mvc.perform(get("/api/documents/" + documentId(id, "IDENTITE")).with(user(OWNER)))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"IDENTITE.png\""));
        mvc.perform(get("/api/documents/" + documentId(id, "DOMICILE")).with(user(OWNER)))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"DOMICILE.jpg\""));
    }

    // ------------------------------------------------- gestion du catalogue

    @Test
    void adminCanSwitchOfferButActiveDossierBlocksChange() throws Exception {
        putOffer(admin(), "veh-001", "LOCATION").andExpect(status().isOk());

        account(OWNER);
        dossier(OWNER, "veh-001", NO_OPTIONS);
        putOffer(admin(), "veh-001", "ACHAT").andExpect(status().isConflict());
    }

    @Test
    void adminCanAddVehicleWithValidatedPrices() throws Exception {
        String body = "{\"brand\":\"Renault\",\"model\":\"Captur\",\"year\":2024,\"mileage\":12000,"
                + "\"fuel\":\"Hybride\",\"transmission\":\"Automatique\","
                + "\"price\":22900,\"monthly\":320,\"offer\":\"LOCATION\"}";
        postJson("/api/admin/vehicles", admin(), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.brand").value("Renault"));

        mvc.perform(get("/api/admin/vehicles").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7));
    }
}
