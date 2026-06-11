package com.igormaznitsa.annotator.api.png;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.igormaznitsa.annotator.api.model.AnnotationCoords;
import com.igormaznitsa.annotator.api.model.AnnotationDocument;
import com.igormaznitsa.annotator.api.model.AnnotationEntry;
import com.igormaznitsa.annotator.api.model.AnnotationType;
import com.igormaznitsa.annotator.api.render.AnnotationOverlayRenderer;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class AnnotatedPngTest {

  private static byte[] encodeWithDefaultPngWriter(final BufferedImage image) throws IOException {
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ImageIO.write(image, "png", bytes);
    return bytes.toByteArray();
  }

  private static BufferedImage createNoisyImage(final int width, final int height) {
    final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        image.setRGB(x, y, new Color(
            (x * 53 + y * 97) & 0xFF,
            (x * 149 + y * 31) & 0xFF,
            (x * 17 + y * 211) & 0xFF).getRGB());
      }
    }
    return image;
  }

  @Test
  void roundTripsWhenBaseAndComposedPngFormatsDiffer() throws IOException {
    final BufferedImage base = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
    final AnnotationDocument document = new AnnotationDocument();
    document.add(new AnnotationEntry(
        "box",
        AnnotationType.RECTANGLE,
        "#ff0000",
        AnnotationCoords.rectangle(0.1, 0.1, 0.4, 0.3)));

    final ByteArrayOutputStream written = new ByteArrayOutputStream();
    new AnnotatedPng(base, document).write(written);

    final AnnotatedPng read = AnnotatedPng.read(new ByteArrayInputStream(written.toByteArray()));
    assertNotNull(read.baseImage());
    assertEquals(base.getWidth(), read.baseImage().getWidth());
    assertEquals(base.getHeight(), read.baseImage().getHeight());
    assertEquals(1, read.document().entries().size());
  }

  @Test
  void detectsAnnotationChunksWithoutFullDecode() throws IOException {
    final BufferedImage base = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
    final ByteArrayOutputStream plain = new ByteArrayOutputStream();
    ImageIO.write(base, "png", plain);

    assertFalse(AnnotatedPng.hasAnnotationChunks(
        new ByteArrayInputStream(plain.toByteArray())));

    final AnnotationDocument document = new AnnotationDocument();
    document.add(new AnnotationEntry(
        "box",
        AnnotationType.RECTANGLE,
        "#ff0000",
        AnnotationCoords.rectangle(0.1, 0.1, 0.4, 0.3)));
    final ByteArrayOutputStream annotated = new ByteArrayOutputStream();
    new AnnotatedPng(base, document).write(annotated);

    assertTrue(AnnotatedPng.hasAnnotationChunks(
        new ByteArrayInputStream(annotated.toByteArray())));
  }

  @Test
  void readRestoresBaseImageNotComposedDisplayRaster() throws IOException {
    final BufferedImage base = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
    final Graphics2D graphics = base.createGraphics();
    graphics.setColor(Color.BLUE);
    graphics.fillRect(0, 0, 64, 48);
    graphics.dispose();

    final AnnotationDocument document = new AnnotationDocument();
    document.add(new AnnotationEntry(
        "box",
        AnnotationType.RECTANGLE,
        "#ff0000",
        AnnotationCoords.rectangle(0.25, 0.25, 0.5, 0.5)));

    final ByteArrayOutputStream written = new ByteArrayOutputStream();
    new AnnotatedPng(base, document).write(written);
    final byte[] fileBytes = written.toByteArray();

    final PngChunkIO chunkIo = new PngChunkIO();
    final List<PngChunk> chunks = chunkIo.readAll(new ByteArrayInputStream(fileBytes));
    assertTrue(chunks.stream().anyMatch(chunk -> PngConstants.isIbseChunk(chunk.type())));
    assertTrue(chunks.stream().anyMatch(chunk -> PngConstants.isIannChunk(chunk.type())));

    final BufferedImage display = ImageIO.read(chunkIo.toInputStream(chunkIo.toDisplayPng(chunks)));
    assertNotNull(display);

    final AnnotatedPng read = AnnotatedPng.read(new ByteArrayInputStream(fileBytes));
    final int centerX = 32;
    final int centerY = 24;
    final int baseArgb = read.baseImage().getRGB(centerX, centerY);
    final int displayArgb = display.getRGB(centerX, centerY);

    assertEquals(Color.BLUE.getRGB(), baseArgb);
    assertNotEquals(baseArgb, displayArgb);
  }

  @Test
  void optimizerReducesPreviewPngSizeOnlyInsideFilledAnnotations() throws IOException {
    final BufferedImage base = createNoisyImage(128, 128);
    final AnnotationDocument document = new AnnotationDocument();
    document.add(new AnnotationEntry(
        "box",
        AnnotationType.RECTANGLE,
        "#ff0000",
        AnnotationCoords.rectangle(0.0, 0.0, 0.5, 1.0)));
    final PngDisplayRasterOptimizer optimizer = new PngDisplayRasterOptimizer();
    final AnnotationOverlayRenderer renderer = new AnnotationOverlayRenderer();

    final BufferedImage optimizedBase = optimizer.optimizeBaseForFilledAnnotations(base, document);
    final BufferedImage regularDisplay = renderer.compose(base, document);
    final BufferedImage optimizedDisplay = renderer.compose(optimizedBase, document);

    assertEquals(base.getRGB(96, 64), optimizedBase.getRGB(96, 64));
    assertNotEquals(base.getRGB(32, 64), optimizedBase.getRGB(32, 64));
    assertTrue(encodeWithDefaultPngWriter(optimizedDisplay).length
        < encodeWithDefaultPngWriter(regularDisplay).length);
  }

  private static boolean isLightGray(final int argb) {
    final Color color = new Color(argb, true);
    return color.getRed() > 150
        && color.getGreen() > 150
        && color.getBlue() > 150
        && Math.abs(color.getRed() - color.getGreen()) < 12
        && Math.abs(color.getGreen() - color.getBlue()) < 12;
  }

  @Test
  void saveRendersHiddenAnnotationsAsGrayOutlinesInDisplayRaster() throws IOException {
    final BufferedImage base = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
    final Graphics2D graphics = base.createGraphics();
    graphics.setColor(Color.BLUE);
    graphics.fillRect(0, 0, 64, 48);
    graphics.dispose();

    final AnnotationDocument document = new AnnotationDocument();
    document.add(new AnnotationEntry(
        "box",
        AnnotationType.RECTANGLE,
        "#ff0000",
        AnnotationCoords.rectangle(0.25, 0.25, 0.5, 0.5),
        false,
        false));

    final ByteArrayOutputStream written = new ByteArrayOutputStream();
    new AnnotatedPng(base, document).write(written);
    final PngChunkIO chunkIo = new PngChunkIO();
    final List<PngChunk> chunks =
        chunkIo.readAll(new ByteArrayInputStream(written.toByteArray()));
    final BufferedImage display = ImageIO.read(chunkIo.toInputStream(chunkIo.toDisplayPng(chunks)));

    assertEquals(Color.BLUE.getRGB(), display.getRGB(32, 24));
    assertTrue(isLightGray(display.getRGB(16, 24)));
  }

  @Test
  void rejectsCorruptedIbseChunk() throws IOException {
    final BufferedImage base = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
    final AnnotationDocument document = new AnnotationDocument();
    document.add(new AnnotationEntry(
        "box",
        AnnotationType.RECTANGLE,
        "#ff0000",
        AnnotationCoords.rectangle(0.1, 0.1, 0.4, 0.3)));

    final ByteArrayOutputStream written = new ByteArrayOutputStream();
    new AnnotatedPng(base, document).write(written);
    final byte[] fileBytes = written.toByteArray();

    final PngChunkIO chunkIo = new PngChunkIO();
    final List<PngChunk> chunks = chunkIo.readAll(new ByteArrayInputStream(fileBytes));
    final List<PngChunk> corrupted = chunks.stream()
        .map(chunk -> PngConstants.isIbseChunk(chunk.type())
            ? new PngChunk(chunk.type(), new byte[] {0, 1, 2, 3})
            : chunk)
        .toList();
    final byte[] corruptedFile = chunkIo.writeAll(corrupted);

    assertThrows(IOException.class,
        () -> AnnotatedPng.read(new ByteArrayInputStream(corruptedFile)));
  }
}
