package com.igormaznitsa.annotator.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ObbCornersTest {

  @Test
  void fromThreePointsFormsParallelogram() {
    final List<NormPoint> corners = ObbCorners.fromThreePoints(
        NormPoint.of(0.1, 0.1),
        NormPoint.of(0.5, 0.1),
        NormPoint.of(0.5, 0.4));
    assertEquals(4, corners.size());
    assertTrue(corners.stream()
        .anyMatch(point -> Math.abs(point.x() - 0.1) < 1e-6 && Math.abs(point.y() - 0.1) < 1e-6));
    assertTrue(corners.stream()
        .anyMatch(point -> Math.abs(point.x() - 0.1) < 1e-6 && Math.abs(point.y() - 0.4) < 1e-6));
  }

  @Test
  void toYoloLineHasFourCornerPairs() {
    final List<NormPoint> corners = List.of(
        NormPoint.of(0.780811, 0.743961),
        NormPoint.of(0.782371, 0.74686),
        NormPoint.of(0.777691, 0.752174),
        NormPoint.of(0.776131, 0.749758));
    final String line = ObbCorners.toYoloLine(0, corners);
    final String[] parts = line.split(" ");
    assertEquals(9, parts.length);
    assertEquals("0", parts[0]);
  }

  @Test
  void rejectsWrongCornerCount() {
    assertThrows(IllegalArgumentException.class,
        () -> ObbCorners.normalize(List.of(NormPoint.of(0, 0))));
  }
}
