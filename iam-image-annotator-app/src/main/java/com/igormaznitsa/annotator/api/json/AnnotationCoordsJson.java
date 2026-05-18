package com.igormaznitsa.annotator.api.json;

import java.util.List;

/**
 * Gson DTO for annotation geometry; only fields relevant to the entry type are set.
 */
final class AnnotationCoordsJson {

  Double x;
  Double y;
  Double width;
  Double height;
  List<NormPointJson> points;
  List<NormPointJson> corners;

  AnnotationCoordsJson() {
  }

  AnnotationCoordsJson(
      final Double x,
      final Double y,
      final Double width,
      final Double height,
      final List<NormPointJson> points,
      final List<NormPointJson> corners) {
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
    this.points = points;
    this.corners = corners;
  }
}
