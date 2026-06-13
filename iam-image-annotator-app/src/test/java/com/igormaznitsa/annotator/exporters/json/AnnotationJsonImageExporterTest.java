package com.igormaznitsa.annotator.exporters.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.igormaznitsa.annotator.api.json.AnnotationJsonCodec;
import com.igormaznitsa.annotator.api.model.AnnotationCoords;
import com.igormaznitsa.annotator.api.model.AnnotationDocument;
import com.igormaznitsa.annotator.api.model.AnnotationEntry;
import com.igormaznitsa.annotator.api.model.AnnotationType;
import com.igormaznitsa.annotator.api.service.EditorSession;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnnotationJsonImageExporterTest {

  @TempDir
  private Path tempDir;

  @Test
  void exportsOpenUnsavedAnnotationSession() throws IOException {
    final Path image = this.writePlainImage("source.png");
    final Path output = this.tempDir.resolve("json");
    final EditorSession session = EditorSession.fromImage(image, this.solidImage());
    session.document().add(new AnnotationEntry(
        "car",
        AnnotationType.RECTANGLE,
        "#00FF00",
        AnnotationCoords.rectangle(0.1, 0.2, 0.3, 0.4)));

    new AnnotationJsonImageExporter(
        path -> path.equals(image) ? Optional.of(session) : Optional.empty())
        .exportImages(List.of(image), output, ignored -> {
        });

    final AnnotationDocument restored =
        new AnnotationJsonCodec().decode(Files.readAllBytes(output.resolve("source.json")));
    assertEquals(1, restored.entries().size());
    assertEquals("car", restored.entries().getFirst().id());
  }

  @Test
  void skipsImagesWithoutAnnotationsInsteadOfWritingEmptyJson() throws IOException {
    final Path image = this.writePlainImage("plain.png");
    final Path output = this.tempDir.resolve("json");
    final EditorSession session = EditorSession.fromImage(image, this.solidImage());

    assertThrows(
        IOException.class,
        () -> new AnnotationJsonImageExporter(path -> Optional.of(session))
            .exportImages(List.of(image), output, ignored -> {
            }));

    assertFalse(Files.exists(output.resolve("plain.json")));
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
