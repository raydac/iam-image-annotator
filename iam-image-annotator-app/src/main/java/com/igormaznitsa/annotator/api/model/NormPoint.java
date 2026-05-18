package com.igormaznitsa.annotator.api.model;

public record NormPoint(double x, double y, int visibility) {

  public NormPoint(final double x, final double y) {
    this(x, y, 2);
  }

  public static NormPoint of(final double x, final double y) {
    return new NormPoint(x, y, 2);
  }
}
