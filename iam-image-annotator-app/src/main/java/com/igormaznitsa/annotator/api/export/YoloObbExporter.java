package com.igormaznitsa.annotator.api.export;

import com.igormaznitsa.annotator.api.model.AnnotationEntry;
import com.igormaznitsa.annotator.api.model.AnnotationType;
import com.igormaznitsa.annotator.api.model.ObbCorners;
import com.igormaznitsa.annotator.api.service.EditorSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Exports OBB labels in Ultralytics YOLO OBB text format
 * ({@code class_index x1 y1 x2 y2 x3 y3 x4 y4}).
 */
public final class YoloObbExporter {

  public static String labelFileNameFor(final Path imagePath) {
    final String fileName = imagePath.getFileName().toString();
    final int dot = fileName.lastIndexOf('.');
    if (dot > 0) {
      return fileName.substring(0, dot) + ".txt";
    }
    return fileName + ".txt";
  }

  public void exportLabelFile(final EditorSession session, final Path targetTxt)
      throws IOException {
    final List<String> lines = this.formatLines(session);
    Files.createDirectories(targetTxt.getParent());
    Files.write(targetTxt, lines, StandardCharsets.UTF_8);
  }

  public List<String> formatLines(final EditorSession session) {
    final Map<String, Integer> classIndices =
        ObbCorners.classIndexMap(session.document().entries());
    final List<String> lines = new ArrayList<>();
    for (final AnnotationEntry entry : session.document().entries()) {
      if (entry.type() != AnnotationType.OBB) {
        continue;
      }
      final int classIndex = classIndices.get(entry.id());
      lines.add(ObbCorners.toYoloLine(classIndex, entry.coords().corners()));
    }
    return lines;
  }
}
