package fr.hetic.mmotors;

import java.math.BigDecimal;
import java.util.Set;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;

public final class BusinessRules {
    private BusinessRules() {}
    public static final Map<String, BigDecimal> SERVICES = Map.of(
        "ASSURANCE", new BigDecimal("35.00"), "ASSISTANCE", new BigDecimal("8.00"),
        "ENTRETIEN", new BigDecimal("25.00"), "CONTROLE", new BigDecimal("5.00"));
    public static BigDecimal monthlyTotal(BigDecimal base, Set<String> options) {
        validateOptions("LOCATION", options);
        return options.stream().map(SERVICES::get).reduce(base, BigDecimal::add);
    }
    public static void validateOptions(String offer, Set<String> options) {
        if (!Set.of("ACHAT", "LOCATION").contains(offer) || !SERVICES.keySet().containsAll(options)
                || (offer.equals("ACHAT") && !options.isEmpty())) {
            throw new IllegalArgumentException("Les services sélectionnés ne correspondent pas à cette offre.");
        }
    }
    public static void requireComplete(Set<String> kinds) {
        if (!kinds.containsAll(Set.of("IDENTITE", "DOMICILE"))) {
            throw new IllegalArgumentException("Ajoutez une pièce d'identité et un justificatif de domicile.");
        }
    }
    public static void validateDecision(String current, String next, String reason) {
        if (!current.equals("SOUMIS") || !Set.of("VALIDE", "REFUSE").contains(next)) {
            throw new IllegalArgumentException("Seul un dossier soumis peut être validé ou refusé.");
        }
        if (next.equals("REFUSE") && reason.isBlank()) {
            throw new IllegalArgumentException("Expliquez le motif du refus.");
        }
    }
    public static String fileType(byte[] bytes) {
        try {
            if (bytes.length >= 5 && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
                try (var pdf=Loader.loadPDF(bytes)) {
                    if(pdf.isEncrypted() || pdf.getNumberOfPages()<1 || pdf.getNumberOfPages()>100) throw new IOException("PDF non accepté");
                    return "application/pdf";
                }
            }
            String type=null;
            if (bytes.length >= 3 && bytes[0] == (byte)255 && bytes[1] == (byte)216 && bytes[2] == (byte)255) type="image/jpeg";
            if (bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8), new byte[]{(byte)137,80,78,71,13,10,26,10})) type="image/png";
            if(type!=null) {
                try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                    var readers=ImageIO.getImageReaders(stream);
                    if(!readers.hasNext()) throw new IOException("Image illisible");
                    var reader=readers.next();
                    try {
                        reader.setInput(stream);
                        if((long)reader.getWidth(0)*reader.getHeight(0)>20_000_000) throw new IOException("Image trop grande");
                        if(reader.read(0)==null) throw new IOException("Image vide");
                        return type;
                    } finally { reader.dispose(); }
                }
            }
        } catch(IOException | RuntimeException error) {
            throw new IllegalArgumentException("Document illisible ou trop complexe. Utilisez un PDF non chiffré (100 pages maximum) ou une image lisible (20 mégapixels maximum).");
        }
        throw new IllegalArgumentException("Format non reconnu. Utilisez un PDF, JPEG ou PNG de 5 Mo maximum.");
    }
}
