package com.igormaznitsa.annotator.ui.selection;

import com.igormaznitsa.annotator.api.model.NormPoint;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;

/**
 * Traces the outer boundary of a flood-fill mask and builds a simplified polygon.
 */
final class MagicWandMaskContour {

  private static final int MIN_POLYGON_VERTICES = 3;
  private static final int MAX_CONTOUR_POINTS = 4_096;

  private static final int[] NEIGHBOR_4_DX = {1, -1, 0, 0};
  private static final int[] NEIGHBOR_4_DY = {0, 0, 1, -1};
  private static final int[] DIR_DX = {1, 1, 0, -1, -1, -1, 0, 1};
  private static final int[] DIR_DY = {0, 1, 1, 1, 0, -1, -1, -1};

  private MagicWandMaskContour() {
  }

  static Optional<List<NormPoint>> toPolygon(
      final MagicWandMask mask,
      final int seedX,
      final int seedY,
      final MagicWandSettings settings) {
    final int width = mask.width();
    final int height = mask.height();
    final List<Point> contour = traceOuterContour(mask.pixels(), width, height, seedX, seedY);
    if (contour.size() < MIN_POLYGON_VERTICES) {
      return Optional.empty();
    }
    final NormPoint seed = NormPoint.of((seedX + 0.5) / width, (seedY + 0.5) / height);
    final List<NormPoint> normalized = toNormalizedContour(contour, width, height);
    final double epsilon = simplificationEpsilon(width, height, settings.smoothness());
    final List<NormPoint> simplified = simplifyClosedPolygon(normalized, epsilon);
    final List<NormPoint> windingSafe = ensureClockwiseWinding(removeDuplicateVertices(simplified));
    final List<NormPoint> seedSafe = ensureContainsSeed(windingSafe, normalized, seed, epsilon);
    if (seedSafe.size() < MIN_POLYGON_VERTICES) {
      return Optional.empty();
    }
    return Optional.of(seedSafe);
  }

  private static List<Point> traceOuterContour(
      final BitSet region,
      final int width,
      final int height,
      final int seedX,
      final int seedY) {
    final int start = findBoundaryStartNearSeed(region, width, height, seedX, seedY);
    if (start < 0) {
      return List.of();
    }
    int x = start % width;
    int y = start / width;
    int direction = 7;
    final int startX = x;
    final int startY = y;
    final List<Point> contour = new ArrayList<>();
    int guard = 0;
    do {
      contour.add(new Point(x, y));
      if (contour.size() > MAX_CONTOUR_POINTS) {
        return subsampleContour(contour, MAX_CONTOUR_POINTS);
      }
      boolean stepped = false;
      final int from = (direction + 5) & 7;
      for (int offset = 0; offset < 8; offset++) {
        final int nextDirection = (from + offset) & 7;
        final int nextX = x + DIR_DX[nextDirection];
        final int nextY = y + DIR_DY[nextDirection];
        if (isBoundaryPixel(region, width, height, nextX, nextY)) {
          x = nextX;
          y = nextY;
          direction = nextDirection;
          stepped = true;
          break;
        }
      }
      if (!stepped) {
        break;
      }
      guard++;
    } while ((x != startX || y != startY || contour.size() == 1) && guard < width * height * 4);
    if (contour.size() > 1 && contour.get(0).equals(contour.get(contour.size() - 1))) {
      contour.remove(contour.size() - 1);
    }
    return contour;
  }

  private static int findBoundaryStartNearSeed(
      final BitSet region,
      final int width,
      final int height,
      final int seedX,
      final int seedY) {
    if (!region.get(MagicWandMask.index(width, seedX, seedY))) {
      return -1;
    }
    final ArrayDeque<Integer> queue = new ArrayDeque<>();
    final BitSet visited = new BitSet(width * height);
    queue.add(MagicWandMask.index(width, seedX, seedY));
    visited.set(MagicWandMask.index(width, seedX, seedY));
    while (!queue.isEmpty()) {
      final int current = queue.poll();
      final int x = current % width;
      final int y = current / width;
      if (isBoundaryPixel(region, width, height, x, y)) {
        return current;
      }
      for (int direction = 0; direction < NEIGHBOR_4_DX.length; direction++) {
        final int neighborX = x + NEIGHBOR_4_DX[direction];
        final int neighborY = y + NEIGHBOR_4_DY[direction];
        if (!MagicWandMask.isInside(width, height, neighborX, neighborY)) {
          continue;
        }
        final int neighbor = MagicWandMask.index(width, neighborX, neighborY);
        if (!region.get(neighbor) || visited.get(neighbor)) {
          continue;
        }
        visited.set(neighbor);
        queue.add(neighbor);
      }
    }
    return -1;
  }

