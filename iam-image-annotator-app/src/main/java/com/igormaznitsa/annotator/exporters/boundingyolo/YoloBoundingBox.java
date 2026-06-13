package com.igormaznitsa.annotator.exporters.boundingyolo;

import java.util.Locale;

record YoloBoundingBox(
    String className,
    int classId,
    double xCenter,
    double yCenter,
    double width,
    double height,
    ImageZone zone,
    BoundingBoxSize size) {

  static YoloBoundingBox of(final String className, final int classId, final Bounds bounds) {
    return new YoloBoundingBox(
        className,
        classId,
        bounds.xCenter(),
        bounds.yCenter(),
        bounds.width(),
        bounds.height(),
        ImageZone.of(bounds.xCenter(), bounds.yCenter()),
        BoundingBoxSize.of(bounds.width() * bounds.height()));
  }

  String toLabelLine() {
    return String.format(
        Locale.US,
        "%d %.6f %.6f %.6f %.6f",
        this.classId,
        this.xCenter,
        this.yCenter,
        this.width,
        this.height);
  }

  public record Bounds(double minX, double minY, double maxX, double maxY) {

    public Bounds {
      minX = clamp01(minX);
      minY = clamp01(minY);
      maxX = clamp01(maxX);
      maxY = clamp01(maxY);
      if (maxX <= minX || maxY <= minY) {
        throw new IllegalArgumentException("bounding box must have positive area");
      }
    }

    private static double clamp01(final double value) {
      return Math.clamp(value, 0.0d, 1.0d);
    }

    double xCenter() {
      return (this.minX + this.maxX) / 2.0d;
    }

    double yCenter() {
      return (this.minY + this.maxY) / 2.0d;
    }

    double width() {
      return this.maxX - this.minX;
    }

    double height() {
      return this.maxY - this.minY;
    }
  }
}
