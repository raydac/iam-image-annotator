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
import java.util.Map;
import java.util.concurrent.CancellationException;
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
    final boolean[] confirmed = {false};

    new BoundingYoloImageExporter(5, classIds -> {
      confirmed[0] = true;
      assertEquals(Map.of("vehicle", 5), classIds);
      return true;
    }).exportImages(List.of(plain, annotated), dataset, ignored -> {
    });

    assertTrue(confirmed[0]);
    assertFalse(Files.exists(dataset.resolve("images").resolve("train").resolve("plain.jpg")));
    assertFalse(Files.exists(dataset.resolve("images").resolve("train").resolve("annotated.png")));
    assertTrue(Files.exists(dataset.resolve("images").resolve("train").resolve("annotated.jpg")));
    assertEquals(
        List.of("5 0.500000 0.500000 0.500000 0.500000"),
        Files.readAllLines(dataset.resolve("labels").resolve("train").resolve("annotated.txt")));
    assertEquals(
        List.of(
            "path: .",
            "train: images/train",
            "val: images/val",
            "",
            "names:",
            "  5: vehicle"),
        Files.readAllLines(dataset.resolve("data.yaml")));
    final BufferedImage exported =
        ImageIO.read(dataset.resolve("images").resolve("train").resolve("annotated.jpg").toFile());
    assertEquals(32, exported.getWidth());
    assertEquals(32, exported.getHeight());
  }

  @Test
  void cancelingClassConfirmationStopsExport() throws IOException {
    final Path annotated = this.tempDir.resolve("cancel.png");
    final Path dataset = this.tempDir.resolve("cancel-dataset");
    this.writeAnnotatedImage(annotated);

    org.junit.jupiter.api.Assertions.assertThrows(
        CancellationException.class,
        () -> new BoundingYoloImageExporter(0, ignored -> false)
            .exportImages(List.of(annotated), dataset, ignored -> {
            }));

    assertFalse(Files.exists(dataset.resolve("data.yaml")));
    assertFalse(Files.exists(dataset.resolve("images")));
    assertFalse(Files.exists(dataset.resolve("labels")));
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
