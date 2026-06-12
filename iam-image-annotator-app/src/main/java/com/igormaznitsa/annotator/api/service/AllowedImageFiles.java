package com.igormaznitsa.annotator.api.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class AllowedImageFiles {

  private static final String PNG_SUFFIX = ".png";
  private static final String JPG_SUFFIX = ".jpg";
  private static final String JPEG_SUFFIX = ".jpeg";

  private AllowedImageFiles() {
  }

  public static boolean isAllowed(final Path path) {
    if (!Files.isRegularFile(path)) {
      return false;
    }
    final String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return suffixOf(lower)
        .map(suffix -> !lower.substring(0, lower.length() - suffix.length()).contains("."))
        .orElse(false);
  }

  public static boolean isPng(final Path path) {
    return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(PNG_SUFFIX);
  }

  public static Path toAnnotatedPngPath(final Path source) {
    final String fileName = source.getFileName().toString();
    final int dot = fileName.lastIndexOf('.');
    final String pngFileName = (dot > 0 ? fileName.substring(0, dot) : fileName) + PNG_SUFFIX;
    return source.resolveSibling(pngFileName);
  }

  public static void ensureParentExists(final Path target) throws IOException {
    final Path parent = target.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
  }

  private static java.util.Optional<String> suffixOf(final String fileName) {
    if (fileName.endsWith(PNG_SUFFIX)) {
      return java.util.Optional.of(PNG_SUFFIX);
    }
    if (fileName.endsWith(JPEG_SUFFIX)) {
      return java.util.Optional.of(JPEG_SUFFIX);
    }
    if (fileName.endsWith(JPG_SUFFIX)) {
      return java.util.Optional.of(JPG_SUFFIX);
    }
    return java.util.Optional.empty();
  }
}
