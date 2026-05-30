package com.igormaznitsa.annotator.api.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class ClassNames {

  private static final Pattern CLASS_ID = Pattern.compile("^[A-Za-z0-9_.\\-]+$");
  private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

  private ClassNames() {
  }

  public static String normalize(final String classId) {
    Objects.requireNonNull(classId, "classId");
    final String trimmed = classId.trim();
    if (!CLASS_ID.matcher(trimmed).matches()) {
      throw new IllegalArgumentException(
          "Class id must be alphanumeric with _, . or - only: " + classId);
    }
    return trimmed;
  }

  public static boolean matchesIgnoreCase(final String left, final String right) {
    return normalize(left).equalsIgnoreCase(normalize(right));
  }

  public static String normalizeColor(final String colorHex) {
    Objects.requireNonNull(colorHex, "colorHex");
    final String trimmed = colorHex.trim();
    if (!COLOR.matcher(trimmed).matches()) {
      throw new IllegalArgumentException("Color must be #RRGGBB: " + colorHex);
    }
    return trimmed.toUpperCase(Locale.ROOT);
  }

  public static String autoColor(final String classId) {
    final int hash = normalize(classId).toLowerCase(Locale.ROOT).hashCode();
    final int r = (hash & 0xFF0000) >> 16;
    final int g = (hash & 0x00FF00) >> 8;
    final int b = hash & 0x0000FF;
    return normalizeColor(String.format(
        Locale.ROOT,
        "#%02X%02X%02X",
        Math.max(64, r),
        Math.max(64, g),
        Math.max(64, b)));
  }
}
