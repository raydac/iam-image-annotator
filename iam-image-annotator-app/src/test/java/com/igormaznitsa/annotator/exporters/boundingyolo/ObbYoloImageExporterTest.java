package com.igormaznitsa.annotator.exporters.boundingyolo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.igormaznitsa.annotator.api.model.AnnotationCoords;
import com.igormaznitsa.annotator.api.model.AnnotationEntry;
import com.igormaznitsa.annotator.api.model.AnnotationType;
import com.igormaznitsa.annotator.api.model.NormPoint;
import com.igormaznitsa.annotator.api.service.EditorSession;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ObbYoloImageExporterTest {

  @TempDir
  private Path tempDir;

  @Test
  void exportsOpenUnsavedObbSessionAsDataset() throws IOException {
    final Path image = this.writePlainImage("source.png");
    final Path dataset = this.tempDir.resolve("dataset");
    final EditorSession session = EditorSession.fromImage(image, this.solidImage());
    session.document().add(this.obb("Plane.1", true));
    final boolean[] confirmed = {false};

    new ObbYoloImageExporter(3, classIds -> {
      confirmed[0] = true;
      assertEquals(Map.of("plane_1", 3), classIds);
      return true;
    }, path -> path.equals(image) ? Optional.of(session) : Optional.empty())
        .exportImages(List.of(image), dataset, ignored -> {
        });

    assertTrue(confirmed[0]);
    assertTrue(Files.exists(dataset.resolve("images").resolve("train").resolve("source.jpg")));
    assertEquals(
        List.of("3 0.100000 0.100000 0.500000 0.100000 0.500000 0.400000 0.100000 0.400000"),
        Files.readAllLines(dataset.resolve("labels").resolve("train").resolve("source.txt")));
    assertEquals(
        List.of(
            "path: .",
            "train: images/train",
            "val: images/val",
            "",
            "names:",
            "  3: plane_1"),
        Files.readAllLines(dataset.resolve("data.yaml")));
  }

  @Test
  void skipsImagesWithoutVisibleObbInsteadOfWritingEmptyLabels() throws IOException {
    final Path image = this.writePlainImage("rectangle.png");
    final Path dataset = this.tempDir.resolve("dataset");
    final EditorSession session = EditorSession.fromImage(image, this.solidImage());
    session.document().add(new AnnotationEntry(
        "box",
        AnnotationType.RECTANGLE,
        "#00FF00",
        AnnotationCoords.rectangle(0.1, 0.2, 0.3, 0.4)));

    assertThrows(
        IOException.class,
        () -> new ObbYoloImageExporter(0, ignored -> true, path -> Optional.of(session))
            .exportImages(List.of(image), dataset, ignored -> {
            }));

    assertFalse(Files.exists(dataset.resolve("labels").resolve("train").resolve("rectangle.txt")));
  }

  private AnnotationEntry obb(final String id, final boolean visible) {
    return new AnnotationEntry(
        id,
        AnnotationType.OBB,
        "#FF0000",
        AnnotationCoords.obb(List.of(
            NormPoint.of(0.1, 0.1),
            NormPoint.of(0.5, 0.1),
            NormPoint.of(0.5, 0.4),
            NormPoint.of(0.1, 0.4))),
        false,
        visible);
  }

  private Path writePlainImage(final String fileName) throws IOException {
    final Path image = this.tempDir.resolve(fileName);
    ImageIO.write(this.solidImage(), "png", image.toFile());
    return image;
  }

  private BufferedImage solidImage() {
    final BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
    final Graphics2D graphics = image.createGraphics();
    try {
      graphics.setColor(Color.BLUE);
      graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
    } finally {
      graphics.dispose();
    }
    return image;
  }
}