  private static boolean isBoundaryPixel(final BitSet region, final int width, final int height,
                                         final int x, final int y) {
    if (!MagicWandMask.isInside(width, height, x, y) ||
        !region.get(MagicWandMask.index(width, x, y))) {
      return false;
    }
    return x == 0
        || y == 0
        || x == width - 1
        || y == height - 1
        || !region.get(MagicWandMask.index(width, x - 1, y))
        || !region.get(MagicWandMask.index(width, x + 1, y))
        || !region.get(MagicWandMask.index(width, x, y - 1))
        || !region.get(MagicWandMask.index(width, x, y + 1));
  }

  private static List<Point> subsampleContour(final List<Point> contour, final int maxPoints) {
    if (contour.size() <= maxPoints) {
      return contour;
    }
    final List<Point> sampled = new ArrayList<>(maxPoints);
    final double step = (double) contour.size() / maxPoints;
    for (int i = 0; i < maxPoints; i++) {
      sampled.add(contour.get((int) Math.floor(i * step)));
    }
    return sampled;
  }

  private static List<NormPoint> toNormalizedContour(final List<Point> contour, final int width,
                                                     final int height) {
    final List<NormPoint> points = new ArrayList<>(contour.size());
    for (final Point point : contour) {
      points.add(NormPoint.of((double) point.x / width, (double) point.y / height));
    }
    return points;
  }

  private static List<NormPoint> ensureContainsSeed(
      final List<NormPoint> polygon,
      final List<NormPoint> fullContour,
      final NormPoint seed,
      final double epsilon) {
    if (containsPoint(seed.x(), seed.y(), polygon)) {
      return polygon;
    }
    for (final double factor : new double[] {0.5, 0.2, 0.05, 0.0}) {
      final List<NormPoint> relaxed = simplifyClosedPolygon(fullContour, epsilon * factor);
      if (containsPoint(seed.x(), seed.y(), relaxed)) {
        return relaxed;
      }
    }
    return insertVertexNearSeed(polygon, seed);
  }

  private static List<NormPoint> insertVertexNearSeed(final List<NormPoint> polygon,
                                                      final NormPoint seed) {
    if (polygon.isEmpty()) {
      return List.of(seed);
    }
    int bestEdge = 0;
    double bestDistance = Double.POSITIVE_INFINITY;
    for (int i = 0; i < polygon.size(); i++) {
      final NormPoint start = polygon.get(i);
      final NormPoint end = polygon.get((i + 1) % polygon.size());
      final double distance = perpendicularDistance(seed, start, end);
      if (distance < bestDistance) {
        bestDistance = distance;
        bestEdge = i;
      }
    }
    final List<NormPoint> expanded = new ArrayList<>(polygon.size() + 1);
    for (int i = 0; i <= bestEdge; i++) {
      expanded.add(polygon.get(i));
    }
    expanded.add(seed);
    for (int i = bestEdge + 1; i < polygon.size(); i++) {
      expanded.add(polygon.get(i));
    }
    return expanded;
  }

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

  private static List<NormPoint> simplifyClosedPolygon(final List<NormPoint> contour,
                                                       final double epsilon) {
    if (contour.size() < MIN_POLYGON_VERTICES) {
      return List.copyOf(contour);
    }
    if (epsilon <= 0.0 || contour.size() <= 4) {
      return List.copyOf(contour);
    }
    final int[] diameter = diameterEndpoints(contour);
    final List<NormPoint> chainA = contourChain(contour, diameter[0], diameter[1]);
    final List<NormPoint> chainB = contourChain(contour, diameter[1], diameter[0]);
    final List<NormPoint> simplifiedA = douglasPeuckerOpen(chainA, epsilon);
    final List<NormPoint> simplifiedB = douglasPeuckerOpen(chainB, epsilon);
    final List<NormPoint> merged = new ArrayList<>(simplifiedA.size() + simplifiedB.size());
    merged.addAll(simplifiedA.subList(0, simplifiedA.size() - 1));
    merged.addAll(simplifiedB.subList(0, simplifiedB.size() - 1));
    if (merged.size() >= MIN_POLYGON_VERTICES) {
      return merged;
    }
    return simplifyClosedContourFallback(contour, epsilon);
  }

  private static List<NormPoint> simplifyClosedContourFallback(final List<NormPoint> contour,
                                                               final double epsilon) {
    final List<NormPoint> open = new ArrayList<>(contour);
    open.add(contour.get(0));
    List<NormPoint> simplified = douglasPeuckerOpen(open, epsilon * 0.5);
    if (simplified.size() > 1) {
      final NormPoint first = simplified.get(0);
      final NormPoint last = simplified.get(simplified.size() - 1);
      if (Math.abs(first.x() - last.x()) < 1e-9 && Math.abs(first.y() - last.y()) < 1e-9) {
        simplified = new ArrayList<>(simplified.subList(0, simplified.size() - 1));
      }
    }
    if (simplified.size() >= MIN_POLYGON_VERTICES) {
      return simplified;
    }
    return subsampleNormalized(contour, 32);
  }

