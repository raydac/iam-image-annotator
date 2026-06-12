package com.igormaznitsa.annotator.exporters.boundingyolo;

enum BoundingBoxSize {
  SMALL,
  MEDIUM,
  LARGE;

  static BoundingBoxSize of(final double normalizedArea) {
    if (normalizedArea < 0.01d) {
      return SMALL;
    }
    return normalizedArea < 0.10d ? MEDIUM : LARGE;
  }
}
