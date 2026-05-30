package com.igormaznitsa.annotator.api.json;

/**
 * Gson DTO for one annotation entry.
 */
final class AnnotationEntryJson {

  String id;
  String type;
  String fillColor;
  Object lock;
  Object visible;
  AnnotationCoordsJson coords;

  AnnotationEntryJson() {
  }

  AnnotationEntryJson(
      final String id,
      final String type,
      final String fillColor,
      final Object lock,
      final Object visible,
      final AnnotationCoordsJson coords) {
    this.id = id;
    this.type = type;
    this.fillColor = fillColor;
    this.lock = lock;
    this.visible = visible;
    this.coords = coords;
  }
}
