package com.igormaznitsa.annotator.api.model;

import java.util.List;

public record AnnotationCoords(
    Double x,
    Double y,
    Double width,
    Double height,
    List<NormPoint> points,
    List<NormPoint> corners) {

  public static AnnotationCoords rectangle(final double x, final double y, final double width,
                                           final double height) {
    return new AnnotationCoords(x, y, width, height, List.of(), List.of());
  }

  public static AnnotationCoords polygon(final List<NormPoint> points) {
    return new AnnotationCoords(null, null, null, null, List.copyOf(points), List.of());
  }

  public static AnnotationCoords pose(
      final double x,
      final double y,
      final double width,
      final double height,
      final List<NormPoint> keypoints) {
    return new AnnotationCoords(x, y, width, height, List.copyOf(keypoints), List.of());
  }

  public static AnnotationCoords obb(final List<NormPoint> corners) {
    return new AnnotationCoords(null, null, null, null, List.of(), ObbCorners.normalize(corners));
  }
}
