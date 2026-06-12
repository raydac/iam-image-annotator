package com.igormaznitsa.annotator.exporters.boundingyolo;

enum ImageZone {
  TOP_LEFT,
  TOP_CENTER,
  TOP_RIGHT,
  MIDDLE_LEFT,
  MIDDLE_CENTER,
  MIDDLE_RIGHT,
  BOTTOM_LEFT,
  BOTTOM_CENTER,
  BOTTOM_RIGHT;

  static ImageZone of(final double x, final double y) {
    return values()[row(y) * 3 + column(x)];
  }

  private static int column(final double x) {
    if (x < 1.0d / 3.0d) {
      return 0;
    }
    return x < 2.0d / 3.0d ? 1 : 2;
  }

  private static int row(final double y) {
    if (y < 1.0d / 3.0d) {
      return 0;
    }
    return y < 2.0d / 3.0d ? 1 : 2;
  }
}
