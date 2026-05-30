package com.igormaznitsa.annotator.api.png;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.igormaznitsa.annotator.api.model.AnnotationCoords;
import com.igormaznitsa.annotator.api.model.AnnotationDocument;
import com.igormaznitsa.annotator.api.model.AnnotationEntry;
import com.igormaznitsa.annotator.api.model.AnnotationType;
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
