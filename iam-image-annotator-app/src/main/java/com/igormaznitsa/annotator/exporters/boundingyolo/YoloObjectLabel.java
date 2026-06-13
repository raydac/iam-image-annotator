package com.igormaznitsa.annotator.exporters.boundingyolo;

public record YoloObjectLabel(
    String className,
    int classId,
    String labelLine,
    ImageZone zone,
    BoundingBoxSize size) {

  public static YoloObjectLabel of(
      final String className,
      final int classId,
      final String labelLine,
      final YoloBoundingBox.Bounds bounds) {
    return new YoloObjectLabel(
        className,
        classId,
        labelLine,
        ImageZone.of(bounds.xCenter(), bounds.yCenter()),
        BoundingBoxSize.of(bounds.width() * bounds.height()));
  }
}
