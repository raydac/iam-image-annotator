package com.igormaznitsa.annotator.exporters.common;

import java.nio.file.Path;
import java.util.Map;

public final class ExporterFileNames {

  private ExporterFileNames() {
  }

  public static String replaceExtension(final Path imagePath, final String extension) {
    final String fileName = imagePath.getFileName().toString();
    final int dot = fileName.lastIndexOf('.');
    return dot > 0 ? fileName.substring(0, dot) + extension : fileName + extension;
  }

  public static String uniqueFileName(
      final String fileName,
      final Map<String, Integer> fileNameCounts) {
    final int count = fileNameCounts.merge(fileName, 1, Integer::sum);
    if (count == 1) {
      return fileName;
    }
    final int dot = fileName.lastIndexOf('.');
    return dot > 0
        ? fileName.substring(0, dot) + '_' + count + fileName.substring(dot)
        : fileName + '_' + count;
  }
}
