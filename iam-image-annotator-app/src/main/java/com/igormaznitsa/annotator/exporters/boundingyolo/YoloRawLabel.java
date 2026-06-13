package com.igormaznitsa.annotator.exporters.boundingyolo;

import static java.util.Objects.requireNonNull;

import java.util.function.IntFunction;

public record YoloRawLabel(
    String className,
    IntFunction<String> labelLineFormatter,
    YoloBoundingBox.Bounds bounds) {

  public YoloRawLabel {
    requireNonNull(className, "className");
    requireNonNull(labelLineFormatter, "labelLineFormatter");
    requireNonNull(bounds, "bounds");
  }

  public YoloObjectLabel toObjectLabel(final int classId) {
    return YoloObjectLabel.of(
        this.className,
        classId,
        this.labelLineFormatter.apply(classId),
        this.bounds);
  }
}
