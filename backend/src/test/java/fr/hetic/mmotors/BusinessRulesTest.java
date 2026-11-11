package fr.hetic.mmotors;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BusinessRulesTest {
    @Test void rejectsTruncatedPdfAndImagesEvenWithCorrectSignature() {
        for (byte[] bytes : new byte[][] { "%PDF-".getBytes(), new byte[]{(byte)255,(byte)216,(byte)255}, new byte[]{(byte)137,80,78,71,13,10,26,10} }) {
            assertThatThrownBy(() -> BusinessRules.fileType(bytes)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void sumsOnlySelectedRentalServices() {
        assertThat(BusinessRules.monthlyTotal(new BigDecimal("250.00"), Set.of("ASSURANCE", "ENTRETIEN")))
            .isEqualByComparingTo("310.00");
    }
    @Test void rejectsUnknownService() {
        assertThatThrownBy(() -> BusinessRules.validateOptions("LOCATION", Set.of("INCONNU")))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsRentalServicesForPurchase() {
        assertThatThrownBy(() -> BusinessRules.validateOptions("ACHAT", Set.of("ASSURANCE")))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void acceptsPurchaseWithoutService() {
        assertThatCode(() -> BusinessRules.validateOptions("ACHAT", Set.of())).doesNotThrowAnyException();
    }
    @Test void requiresBothKindsOfDocuments() {
        assertThatThrownBy(() -> BusinessRules.requireComplete(Set.of("IDENTITE")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> BusinessRules.requireComplete(Set.of("IDENTITE", "DOMICILE"))).doesNotThrowAnyException();
    }
    @Test void refusesDecisionOnDraft() {
        assertThatThrownBy(() -> BusinessRules.validateDecision("BROUILLON", "VALIDE", ""))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void refusalMustBeExplained() {
        assertThatThrownBy(() -> BusinessRules.validateDecision("SOUMIS", "REFUSE", " "))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> BusinessRules.validateDecision("SOUMIS", "REFUSE", "Pièce illisible"))
            .doesNotThrowAnyException();
    }
    @Test void approvesOnlySubmittedDossier() {
        assertThatCode(() -> BusinessRules.validateDecision("SOUMIS", "VALIDE", "")).doesNotThrowAnyException();
        assertThatThrownBy(() -> BusinessRules.validateDecision("SOUMIS", "BROUILLON", ""))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void checksRealFileSignatureRatherThanClientContentType() {
        assertThat(BusinessRules.fileType(TestFiles.pdf())).isEqualTo("application/pdf");
        assertThat(BusinessRules.fileType(TestFiles.image("jpeg"))).isEqualTo("image/jpeg");
        assertThat(BusinessRules.fileType(TestFiles.image("png"))).isEqualTo("image/png");
        assertThatThrownBy(() -> BusinessRules.fileType("<script>alert(1)</script>".getBytes()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BusinessRules.fileType(new byte[0])).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsEncryptedPdf() {
        assertThatThrownBy(() -> BusinessRules.fileType(TestFiles.encryptedPdf())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsPdfWithoutPage() {
        assertThatThrownBy(() -> BusinessRules.fileType(TestFiles.pdfWithPages(0))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsPdfWithTooManyPages() {
        assertThatThrownBy(() -> BusinessRules.fileType(TestFiles.pdfWithPages(101))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void acceptsPdfAtThePageLimit() {
        assertThat(BusinessRules.fileType(TestFiles.pdfWithPages(100))).isEqualTo("application/pdf");
    }
    @Test void rejectsImageThatNoReaderCanDecode() {
        byte[] jpegSignatureOnly = new byte[64];
        jpegSignatureOnly[0] = (byte) 0xFF; jpegSignatureOnly[1] = (byte) 0xD8; jpegSignatureOnly[2] = (byte) 0xFF;
        assertThatThrownBy(() -> BusinessRules.fileType(jpegSignatureOnly)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsImageAboveTwentyMegapixels() {
        assertThatThrownBy(() -> BusinessRules.fileType(TestFiles.imageOfSize(5000, 4001))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void acceptsImageAtTheMegapixelLimit() {
        assertThat(BusinessRules.fileType(TestFiles.imageOfSize(5000, 4000))).isEqualTo("image/png");
    }
    @Test void rejectsUnknownOfferEvenWithoutServices() {
        assertThatThrownBy(() -> BusinessRules.validateOptions("VENTE", Set.of())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void acceptsRentalWithKnownServices() {
        assertThatCode(() -> BusinessRules.validateOptions("LOCATION", Set.of("ASSURANCE", "CONTROLE"))).doesNotThrowAnyException();
    }
}