  private static List<NormPoint> subsampleNormalized(final List<NormPoint> contour,
                                                     final int maxPoints) {
    if (contour.size() <= maxPoints) {
      return List.copyOf(contour);
    }
    final List<NormPoint> sampled = new ArrayList<>(maxPoints);
    final double step = (double) contour.size() / maxPoints;
    for (int i = 0; i < maxPoints; i++) {
      sampled.add(contour.get((int) Math.floor(i * step)));
    }
    return sampled;
  }

  private static double simplificationEpsilon(
      final int imageWidth,
      final int imageHeight,
      final double smoothness) {
    final double pixels = 2.0 + smoothness * 12.0;
    return pixels / Math.min(imageWidth, imageHeight);
  }

  private static List<NormPoint> contourChain(final List<NormPoint> contour, final int from,
                                              final int to) {
    final List<NormPoint> chain = new ArrayList<>();
    int index = from;
    while (true) {
      chain.add(contour.get(index));
      if (index == to) {
        return chain;
      }
      index = (index + 1) % contour.size();
    }
  }

  private static int[] diameterEndpoints(final List<NormPoint> points) {
    int indexA = 0;
    int indexB = Math.min(1, points.size() - 1);
    double maxDistance = -1;
    for (int i = 0; i < points.size(); i++) {
      for (int j = i + 1; j < points.size(); j++) {
        final double distance = Math.hypot(
            points.get(i).x() - points.get(j).x(),
            points.get(i).y() - points.get(j).y());
        if (distance > maxDistance) {
          maxDistance = distance;
          indexA = i;
          indexB = j;
        }
      }
    }
    return new int[] {indexA, indexB};
  }

  private static List<NormPoint> douglasPeuckerOpen(final List<NormPoint> points,
                                                    final double epsilon) {
    if (points.size() < MIN_POLYGON_VERTICES) {
      return List.copyOf(points);
    }
    double maxDistance = 0;
    int splitIndex = 0;
    final NormPoint start = points.get(0);
    final NormPoint end = points.get(points.size() - 1);
    for (int i = 1; i < points.size() - 1; i++) {
      final double distance = perpendicularDistance(points.get(i), start, end);
      if (distance > maxDistance) {
        maxDistance = distance;
        splitIndex = i;
      }
    }
    if (maxDistance > epsilon) {
      final List<NormPoint> left = douglasPeuckerOpen(points.subList(0, splitIndex + 1), epsilon);
      final List<NormPoint> right =
          douglasPeuckerOpen(points.subList(splitIndex, points.size()), epsilon);
      final List<NormPoint> merged = new ArrayList<>(left.size() + right.size());
      merged.addAll(left.subList(0, left.size() - 1));
      merged.addAll(right);
      return merged;
    }
    return List.of(start, end);
  }

  private static List<NormPoint> ensureClockwiseWinding(final List<NormPoint> points) {
    if (signedArea(points) < 0) {
      return reverseCopy(points);
    }
    return List.copyOf(points);
  }

  private static double signedArea(final List<NormPoint> points) {
    double area = 0;
    final int count = points.size();
    for (int i = 0; i < count; i++) {
      final NormPoint current = points.get(i);
      final NormPoint next = points.get((i + 1) % count);
      area += current.x() * next.y() - next.x() * current.y();
    }
    return area * 0.5;
  }

  private static List<NormPoint> reverseCopy(final List<NormPoint> points) {
    final List<NormPoint> reversed = new ArrayList<>(points.size());
    for (int i = points.size() - 1; i >= 0; i--) {
      reversed.add(points.get(i));
    }
    return reversed;
  }

  private static List<NormPoint> removeDuplicateVertices(final List<NormPoint> points) {
    if (points.isEmpty()) {
      return List.of();
    }
    final List<NormPoint> unique = new ArrayList<>(points.size());
    NormPoint previous = null;
    for (final NormPoint point : points) {
      if (previous != null
          && Math.abs(point.x() - previous.x()) < 1e-9
          && Math.abs(point.y() - previous.y()) < 1e-9) {
        continue;
      }
      unique.add(point);
      previous = point;
    }
    if (unique.size() > 1) {
      final NormPoint first = unique.get(0);
      final NormPoint last = unique.get(unique.size() - 1);
      if (Math.abs(first.x() - last.x()) < 1e-9 && Math.abs(first.y() - last.y()) < 1e-9) {
        unique.remove(unique.size() - 1);
      }
    }
    return unique;
  }

  private static double perpendicularDistance(final NormPoint point, final NormPoint lineStart,
                                              final NormPoint lineEnd) {
    final double dx = lineEnd.x() - lineStart.x();
    final double dy = lineEnd.y() - lineStart.y();
    if (dx == 0 && dy == 0) {
      return Math.hypot(point.x() - lineStart.x(), point.y() - lineStart.y());
    }
    final double numerator = Math.abs(
        dy * point.x() - dx * point.y() + lineEnd.x() * lineStart.y() -
            lineEnd.y() * lineStart.x());
    final double denominator = Math.hypot(dx, dy);
    return numerator / denominator;
  }
}
