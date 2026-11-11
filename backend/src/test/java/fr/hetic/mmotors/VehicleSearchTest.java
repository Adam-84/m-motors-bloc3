package fr.hetic.mmotors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Recherche publique du catalogue : les espaces autour du terme ne doivent pas changer le résultat. */
@SpringBootTest
@AutoConfigureMockMvc
class VehicleSearchTest {
    @Autowired
    MockMvc mvc;

    @Test
    void searchIgnoresTrailingSpaces() throws Exception {
        // « Diesel » est le dernier mot de la fiche : un espace saisi après lui ne doit pas masquer le véhicule.
        mvc.perform(get("/api/vehicles").param("search", "diesel "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].model").value("C3 Aircross"));
    }

    @Test
    void searchIgnoresLeadingSpaces() throws Exception {
        mvc.perform(get("/api/vehicles").param("search", " renault"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
