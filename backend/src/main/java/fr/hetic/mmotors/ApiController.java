package fr.hetic.mmotors;

import static fr.hetic.mmotors.Models.DecisionInput;
import static fr.hetic.mmotors.Models.DossierInput;
import static fr.hetic.mmotors.Models.OfferInput;
import static fr.hetic.mmotors.Models.Registration;
import static fr.hetic.mmotors.Models.VehicleInput;

import jakarta.validation.Valid;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Couche HTTP : lit la requête, délègue au service, ne contient aucune règle métier. */
@RestController
@RequestMapping("/api")
class ApiController {
    private final MotorsService service;

    ApiController(MotorsService service) {
        this.service = service;
    }

    // --- Session et comptes ---

    @GetMapping("/csrf")
    Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    Object register(@Valid @RequestBody Registration input) {
        return service.register(input);
    }

    @GetMapping("/me")
    Object me(Authentication auth) {
        return service.me(auth.getName());
    }

    // --- Catalogue public ---

    @GetMapping("/services")
    Object services() {
        return BusinessRules.SERVICES;
    }

    @GetMapping("/vehicles")
    Object vehicles(@RequestParam(required = false) String offer,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) BigDecimal maxPrice) {
        return service.vehicles(offer, search, maxPrice, false);
    }

    @GetMapping("/vehicles/{id}")
    Object vehicle(@PathVariable String id) {
        return service.vehicle(id);
    }

    // --- Gestion du catalogue (admin) ---

    @GetMapping("/admin/vehicles")
    Object adminVehicles() {
        return service.vehicles(null, null, null, true);
    }

    @PostMapping("/admin/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    Object addVehicle(@Valid @RequestBody VehicleInput input) {
        return service.addVehicle(input);
    }

    @PutMapping("/admin/vehicles/{id}/offer")
    Object offer(@PathVariable String id, @Valid @RequestBody OfferInput input) {
        return service.switchOffer(id, input.offer());
    }

    // --- Dossiers du client ---

    @PostMapping("/dossiers")
    @ResponseStatus(HttpStatus.CREATED)
    Object create(@Valid @RequestBody DossierInput input, Authentication auth) {
        return service.createDossier(auth.getName(), input);
    }

    @GetMapping("/dossiers")
    Object dossiers(Authentication auth) {
        return service.dossiers(auth, false);
    }

    @GetMapping("/dossiers/{id}")
    Object dossier(@PathVariable String id, Authentication auth) {
        return service.dossier(id, auth);
    }

    @PostMapping("/dossiers/{id}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    Object upload(@PathVariable String id, @RequestParam String kind,
            @RequestParam MultipartFile file, Authentication auth) throws IOException {
        return service.upload(id, auth, kind, file.getBytes());
    }

    @PostMapping("/dossiers/{id}/submit")
    Object submit(@PathVariable String id, Authentication auth) {
        return service.submit(id, auth);
    }

    @GetMapping("/documents/{id}")
    ResponseEntity<byte[]> document(@PathVariable String id, Authentication auth) {
        var doc = service.document(id, auth);
        String contentType = (String) doc.get("contentType");
        String filename = doc.get("kind") + extensionOf(contentType);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .cacheControl(CacheControl.noStore())
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body((byte[]) doc.get("content"));
    }

    // --- Instruction des dossiers (admin) ---

    @GetMapping("/admin/dossiers")
    Object adminDossiers(Authentication auth) {
        return service.dossiers(auth, true);
    }

    @PostMapping("/admin/dossiers/{id}/decision")
    Object decision(@PathVariable String id, @Valid @RequestBody DecisionInput input) {
        return service.decide(id, input);
    }

    /** L'extension vient du type réellement détecté à l'envoi, jamais du nom fourni par le client. */
    private static String extensionOf(String contentType) {
        return switch (contentType) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            default -> ".jpg";
        };
    }
}
