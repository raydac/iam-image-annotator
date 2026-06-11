package com.igormaznitsa.annotator.api.png;

import static java.util.Objects.requireNonNull;

import com.igormaznitsa.annotator.api.model.AnnotationCoords;
import com.igormaznitsa.annotator.api.model.AnnotationDocument;
import com.igormaznitsa.annotator.api.model.AnnotationEntry;
import com.igormaznitsa.annotator.api.model.NormPoint;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Lossy optimizer for the display raster only; the embedded base PNG remains untouched.
 */
final class PngDisplayRasterOptimizer {

  private static final int POSTERIZATION_LEVELS = 16;

  BufferedImage optimizeBaseForFilledAnnotations(
      final BufferedImage base,
      final AnnotationDocument document) {
    requireNonNull(base, "base");
    requireNonNull(document, "document");
    if (!this.hasCompressibleFilledRegions(document)) {
      return base;
    }

    final BufferedImage mask = this.createFilledRegionMask(base, document);
    final BufferedImage optimized = this.copyAsArgb(base);
    this.posterizeMaskedPixels(optimized, mask);
    return optimized;
  }

  BufferedImage optimizeEncodedDisplayRaster(final BufferedImage image) {
    requireNonNull(image, "image");
    return this.isOpaque(image) ? this.copyAsRgb(image) : image;
  }

  private boolean hasCompressibleFilledRegions(final AnnotationDocument document) {
    return document.entries().stream().anyMatch(this::isCompressibleFilledRegion);
  }

  private boolean isCompressibleFilledRegion(final AnnotationEntry entry) {
    return entry.visible() && switch (entry.type()) {
      case RECTANGLE -> true;
      case POLYGON -> entry.coords().points().size() >= 3;
      case OBB -> entry.coords().corners().size() >= 3;
      case POSE2D -> false;
    };
  }

  private BufferedImage createFilledRegionMask(
      final BufferedImage base,
      final AnnotationDocument document) {
    final BufferedImage mask = new BufferedImage(
        base.getWidth(),
        base.getHeight(),
        BufferedImage.TYPE_BYTE_BINARY);
    final Graphics2D graphics = mask.createGraphics();
    graphics.setColor(Color.WHITE);
    document.entries().stream()
        .filter(this::isCompressibleFilledRegion)
        .forEach(entry -> this.paintFillMask(graphics, base.getWidth(), base.getHeight(), entry));
    graphics.dispose();
    return mask;
  }

  private void paintFillMask(
      final Graphics2D graphics,
      final int imageWidth,
      final int imageHeight,
      final AnnotationEntry entry) {
    final AnnotationCoords coords = entry.coords();
    switch (entry.type()) {
      case RECTANGLE -> graphics.fill(new Rectangle2D.Double(
          coords.x() * imageWidth,
          coords.y() * imageHeight,
          coords.width() * imageWidth,
          coords.height() * imageHeight));
      case POLYGON ->
          graphics.fillPolygon(this.toPolygon(coords.points(), imageWidth, imageHeight));
      case OBB -> graphics.fillPolygon(this.toPolygon(coords.corners(), imageWidth, imageHeight));
      case POSE2D -> throw new IllegalArgumentException(
          "POSE2D annotations do not define filled preview regions");
    }
  }

  private BufferedImage copyAsArgb(final BufferedImage image) {
    final BufferedImage copy = new BufferedImage(
        image.getWidth(),
        image.getHeight(),
        BufferedImage.TYPE_INT_ARGB);
    final Graphics2D graphics = copy.createGraphics();
    graphics.drawImage(image, 0, 0, null);
    graphics.dispose();
    return copy;
  }

  private BufferedImage copyAsRgb(final BufferedImage image) {
    final BufferedImage copy = new BufferedImage(
        image.getWidth(),
        image.getHeight(),
        BufferedImage.TYPE_INT_RGB);
    final Graphics2D graphics = copy.createGraphics();
    graphics.drawImage(image, 0, 0, null);
    graphics.dispose();
    return copy;
  }

  private void posterizeMaskedPixels(final BufferedImage image, final BufferedImage mask) {
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if ((mask.getRGB(x, y) & 0x00FFFFFF) != 0) {
          image.setRGB(x, y, this.posterize(image.getRGB(x, y)));
        }
      }
    }
  }

  private int posterize(final int argb) {
    return (argb & 0xFF000000)
        | (this.posterizeChannel((argb >>> 16) & 0xFF) << 16)
        | (this.posterizeChannel((argb >>> 8) & 0xFF) << 8)
        | this.posterizeChannel(argb & 0xFF);
  }

  private int posterizeChannel(final int value) {
    return ((value * (POSTERIZATION_LEVELS - 1) + 127) / 255) * 255 / (POSTERIZATION_LEVELS - 1);
  }

  private boolean isOpaque(final BufferedImage image) {
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if (((image.getRGB(x, y) >>> 24) & 0xFF) != 0xFF) {
          return false;
        }
      }
    }
    return true;
  }

  private Polygon toPolygon(
      final List<NormPoint> points,
      final int imageWidth,
      final int imageHeight) {
    final Polygon polygon = new Polygon();
    points.forEach(point -> polygon.addPoint(
        (int) Math.round(point.x() * imageWidth),
        (int) Math.round(point.y() * imageHeight)));
    return polygon;
  }
}
