package com.igormaznitsa.annotator.exporters.boundingyolo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

class SegmentYoloImageExporterTest {

  @TempDir
  private Path tempDir;

  @Test
  void exportsPolygonRowsInUltralyticsSegmentationFormat() throws IOException {
    final Path image = this.writePlainImage("mask.png");
    final Path dataset = this.tempDir.resolve("dataset");
    final EditorSession session = EditorSession.fromImage(image, this.solidImage());
    session.document().add(new AnnotationEntry(
        "Wing-Part",
        AnnotationType.POLYGON,
        "#00FF00",
        AnnotationCoords.polygon(List.of(
            NormPoint.of(0.1, 0.2),
            NormPoint.of(0.4, 0.2),
            NormPoint.of(0.4, 0.6),
            NormPoint.of(0.1, 0.6)))));

    new SegmentYoloImageExporter(7, classIds -> {
      assertEquals(Map.of("wing_part", 7), classIds);
      return true;
    }, path -> path.equals(image) ? Optional.of(session) : Optional.empty())
        .exportImages(List.of(image), dataset, ignored -> {
        });

    assertEquals(
        List.of("7 0.100000 0.200000 0.400000 0.200000 0.400000 0.600000 0.100000 0.600000"),
        Files.readAllLines(dataset.resolve("labels").resolve("train").resolve("mask.txt")));
    assertFalse(Files.exists(dataset.resolve("images").resolve("train").resolve("mask.png")));
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
