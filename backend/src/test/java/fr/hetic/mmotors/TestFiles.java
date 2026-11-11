package fr.hetic.mmotors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

final class TestFiles {
    static byte[] pdf() {
        try(var pdf=new PDDocument(); var output=new ByteArrayOutputStream()) {
            pdf.addPage(new PDPage()); pdf.save(output); return output.toByteArray();
        } catch(IOException e) { throw new IllegalStateException(e); }
    }
    static byte[] image(String format) {
        try(var output=new ByteArrayOutputStream()) {
            ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),format,output); return output.toByteArray();
        } catch(IOException e) { throw new IllegalStateException(e); }
    }
    static byte[] pdfWithPages(int pages) {
        try(var pdf=new PDDocument(); var output=new ByteArrayOutputStream()) {
            for (int n=0;n<pages;n++) pdf.addPage(new PDPage());
            pdf.save(output); return output.toByteArray();
        } catch(IOException e) { throw new IllegalStateException(e); }
    }
    static byte[] encryptedPdf() {
        try(var pdf=new PDDocument(); var output=new ByteArrayOutputStream()) {
            pdf.addPage(new PDPage());
            var policy=new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy("owner","user",new org.apache.pdfbox.pdmodel.encryption.AccessPermission());
            policy.setEncryptionKeyLength(128);
            pdf.protect(policy); pdf.save(output); return output.toByteArray();
        } catch(IOException e) { throw new IllegalStateException(e); }
    }
    static byte[] imageOfSize(int width, int height) {
        try(var output=new ByteArrayOutputStream()) {
            ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_BYTE_BINARY),"png",output); return output.toByteArray();
        } catch(IOException e) { throw new IllegalStateException(e); }
    }
}
