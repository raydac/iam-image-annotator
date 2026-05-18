package com.igormaznitsa.annotator.ui.selection;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.igormaznitsa.annotator.api.model.NormPoint;
import java.awt.Color;
import java.awt.image.BufferedImage;
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
