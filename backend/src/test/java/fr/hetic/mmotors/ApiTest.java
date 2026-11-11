package fr.hetic.mmotors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class ApiTest {
    @Test void adminHistoryDoesNotHideOldSubmittedDossiers() throws Exception {
        String owner=account("owner@example.test");
        for (int n=0;n<201;n++) db.update("INSERT INTO dossier (id,user_id,vehicle_id,offer,price_snapshot,monthly_snapshot,status) VALUES (?,?,?,'ACHAT',14990,0,'SOUMIS')","history-"+n,owner,"veh-001");
        mvc.perform(get("/api/admin/dossiers").with(user("admin").roles("ADMIN"))).andExpect(jsonPath("$.length()").value(201));
    }
    @Test void multibytePasswordExceedingBcryptLimitReturns400() throws Exception {
        mvc.perform(post("/api/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(java.util.Map.of("email","unicode@example.test","name","Test","password","é".repeat(40)))))
            .andExpect(status().isBadRequest());
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;

    String account(String email) {
        String id=UUID.randomUUID().toString();
        db.update("INSERT INTO app_user VALUES (?,?,?,?,?)",id,email,encoder.encode("ExamplePassword123!"),"Client test","CLIENT");
        return id;
    }
    String dossier(String email, String vehicle, String options) throws Exception {
        String response=mvc.perform(post("/api/dossiers").with(user(email)).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"vehicleId\":\""+vehicle+"\",\"options\":"+options+"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asText();
    }
    void upload(String email, String id, String kind) throws Exception {
        mvc.perform(multipart("/api/dossiers/"+id+"/documents").file(new MockMultipartFile("file","piece.pdf","application/pdf",TestFiles.pdf()))
            .param("kind",kind).with(user(email)).with(csrf())).andExpect(status().isCreated());
    }
    @Test void publicSearchFiltersOfferAndBudget() throws Exception {
        mvc.perform(get("/api/vehicles").param("offer","LOCATION").param("maxPrice","300"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].model").value("Clio V Techno"));
    }
    @Test void unauthorizedCannotReadDossiers() throws Exception {
        mvc.perform(get("/api/dossiers")).andExpect(status().isUnauthorized());
    }
    @Test void mutationRequiresCsrf() throws Exception {
        mvc.perform(post("/api/dossiers").with(user("test@example.test"))
            .contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden());
    }
    @Test void registrationNormalizesEmailAndCannotGrantAdmin() throws Exception {
        mvc.perform(post("/api/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"Alice@Example.test\",\"name\":\"Alice\",\"password\":\"ExamplePassword123!\",\"role\":\"ADMIN\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("CLIENT"));
        assertThat(db.queryForObject("SELECT role FROM app_user WHERE email=?",String.class,"alice@example.test")).isEqualTo("CLIENT");
        assertThat(db.queryForObject("SELECT password_hash FROM app_user WHERE email=?",String.class,"alice@example.test"))
            .startsWith("$2").isNotEqualTo("ExamplePassword123!");
    }
    @Test void duplicateEmailReturnsConflict() throws Exception {
        account("alice@example.test");
        mvc.perform(post("/api/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"alice@example.test\",\"name\":\"Alice\",\"password\":\"ExamplePassword123!\"}"))
            .andExpect(status().isConflict());
    }
    @Test void invalidRegistrationIsRejected() throws Exception {
        mvc.perform(post("/api/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"bad\",\"name\":\"\",\"password\":\"123\"}"))
            .andExpect(status().isBadRequest());
    }
    @Test void actualPasswordLoginCreatesSessionAndLogoutClearsIt() throws Exception {
        account("login@example.test");
        var result=mvc.perform(post("/api/login").with(csrf()).param("email","login@example.test").param("password","ExamplePassword123!"))
            .andExpect(status().isOk()).andReturn();
        var session=(org.springframework.mock.web.MockHttpSession)result.getRequest().getSession(false);
        mvc.perform(get("/api/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("login@example.test"));
        mvc.perform(post("/api/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
    }
    @Test void incorrectPasswordReturns401() throws Exception {
        account("login@example.test");
        mvc.perform(post("/api/login").with(csrf()).param("email","login@example.test").param("password","incorrect"))
            .andExpect(status().isUnauthorized());
    }
    @Test void ownerCanCompleteDossierAndAdminCanApprove() throws Exception {
        account("owner@example.test");
        String id=dossier("owner@example.test","veh-002","[\"ASSURANCE\"]");
        upload("owner@example.test",id,"IDENTITE"); upload("owner@example.test",id,"DOMICILE");
        mvc.perform(post("/api/dossiers/"+id+"/submit").with(user("owner@example.test")).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/admin/dossiers/"+id+"/decision").with(user("admin").roles("ADMIN")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"VALIDE\",\"reason\":\"Dossier conforme\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALIDE"));
        assertThat(db.queryForObject("SELECT available FROM vehicle WHERE id='veh-002'",Boolean.class)).isFalse();
        mvc.perform(get("/api/dossiers").with(user("owner@example.test"))).andExpect(jsonPath("$[0].monthly").value(314));
    }
    @Test void otherClientCannotReadOrUploadDocuments() throws Exception {
        account("owner@example.test"); account("stranger@example.test");
        String id=dossier("owner@example.test","veh-001","[]"); upload("owner@example.test",id,"IDENTITE");
        mvc.perform(get("/api/dossiers/"+id).with(user("stranger@example.test"))).andExpect(status().isNotFound());
        String docId=db.queryForObject("SELECT id FROM document WHERE dossier_id=?",String.class,id);
        mvc.perform(get("/api/documents/"+docId).with(user("stranger@example.test"))).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/dossiers/"+id+"/documents").file(new MockMultipartFile("file","x.pdf","application/pdf","%PDF-1.7".getBytes()))
            .param("kind","DOMICILE").with(user("stranger@example.test")).with(csrf())).andExpect(status().isNotFound());
    }
    @Test void missingDocumentsPreventSubmission() throws Exception {
        account("owner@example.test"); String id=dossier("owner@example.test","veh-001","[]");
        mvc.perform(post("/api/dossiers/"+id+"/submit").with(user("owner@example.test")).with(csrf())).andExpect(status().isBadRequest());
    }
    @Test void executableDisguisedAsPdfIsRejected() throws Exception {
        account("owner@example.test"); String id=dossier("owner@example.test","veh-001","[]");
        mvc.perform(multipart("/api/dossiers/"+id+"/documents").file(new MockMultipartFile("file","x.pdf","application/pdf","MZexe".getBytes()))
            .param("kind","IDENTITE").with(user("owner@example.test")).with(csrf())).andExpect(status().isBadRequest());
    }
    @Test void clientCannotAdministerCatalogue() throws Exception {
        mvc.perform(get("/api/admin/dossiers").with(user("client"))).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/vehicles/veh-001/offer").with(user("client")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"offer\":\"LOCATION\"}" )).andExpect(status().isForbidden());
    }
    @Test void adminCanSwitchOfferButActiveDossierBlocksChange() throws Exception {
        mvc.perform(put("/api/admin/vehicles/veh-001/offer").with(user("admin").roles("ADMIN")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"offer\":\"LOCATION\"}" )).andExpect(status().isOk());
        account("owner@example.test"); dossier("owner@example.test","veh-001","[]");
        mvc.perform(put("/api/admin/vehicles/veh-001/offer").with(user("admin").roles("ADMIN")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"offer\":\"ACHAT\"}" )).andExpect(status().isConflict());
    }
    @Test void invalidOptionNeverCreatesDossier() throws Exception {
        account("owner@example.test");
        mvc.perform(post("/api/dossiers").with(user("owner@example.test")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"vehicleId\":\"veh-001\",\"options\":[\"ASSURANCE\"]}" )).andExpect(status().isBadRequest());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM dossier",Integer.class)).isZero();
    }
    @Test void adminCanAddVehicleWithValidatedPrices() throws Exception {
        mvc.perform(post("/api/admin/vehicles").with(user("admin").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"brand\":\"Renault\",\"model\":\"Captur\",\"year\":2024,\"mileage\":12000,\"fuel\":\"Hybride\",\"transmission\":\"Automatique\",\"price\":22900,\"monthly\":320,\"offer\":\"LOCATION\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.brand").value("Renault"));
        mvc.perform(get("/api/admin/vehicles").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(7));
    }
    @Test void ownerAndAdminCanDownloadPrivateDocumentButNotAfterItsDeletion() throws Exception {
        account("owner@example.test"); String id=dossier("owner@example.test","veh-001","[]"); upload("owner@example.test",id,"IDENTITE");
        String docId=db.queryForObject("SELECT id FROM document WHERE dossier_id=?",String.class,id);
        mvc.perform(get("/api/documents/"+docId).with(user("owner@example.test")))
            .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
            .andExpect(header().string("Content-Disposition","attachment; filename=\"IDENTITE.pdf\""));
        mvc.perform(get("/api/documents/"+docId).with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/api/documents/missing").with(user("owner@example.test"))).andExpect(status().isNotFound());
    }
    @Test void submittedDossierCannotBeResubmittedOrHaveItsDocumentsReplaced() throws Exception {
        account("owner@example.test"); String id=dossier("owner@example.test","veh-001","[]");
        upload("owner@example.test",id,"IDENTITE"); upload("owner@example.test",id,"DOMICILE");
        mvc.perform(post("/api/dossiers/"+id+"/submit").with(user("owner@example.test")).with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/dossiers/"+id+"/submit").with(user("owner@example.test")).with(csrf())).andExpect(status().isConflict());
        mvc.perform(multipart("/api/dossiers/"+id+"/documents").file(new MockMultipartFile("file","piece.pdf","application/pdf",TestFiles.pdf()))
            .param("kind","IDENTITE").with(user("owner@example.test")).with(csrf())).andExpect(status().isConflict());
    }
    @Test void onlyOneSubmittedApplicantCanReceiveTheSameVehicle() throws Exception {
        account("first@example.test"); account("second@example.test");
        String first=dossier("first@example.test","veh-001","[]"),second=dossier("second@example.test","veh-001","[]");
        db.update("UPDATE dossier SET status='SOUMIS'");
        mvc.perform(post("/api/admin/dossiers/"+first+"/decision").with(user("admin").roles("ADMIN")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"VALIDE\",\"reason\":\"\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/admin/dossiers/"+second+"/decision").with(user("admin").roles("ADMIN")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"VALIDE\",\"reason\":\"\"}")).andExpect(status().isConflict());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM dossier WHERE status='VALIDE'",Integer.class)).isEqualTo(1);
        mvc.perform(post("/api/dossiers").with(user("second@example.test")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"vehicleId\":\"veh-001\",\"options\":[]}")).andExpect(status().isConflict());
    }
    @Test void filteringTreatsSearchAsDataAndRejectsInvalidBudgets() throws Exception {
        mvc.perform(get("/api/vehicles").param("search","' OR 1=1 --")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/vehicles").param("search","renault")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/vehicles").param("offer","OTHER")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/vehicles").param("maxPrice","-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/vehicles").param("maxPrice","oops")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        mvc.perform(get("/api/services")).andExpect(status().isOk()).andExpect(jsonPath("$.ASSURANCE").value(35));
    }
    @Test void unknownVehicleCannotReceiveADossier() throws Exception {
        account("owner@example.test");
        mvc.perform(post("/api/dossiers").with(user("owner@example.test")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"vehicleId\":\"veh-inconnu\",\"options\":[]}")).andExpect(status().isNotFound());
    }
    @Test void sameClientCannotOpenTwoActiveDossiersOnOneVehicleButCanRetryAfterRefusal() throws Exception {
        account("owner@example.test");
        String first=dossier("owner@example.test","veh-001","[]");
        mvc.perform(post("/api/dossiers").with(user("owner@example.test")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"vehicleId\":\"veh-001\",\"options\":[]}")).andExpect(status().isConflict());
        db.update("UPDATE dossier SET status='REFUSE' WHERE id=?",first);
        dossier("owner@example.test","veh-001","[]");
    }
    @Test void unavailableVehicleCannotReceiveADossier() throws Exception {
        account("owner@example.test");
        db.update("UPDATE vehicle SET available=FALSE WHERE id='veh-001'");
        mvc.perform(post("/api/dossiers").with(user("owner@example.test")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"vehicleId\":\"veh-001\",\"options\":[]}")).andExpect(status().isConflict());
    }
    @Test void blankSearchIsIgnored() throws Exception {
        mvc.perform(get("/api/vehicles").param("search","   ")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6));
    }
    @Test void documentKindMustBeKnown() throws Exception {
        account("owner@example.test"); String id=dossier("owner@example.test","veh-001","[]");
        mvc.perform(multipart("/api/dossiers/"+id+"/documents").file(new MockMultipartFile("file","piece.pdf","application/pdf",TestFiles.pdf()))
            .param("kind","PASSEPORT").with(user("owner@example.test")).with(csrf())).andExpect(status().isBadRequest());
    }
    @Test void documentAboveFiveMegabytesReturns413() throws Exception {
        account("owner@example.test"); String id=dossier("owner@example.test","veh-001","[]");
        mvc.perform(multipart("/api/dossiers/"+id+"/documents").file(new MockMultipartFile("file","gros.pdf","application/pdf",new byte[5*1024*1024+1]))
            .param("kind","IDENTITE").with(user("owner@example.test")).with(csrf()))
            .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.message").value("Limite de 5 Mo."));
    }
    @Test void uploadToUnknownDossierReturns404() throws Exception {
        account("owner@example.test");
        mvc.perform(multipart("/api/dossiers/inconnu/documents").file(new MockMultipartFile("file","piece.pdf","application/pdf",TestFiles.pdf()))
            .param("kind","IDENTITE").with(user("owner@example.test")).with(csrf())).andExpect(status().isNotFound());
    }
    @Test void decisionOnUnknownDossierReturns404() throws Exception {
        mvc.perform(post("/api/admin/dossiers/inconnu/decision").with(user("admin").roles("ADMIN")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"VALIDE\",\"reason\":\"\"}")).andExpect(status().isNotFound());
    }
    @Test void refusedDossierKeepsTheVehicleAvailable() throws Exception {
        account("owner@example.test"); String id=dossier("owner@example.test","veh-001","[]");
        db.update("UPDATE dossier SET status='SOUMIS'");
        mvc.perform(post("/api/admin/dossiers/"+id+"/decision").with(user("admin").roles("ADMIN")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REFUSE\",\"reason\":\"Pièces illisibles\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUSE"));
        assertThat(db.queryForObject("SELECT available FROM vehicle WHERE id='veh-001'",Boolean.class)).isTrue();
    }
    @Test void downloadSetsExtensionFromTheRealContentType() throws Exception {
        account("owner@example.test"); String id=dossier("owner@example.test","veh-001","[]");
        for (var file : java.util.Map.of("png",TestFiles.image("png"),"jpg",TestFiles.image("jpeg")).entrySet()) {
            mvc.perform(multipart("/api/dossiers/"+id+"/documents").file(new MockMultipartFile("file","piece","image/png",file.getValue()))
                .param("kind",file.getKey().equals("png")?"IDENTITE":"DOMICILE").with(user("owner@example.test")).with(csrf())).andExpect(status().isCreated());
        }
        String png=db.queryForObject("SELECT id FROM document WHERE dossier_id=? AND kind='IDENTITE'",String.class,id);
        String jpg=db.queryForObject("SELECT id FROM document WHERE dossier_id=? AND kind='DOMICILE'",String.class,id);
        mvc.perform(get("/api/documents/"+png).with(user("owner@example.test")))
            .andExpect(header().string("Content-Disposition","attachment; filename=\"IDENTITE.png\""));
        mvc.perform(get("/api/documents/"+jpg).with(user("owner@example.test")))
            .andExpect(header().string("Content-Disposition","attachment; filename=\"DOMICILE.jpg\""));
    }
}
