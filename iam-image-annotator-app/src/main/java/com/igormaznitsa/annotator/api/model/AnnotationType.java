package com.igormaznitsa.annotator.api.model;

import java.util.Locale;

public enum AnnotationType {
  RECTANGLE("rectangle"),
  POLYGON("polygon"),
  POSE2D("pose2d"),
  OBB("obb");

  private final String jsonName;

  AnnotationType(final String jsonName) {
    this.jsonName = jsonName;
  }

  public static AnnotationType fromJson(final String value) {
    final String normalized = value.trim().toLowerCase(Locale.ROOT);
    for (final AnnotationType type : values()) {
      if (type.jsonName.equals(normalized)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown annotation type: " + value);
  }

  public String jsonName() {
    return this.jsonName;
  }
}
