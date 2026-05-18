package com.igormaznitsa.annotator.ui.editor;

import com.igormaznitsa.annotator.api.model.NormPoint;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Optional;

public final class Geometry {

  private Geometry() {
  }

  public static NormPoint normalize(final int imageWidth, final int imageHeight, final double x,
                                    final double y) {
    return NormPoint.of(clamp01(x / imageWidth), clamp01(y / imageHeight));
  }

  public static double clamp01(final double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }

  public static NormPoint[] rectangleFromDrag(
      final NormPoint start,
      final NormPoint end) {
    final double x = Math.min(start.x(), end.x());
    final double y = Math.min(start.y(), end.y());
    final double width = Math.abs(end.x() - start.x());
    final double height = Math.abs(end.y() - start.y());
    return new NormPoint[] {
        NormPoint.of(x, y),
        NormPoint.of(x + width, y + height)
    };
  }

  public static double distance(final Point2D a, final Point2D b) {
    final double dx = a.getX() - b.getX();
    final double dy = a.getY() - b.getY();
    return Math.hypot(dx, dy);
  }

  public static boolean isNear(final Point2D point, final double x, final double y,
                               final double tolerance) {
    return distance(point, new Point2D.Double(x, y)) <= tolerance;
  }

  public static NormPoint centroid(final List<NormPoint> points) {
    double sumX = 0;
    double sumY = 0;
    for (final NormPoint point : points) {
      sumX += point.x();
      sumY += point.y();
    }
    final double count = Math.max(1, points.size());
    return NormPoint.of(sumX / count, sumY / count);
  }

  /**
   * Rotates in image pixel space so non-square images do not shear the shape.
   */
  public static NormPoint rotateInImage(
      final NormPoint point,
      final double centerX,
      final double centerY,
      final double angleRad,
      final int imageWidth,
      final int imageHeight) {
    final double px = point.x() * imageWidth;
    final double py = point.y() * imageHeight;
    final double cx = centerX * imageWidth;
    final double cy = centerY * imageHeight;
    final double dx = px - cx;
    final double dy = py - cy;
    final double cos = Math.cos(angleRad);
    final double sin = Math.sin(angleRad);
    final double rx = dx * cos - dy * sin;
    final double ry = dx * sin + dy * cos;
    return new NormPoint(
        (cx + rx) / imageWidth,
        (cy + ry) / imageHeight,
        point.visibility());
  }

  public static List<NormPoint> rotatePointsInImage(
      final List<NormPoint> points,
      final double centerX,
      final double centerY,
      final double angleRad,
      final int imageWidth,
      final int imageHeight) {
    return points.stream()
        .map(point -> rotateInImage(point, centerX, centerY, angleRad, imageWidth, imageHeight))
        .toList();
  }

  public static double angleInImage(
      final double centerX,
      final double centerY,
      final double x,
      final double y,
      final int imageWidth,
      final int imageHeight) {
    final double dx = x * imageWidth - centerX * imageWidth;
    final double dy = y * imageHeight - centerY * imageHeight;
    return Math.atan2(dy, dx);
  }

  public static NormPoint pointOnCircleInImage(
      final double centerX,
      final double centerY,
      final double angleRad,
      final double radiusPixels,
      final int imageWidth,
      final int imageHeight) {
    final double cx = centerX * imageWidth;
    final double cy = centerY * imageHeight;
    final double px = cx + radiusPixels * Math.cos(angleRad);
    final double py = cy + radiusPixels * Math.sin(angleRad);
    return NormPoint.of(px / imageWidth, py / imageHeight);
  }

  /**
   * Places the handle on the rotation arm, shortened so it stays inside the image bounds.
   */
  public static NormPoint pointOnCircleInImageBounded(
      final double centerX,
      final double centerY,
      final double angleRad,
      final double radiusPixels,
      final int imageWidth,
      final int imageHeight) {
    final double cx = centerX * imageWidth;
    final double cy = centerY * imageHeight;
    final double directionX = Math.cos(angleRad);
    final double directionY = Math.sin(angleRad);
    final double maxDistance =
        rayDistanceToImageBounds(cx, cy, directionX, directionY, imageWidth, imageHeight);
    final double distance = Math.min(radiusPixels, maxDistance);
    return NormPoint.of(
        (cx + distance * directionX) / imageWidth,
        (cy + distance * directionY) / imageHeight);
  }

