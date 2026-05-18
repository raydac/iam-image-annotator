package com.igormaznitsa.annotator.api.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class AllowedImageFiles {

  private static final String PNG_SUFFIX = ".png";

  private AllowedImageFiles() {
  }

  public static boolean isAllowed(final Path path) {
    if (!Files.isRegularFile(path)) {
      return false;
    }
    final String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
    if (!lower.endsWith(PNG_SUFFIX)) {
      return false;
    }
    final String stem = lower.substring(0, lower.length() - PNG_SUFFIX.length());
    return !stem.contains(".");
  }

  public static void ensureParentExists(final Path target) throws IOException {
    final Path parent = target.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
  }
}
