package com.igormaznitsa.annotator.api.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Oriented box helpers aligned with
 * <a href="https://docs.ultralytics.com/datasets/obb/">YOLO OBB format</a>
 * ({@code class x1 y1 x2 y2 x3 y3 x4 y4}, normalized corners in order).
 */
public final class ObbCorners {

  private static final int CORNER_COUNT = 4;
  private static final double MIN_AREA = 1e-6;

  private ObbCorners() {
  }

  public static List<NormPoint> normalize(final List<NormPoint> corners) {
    requireValid(corners);
    return orderClockwise(List.copyOf(corners));
  }

  /**
   * Builds a parallelogram (rotated rectangle) from three corners: p1—p2 is one edge, p3 defines width.
   */
  public static List<NormPoint> fromThreePoints(final NormPoint p1, final NormPoint p2,
                                                final NormPoint p3) {
    final double p4x = p1.x() + (p3.x() - p2.x());
    final double p4y = p1.y() + (p3.y() - p2.y());
    return normalize(List.of(p1, p2, p3, NormPoint.of(p4x, p4y)));
  }

  public static void requireValid(final List<NormPoint> corners) {
    if (corners == null || corners.size() != CORNER_COUNT) {
      throw new IllegalArgumentException("OBB requires exactly 4 corners (x1 y1 … x4 y4)");
    }
    if (signedArea(corners) < MIN_AREA) {
      throw new IllegalArgumentException("OBB corners are degenerate");
    }
  }

  public static String toYoloLine(final int classIndex, final List<NormPoint> corners) {
    final List<NormPoint> ordered = normalize(corners);
    return String.format(
        Locale.US,
        "%d %.6f %.6f %.6f %.6f %.6f %.6f %.6f %.6f",
        classIndex,
        ordered.get(0).x(),
        ordered.get(0).y(),
        ordered.get(1).x(),
        ordered.get(1).y(),
        ordered.get(2).x(),
        ordered.get(2).y(),
        ordered.get(3).x(),
        ordered.get(3).y());
  }

  public static Map<String, Integer> classIndexMap(final List<AnnotationEntry> entries) {
    final Map<String, Integer> indices = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    for (final AnnotationEntry entry : entries) {
      indices.putIfAbsent(entry.id(), indices.size());
    }
    return indices;
  }

  private static List<NormPoint> orderClockwise(final List<NormPoint> corners) {
    double centerX = 0;
    double centerY = 0;
    for (final NormPoint corner : corners) {
      centerX += corner.x();
      centerY += corner.y();
    }
    centerX /= CORNER_COUNT;
    centerY /= CORNER_COUNT;
    final double cx = centerX;
    final double cy = centerY;
    final List<NormPoint> sorted = new ArrayList<>(corners);
    sorted.sort(Comparator.comparingDouble(point -> Math.atan2(point.y() - cy, point.x() - cx)));
    return List.copyOf(sorted);
  }

  private static double signedArea(final List<NormPoint> corners) {
    double sum = 0;
    for (int i = 0; i < CORNER_COUNT; i++) {
      final NormPoint a = corners.get(i);
      final NormPoint b = corners.get((i + 1) % CORNER_COUNT);
      sum += a.x() * b.y() - b.x() * a.y();
    }
    return Math.abs(sum) / 2.0;
  }
}
