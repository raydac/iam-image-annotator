package com.igormaznitsa.annotator.ui.selection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.igormaznitsa.annotator.api.model.NormPoint;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class MagicWandSelectorTest {

  private static boolean containsPoint(final double x, final double y,
                                       final List<NormPoint> polygon) {
    boolean inside = false;
    for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
      final NormPoint current = polygon.get(i);
      final NormPoint previous = polygon.get(j);
      final boolean intersects = (current.y() > y) != (previous.y() > y)
          && x < (previous.x() - current.x()) * (y - current.y()) / (previous.y() - current.y()) +
          current.x();
      if (intersects) {
        inside = !inside;
      }
    }
    return inside;
  }

  private static double signedArea(final List<NormPoint> points) {
    double area = 0;
    for (int i = 0; i < points.size(); i++) {
      final NormPoint current = points.get(i);
      final NormPoint next = points.get((i + 1) % points.size());
      area += current.x() * next.y() - next.x() * current.y();
    }
    return area * 0.5;
  }

  private static boolean hasUniqueVertices(final List<NormPoint> polygon) {
    return new HashSet<>(polygon).size() == polygon.size();
  }

  private static boolean hasVertexAt(final List<NormPoint> polygon, final double x,
                                     final double y) {
    return polygon.stream()
        .anyMatch(point -> Math.abs(point.x() - x) < 1e-12 && Math.abs(point.y() - y) < 1e-12);
  }

  private static boolean isSimplePolygon(final List<NormPoint> polygon) {
    for (int i = 0; i < polygon.size(); i++) {
      for (int j = i + 1; j < polygon.size(); j++) {
        if (!areAdjacentEdges(i, j, polygon.size()) && segmentsIntersect(
            polygon.get(i),
            polygon.get((i + 1) % polygon.size()),
            polygon.get(j),
            polygon.get((j + 1) % polygon.size()))) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean areAdjacentEdges(final int left, final int right, final int count) {
    return left == right || (left + 1) % count == right || (right + 1) % count == left;
  }

  private static boolean segmentsIntersect(
      final NormPoint firstStart,
      final NormPoint firstEnd,
      final NormPoint secondStart,
      final NormPoint secondEnd) {
    final int orientationA = orientation(firstStart, firstEnd, secondStart);
    final int orientationB = orientation(firstStart, firstEnd, secondEnd);
    final int orientationC = orientation(secondStart, secondEnd, firstStart);
    final int orientationD = orientation(secondStart, secondEnd, firstEnd);
    return orientationA != orientationB && orientationC != orientationD
        || orientationA == 0 && isOnSegment(firstStart, secondStart, firstEnd)
        || orientationB == 0 && isOnSegment(firstStart, secondEnd, firstEnd)
        || orientationC == 0 && isOnSegment(secondStart, firstStart, secondEnd)
        || orientationD == 0 && isOnSegment(secondStart, firstEnd, secondEnd);
  }

  private static int orientation(final NormPoint a, final NormPoint b, final NormPoint c) {
    final double value = (b.y() - a.y()) * (c.x() - b.x()) -
        (b.x() - a.x()) * (c.y() - b.y());
    if (Math.abs(value) < 1e-12) {
      return 0;
    }
    return value > 0.0 ? 1 : -1;
  }

  private static boolean isOnSegment(final NormPoint start, final NormPoint point,
                                     final NormPoint end) {
    return point.x() >= Math.min(start.x(), end.x())
        && point.x() <= Math.max(start.x(), end.x())
        && point.y() >= Math.min(start.y(), end.y())
        && point.y() <= Math.max(start.y(), end.y());
  }

  @Test
  void selectsSolidRectangleRegion() {
    final BufferedImage image = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
    final var graphics = image.createGraphics();
    graphics.setColor(new Color(200, 200, 200));
    graphics.fillRect(0, 0, 40, 30);
    graphics.setColor(new Color(40, 40, 40));
    graphics.fillRect(10, 8, 12, 10);
    graphics.dispose();
    final List<NormPoint> polygon = MagicWandSelector.selectPolygon(
        image,
        15,
        12,
        new MagicWandSettings(0.2, MagicWandMode.LUMINANCE, 0.75, false)).orElseThrow();
    assertTrue(polygon.size() >= 3);
    assertTrue(polygon.size() <= 12);
  }

  @Test
  void usesClockwiseWindingInImageSpace() {
    final BufferedImage image = new BufferedImage(50, 40, BufferedImage.TYPE_INT_RGB);
    final var graphics = image.createGraphics();
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, 50, 40);
    graphics.setColor(Color.BLACK);
    graphics.fillRect(12, 10, 20, 16);
    graphics.dispose();
    final List<NormPoint> polygon =
        MagicWandSelector.selectPolygon(image, 20, 18, 0.25).orElseThrow();
    assertTrue(signedArea(polygon) > 0, "polygon must be clockwise in image coordinates");
  }

  @Test
  void colorModeSelectsColoredBlobDespiteSimilarLuminance() {
    final BufferedImage image = new BufferedImage(60, 40, BufferedImage.TYPE_INT_RGB);
    final var graphics = image.createGraphics();
    graphics.setColor(new Color(30, 180, 30));
    graphics.fillRect(0, 0, 60, 40);
    graphics.setColor(new Color(180, 30, 30));
    graphics.fillRect(15, 10, 20, 14);
    graphics.dispose();
    final MagicWandSettings settings = new MagicWandSettings(0.2, MagicWandMode.COLOR, 0.35, false);
    final List<NormPoint> polygon =
        MagicWandSelector.selectPolygon(image, 22, 16, settings).orElseThrow();
    assertTrue(polygon.size() >= 3);
    assertTrue(polygon.stream()
        .allMatch(point -> point.x() >= 0 && point.x() <= 1 && point.y() >= 0 && point.y() <= 1));
  }

  @Test
  void tracesConcaveRegionAsSimpleOrderedPolygon() {
    final BufferedImage image = new BufferedImage(90, 70, BufferedImage.TYPE_INT_RGB);
    final var graphics = image.createGraphics();
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, 90, 70);
    graphics.setColor(Color.BLACK);
    graphics.fillRect(15, 15, 55, 35);
    graphics.setColor(Color.WHITE);
    graphics.fillRect(30, 25, 25, 25);
    graphics.fillRect(55, 15, 15, 10);
    graphics.dispose();
    final int seedX = 24;
    final int seedY = 24;
    final List<NormPoint> polygon = MagicWandSelector.selectPolygon(
        image,
        seedX,
        seedY,
        new MagicWandSettings(0.2, MagicWandMode.COLOR, 0.3, false)).orElseThrow();
    assertTrue(hasUniqueVertices(polygon), "polygon must not cycle through repeated vertices");
    assertTrue(isSimplePolygon(polygon), "polygon edges must not cross");
    assertTrue(containsPoint((seedX + 0.5) / 90.0, (seedY + 0.5) / 70.0, polygon));
  }

  @Test
  void simplifiedPolygonStillContainsClickPointInConcaveRegion() {
    final BufferedImage image = new BufferedImage(80, 80, BufferedImage.TYPE_INT_RGB);
    final var graphics = image.createGraphics();
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, 80, 80);
    graphics.setColor(Color.BLACK);
    graphics.fillRect(20, 20, 40, 40);
    graphics.setColor(Color.WHITE);
    graphics.fillRect(20, 20, 25, 25);
    graphics.dispose();
    final int seedX = 30;
    final int seedY = 30;
    final MagicWandSettings settings =
        new MagicWandSettings(0.15, MagicWandMode.LUMINANCE, 0.9, false);
    final List<NormPoint> polygon =
        MagicWandSelector.selectPolygon(image, seedX, seedY, settings).orElseThrow();
    final double seedNormX = (seedX + 0.5) / 80.0;
    final double seedNormY = (seedY + 0.5) / 80.0;
    assertTrue(containsPoint(seedNormX, seedNormY, polygon));
    assertFalse(hasVertexAt(polygon, seedNormX, seedNormY),
        "click point must not be injected as a boundary vertex");
  }

  @Test
  void doesNotLeakThroughSinglePixelBridge() {
    final BufferedImage image = new BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB);
    final var graphics = image.createGraphics();
    graphics.setColor(Color.BLACK);
    graphics.fillRect(0, 0, 100, 50);
    graphics.setColor(new Color(200, 200, 200));
    graphics.fillRect(10, 10, 30, 30);
    graphics.fillRect(60, 10, 30, 30);
    graphics.fillRect(39, 24, 2, 2);
    graphics.dispose();
    final List<NormPoint> polygon = MagicWandSelector.selectPolygon(
        image,
        20,
        20,
        new MagicWandSettings(0.35, MagicWandMode.COLOR, 0.35, false)).orElseThrow();
    final double seedNormX = 20.5 / 100.0;
    final double seedNormY = 20.5 / 50.0;
    assertTrue(containsPoint(seedNormX, seedNormY, polygon));
    assertTrue(polygon.stream().noneMatch(point -> point.x() > 0.55),
        "polygon should stay on the left blob, not cross the bridge");
  }

  @Test
  void rejectsTinyRegion() {
    final BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
    image.setRGB(5, 5, Color.WHITE.getRGB());
    assertTrue(MagicWandSelector.selectPolygon(image, 5, 5, 0.01).isEmpty());
  }
}
