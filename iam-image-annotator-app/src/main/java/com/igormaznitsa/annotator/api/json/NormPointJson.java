package com.igormaznitsa.annotator.api.json;

/**
 * Gson DTO for a normalized point; {@code v} is omitted when visibility is the default (2).
 */
final class NormPointJson {

  double x;
  double y;
  Integer v;

  NormPointJson() {
  }

  NormPointJson(final double x, final double y, final Integer v) {
    this.x = x;
    this.y = y;
    this.v = v;
  }
}
