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
}
