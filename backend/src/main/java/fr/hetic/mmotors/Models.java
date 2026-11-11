package fr.hetic.mmotors;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Set;

/** Corps des requêtes JSON, validés par Bean Validation avant d'atteindre le service. */
final class Models {
    private static final String OFFER = "ACHAT|LOCATION";

    private Models() {}

    record Registration(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 64) String password,
            @NotBlank @Size(max = 100) String name) {}

    record VehicleInput(
            @NotBlank @Size(max = 60) String brand,
            @NotBlank @Size(max = 100) String model,
            @Min(1950) @Max(2100) int year,
            @Min(0) int mileage,
            @NotBlank @Size(max = 30) String fuel,
            @NotBlank @Size(max = 30) String transmission,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal monthly,
            @NotNull @Pattern(regexp = OFFER) String offer) {}

    record DossierInput(
            @NotBlank @Size(max = 36) String vehicleId,
            @NotNull @Size(max = 4) Set<@NotNull String> options) {}

    record OfferInput(@NotNull @Pattern(regexp = OFFER) String offer) {}

    record DecisionInput(
            @NotNull @Pattern(regexp = "VALIDE|REFUSE") String status,
            @NotNull @Size(max = 1000) String reason) {}
}
