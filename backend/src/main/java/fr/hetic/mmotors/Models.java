package fr.hetic.mmotors;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Set;

final class Models {
    private Models() {}
    record Registration(@NotBlank @Email @Size(max=254) String email,
        @NotBlank @Size(min=12,max=64) String password, @NotBlank @Size(max=100) String name) {}
    record VehicleInput(@NotBlank @Size(max=60) String brand, @NotBlank @Size(max=100) String model,
        @Min(1950) @Max(2100) int year, @Min(0) int mileage,
        @NotBlank @Size(max=30) String fuel, @NotBlank @Size(max=30) String transmission,
        @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal price,
        @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal monthly,
        @NotNull @Pattern(regexp="ACHAT|LOCATION") String offer) {}
    record DossierInput(@NotBlank @Size(max=36) String vehicleId, @NotNull @Size(max=4) Set<@NotNull String> options) {}
    record OfferInput(@NotNull @Pattern(regexp="ACHAT|LOCATION") String offer) {}
    record DecisionInput(@NotNull @Pattern(regexp="VALIDE|REFUSE") String status, @NotNull @Size(max=1000) String reason) {}
}
