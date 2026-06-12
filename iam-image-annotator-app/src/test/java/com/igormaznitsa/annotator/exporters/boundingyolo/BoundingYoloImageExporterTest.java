package com.igormaznitsa.annotator.exporters.boundingyolo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.igormaznitsa.annotator.api.model.AnnotationCoords;
import com.igormaznitsa.annotator.api.model.AnnotationDocument;
import com.igormaznitsa.annotator.api.model.AnnotationEntry;
import com.igormaznitsa.annotator.api.model.AnnotationType;
import com.igormaznitsa.annotator.api.png.AnnotatedPng;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BoundingYoloImageExporterTest {

  @TempDir
  private Path tempDir;

  @Test
  void exportsOnlyAnnotatedImagesWithBoundingLabels() throws IOException {
    final Path plain = this.tempDir.resolve("plain.png");
    final Path annotated = this.tempDir.resolve("annotated.png");
    final Path dataset = this.tempDir.resolve("dataset");
    ImageIO.write(this.solidImage(Color.GREEN), "png", plain.toFile());
    this.writeAnnotatedImage(annotated);

    new BoundingYoloImageExporter(5).exportImages(List.of(plain, annotated), dataset, ignored -> {
    });

    assertFalse(Files.exists(dataset.resolve("train").resolve("images").resolve("plain.jpg")));
    assertFalse(Files.exists(dataset.resolve("train").resolve("images").resolve("annotated.png")));
    assertTrue(Files.exists(dataset.resolve("train").resolve("images").resolve("annotated.jpg")));
    assertEquals(
        List.of("5 0.500000 0.500000 0.500000 0.500000"),
        Files.readAllLines(dataset.resolve("train").resolve("labels").resolve("annotated.txt")));
    final BufferedImage exported =
        ImageIO.read(dataset.resolve("train").resolve("images").resolve("annotated.jpg").toFile());
    assertEquals(32, exported.getWidth());
    assertEquals(32, exported.getHeight());
  }

  private void writeAnnotatedImage(final Path path) throws IOException {
    final AnnotationDocument document = new AnnotationDocument();
    document.add(new AnnotationEntry(
        "vehicle",
        AnnotationType.RECTANGLE,
        "#ff0000",
        AnnotationCoords.rectangle(0.25, 0.25, 0.5, 0.5)));
    try (OutputStream output = Files.newOutputStream(path)) {
      new AnnotatedPng(this.solidImage(Color.BLUE), document).write(output);
    }
  }

  private BufferedImage solidImage(final Color color) {
    final BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
    final Graphics2D graphics = image.createGraphics();
    try {
      graphics.setColor(color);
      graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
    } finally {
      graphics.dispose();
    }
    return image;
  }
}