  public static NormPoint clampNormPoint(final NormPoint point) {
    return new NormPoint(clamp01(point.x()), clamp01(point.y()), point.visibility());
  }

  public static List<NormPoint> clampNormPoints(final List<NormPoint> points) {
    return points.stream().map(Geometry::clampNormPoint).toList();
  }

  public static double[] constrainedTranslationDelta(
      final List<NormPoint> points,
      final double dx,
      final double dy) {
    double minDx = Double.NEGATIVE_INFINITY;
    double maxDx = Double.POSITIVE_INFINITY;
    double minDy = Double.NEGATIVE_INFINITY;
    double maxDy = Double.POSITIVE_INFINITY;
    for (final NormPoint point : points) {
      minDx = Math.max(minDx, -point.x());
      maxDx = Math.min(maxDx, 1.0 - point.x());
      minDy = Math.max(minDy, -point.y());
      maxDy = Math.min(maxDy, 1.0 - point.y());
    }
    return new double[] {
        Math.max(minDx, Math.min(maxDx, dx)),
        Math.max(minDy, Math.min(maxDy, dy))
    };
  }

  static double rayDistanceToImageBounds(
      final double originX,
      final double originY,
      final double directionX,
      final double directionY,
      final int imageWidth,
      final int imageHeight) {
    double limit = Double.POSITIVE_INFINITY;
    if (directionX > 1e-9) {
      limit = Math.min(limit, (imageWidth - originX) / directionX);
    } else if (directionX < -1e-9) {
      limit = Math.min(limit, -originX / directionX);
    }
    if (directionY > 1e-9) {
      limit = Math.min(limit, (imageHeight - originY) / directionY);
    } else if (directionY < -1e-9) {
      limit = Math.min(limit, -originY / directionY);
    }
    return Math.max(0.0, limit);
  }

  public static SegmentProjection projectToSegmentInImage(
      final NormPoint start,
      final NormPoint end,
      final double normX,
      final double normY,
      final int imageWidth,
      final int imageHeight) {
    final double ax = start.x() * imageWidth;
    final double ay = start.y() * imageHeight;
    final double bx = end.x() * imageWidth;
    final double by = end.y() * imageHeight;
    final double px = normX * imageWidth;
    final double py = normY * imageHeight;
    final double dx = bx - ax;
    final double dy = by - ay;
    final double lengthSquared = dx * dx + dy * dy;
    final double t = lengthSquared < 1e-9
        ? 0.0
        : Math.max(0.0, Math.min(1.0, ((px - ax) * dx + (py - ay) * dy) / lengthSquared));
    final double qx = ax + t * dx;
    final double qy = ay + t * dy;
    return new SegmentProjection(
        NormPoint.of(qx / imageWidth, qy / imageHeight),
        Math.hypot(px - qx, py - qy));
  }

  public static Optional<SegmentProjection> nearestEdgeProjection(
      final List<NormPoint> vertices,
      final double normX,
      final double normY,
      final int imageWidth,
      final int imageHeight,
      final double tolerancePixels) {
    if (vertices.size() < 2) {
      return Optional.empty();
    }
    SegmentProjection nearest = null;
    for (int i = 0; i < vertices.size(); i++) {
      final NormPoint start = vertices.get(i);
      final NormPoint end = vertices.get((i + 1) % vertices.size());
      final SegmentProjection projection = projectToSegmentInImage(
          start,
          end,
          normX,
          normY,
          imageWidth,
          imageHeight);
      if (projection.distancePixels() > tolerancePixels) {
        continue;
      }
      if (nearest == null || projection.distancePixels() < nearest.distancePixels()) {
        nearest = projection;
      }
    }
    return Optional.ofNullable(nearest);
  }

  public record SegmentProjection(NormPoint point, double distancePixels) {
  }
}
