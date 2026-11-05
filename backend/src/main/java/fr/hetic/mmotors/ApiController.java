package fr.hetic.mmotors;

import static fr.hetic.mmotors.Models.*;
import jakarta.validation.Valid;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api")
class ApiController {
    private final MotorsService service;
    ApiController(MotorsService service) { this.service=service; }
    @GetMapping("/csrf") Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken(),"headerName",token.getHeaderName()); }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    Object register(@Valid @RequestBody Registration input) { return service.register(input); }
    @GetMapping("/me") Object me(Authentication auth) { return service.me(auth.getName()); }
    @GetMapping("/services") Object services() { return BusinessRules.SERVICES; }
    @GetMapping("/vehicles") Object vehicles(@RequestParam(required=false) String offer,@RequestParam(required=false) String search,
        @RequestParam(required=false) BigDecimal maxPrice) { return service.vehicles(offer,search,maxPrice,false); }
    @GetMapping("/vehicles/{id}") Object vehicle(@PathVariable String id) { return service.vehicle(id); }
    @GetMapping("/admin/vehicles") Object adminVehicles() { return service.vehicles(null,null,null,true); }
    @PostMapping("/admin/vehicles") @ResponseStatus(HttpStatus.CREATED)
    Object addVehicle(@Valid @RequestBody VehicleInput input) { return service.addVehicle(input); }
    @PutMapping("/admin/vehicles/{id}/offer") Object offer(@PathVariable String id,@Valid @RequestBody OfferInput input) { return service.switchOffer(id,input.offer()); }
    @PostMapping("/dossiers") @ResponseStatus(HttpStatus.CREATED)
    Object create(@Valid @RequestBody DossierInput input,Authentication auth) { return service.createDossier(auth.getName(),input); }
    @GetMapping("/dossiers") Object dossiers(Authentication auth) { return service.dossiers(auth,false); }
    @GetMapping("/admin/dossiers") Object adminDossiers(Authentication auth) { return service.dossiers(auth,true); }
    @GetMapping("/dossiers/{id}") Object dossier(@PathVariable String id,Authentication auth) { return service.dossier(id,auth); }
    @PostMapping("/dossiers/{id}/documents") @ResponseStatus(HttpStatus.CREATED)
    Object upload(@PathVariable String id,@RequestParam String kind,@RequestParam MultipartFile file,Authentication auth) throws IOException { return service.upload(id,auth,kind,file.getBytes()); }
    @GetMapping("/documents/{id}") ResponseEntity<byte[]> document(@PathVariable String id,Authentication auth) {
        var doc=service.document(id,auth);
        String ext=switch((String)doc.get("contentType")) { case "application/pdf" -> ".pdf"; case "image/png" -> ".png"; default -> ".jpg"; };
        return ResponseEntity.ok().contentType(MediaType.parseMediaType((String)doc.get("contentType")))
            .cacheControl(CacheControl.noStore()).header("Content-Disposition","attachment; filename=\""+doc.get("kind")+ext+"\"")
            .body((byte[])doc.get("content"));
    }
    @PostMapping("/dossiers/{id}/submit") Object submit(@PathVariable String id,Authentication auth) { return service.submit(id,auth); }
    @PostMapping("/admin/dossiers/{id}/decision") Object decision(@PathVariable String id,@Valid @RequestBody DecisionInput input) { return service.decide(id,input); }
}
