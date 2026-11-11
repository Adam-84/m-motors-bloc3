package fr.hetic.mmotors;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import org.apache.pdfbox.Loader;

/** Règles métier pures : tarifs, cohérence des options, décisions et validation des pièces. */
public final class BusinessRules {

    public static final Map<String, BigDecimal> SERVICES = Map.of(
        "ASSURANCE", new BigDecimal("35.00"),
        "ASSISTANCE", new BigDecimal("8.00"),
        "ENTRETIEN", new BigDecimal("25.00"),
        "CONTROLE", new BigDecimal("5.00"));

    private static final Set<String> OFFERS = Set.of("ACHAT", "LOCATION");
    private static final Set<String> REQUIRED_DOCUMENTS = Set.of("IDENTITE", "DOMICILE");

    private static final String PDF_SIGNATURE = "%PDF-";
    private static final int PDF_MAX_PAGES = 100;
    private static final long IMAGE_MAX_PIXELS = 20_000_000L;
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private BusinessRules() {
    }

    public static BigDecimal monthlyTotal(BigDecimal base, Set<String> options) {
        validateOptions("LOCATION", options);
        return options.stream().map(SERVICES::get).reduce(base, BigDecimal::add);
    }

    public static void validateOptions(String offer, Set<String> options) {
        boolean unknownOffer = !OFFERS.contains(offer);
        boolean unknownService = !SERVICES.keySet().containsAll(options);
        boolean servicesOnPurchase = offer.equals("ACHAT") && !options.isEmpty();
        if (unknownOffer || unknownService || servicesOnPurchase) {
            throw new IllegalArgumentException("Les services sélectionnés ne correspondent pas à cette offre.");
        }
    }

    public static void requireComplete(Set<String> kinds) {
        if (!kinds.containsAll(REQUIRED_DOCUMENTS)) {
            throw new IllegalArgumentException("Ajoutez une pièce d'identité et un justificatif de domicile.");
        }
    }

    public static void validateDecision(String current, String next, String reason) {
        boolean alreadyDecided = !current.equals("SOUMIS");
        boolean unknownDecision = !Set.of("VALIDE", "REFUSE").contains(next);
        if (alreadyDecided || unknownDecision) {
            throw new IllegalArgumentException("Seul un dossier soumis peut être validé ou refusé.");
        }
        if (next.equals("REFUSE") && reason.isBlank()) {
            throw new IllegalArgumentException("Expliquez le motif du refus.");
        }
    }

    /**
     * Détermine le type réel d'une pièce à partir de son contenu, jamais de son nom ni de son en-tête HTTP.
     * La signature seule ne suffit pas : le fichier est ensuite réellement lu (PDFBox ou ImageIO).
     */
    public static String fileType(byte[] bytes) {
        try {
            if (startsWith(bytes, PDF_SIGNATURE.getBytes(StandardCharsets.US_ASCII))) {
                checkPdf(bytes);
                return "application/pdf";
            }
            if (startsWith(bytes, JPEG_SIGNATURE)) {
                checkImage(bytes);
                return "image/jpeg";
            }
            if (startsWith(bytes, PNG_SIGNATURE)) {
                checkImage(bytes);
                return "image/png";
            }
        } catch (IOException | RuntimeException error) {
            throw new IllegalArgumentException(
                "Document illisible ou trop complexe. Utilisez un PDF non chiffré (100 pages maximum) "
                    + "ou une image lisible (20 mégapixels maximum).");
        }
        throw new IllegalArgumentException("Format non reconnu. Utilisez un PDF, JPEG ou PNG de 5 Mo maximum.");
    }

    private static boolean startsWith(byte[] bytes, byte[] signature) {
        return bytes.length >= signature.length
            && Arrays.equals(Arrays.copyOf(bytes, signature.length), signature);
    }

    private static void checkPdf(byte[] bytes) throws IOException {
        try (var pdf = Loader.loadPDF(bytes)) {
            int pages = pdf.getNumberOfPages();
            if (pdf.isEncrypted() || pages < 1 || pages > PDF_MAX_PAGES) {
                throw new IOException("PDF non accepté");
            }
        }
    }

    private static void checkImage(byte[] bytes) throws IOException {
        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new IOException("Image illisible");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > IMAGE_MAX_PIXELS) {
                    throw new IOException("Image trop grande");
                }
                if (reader.read(0) == null) {
                    throw new IOException("Image vide");
                }
            } finally {
                reader.dispose();
            }
        }
    }
}
