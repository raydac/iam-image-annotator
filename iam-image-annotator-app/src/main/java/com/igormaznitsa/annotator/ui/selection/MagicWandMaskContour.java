package com.igormaznitsa.annotator.ui.selection;

import com.igormaznitsa.annotator.api.model.NormPoint;

import java.awt.Point;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Traces the outer boundary of a flood-fill mask and builds a simplified polygon.
 */
final class MagicWandMaskContour {

  private static final int MIN_POLYGON_VERTICES = 3;
  private static final int MAX_CONTOUR_POINTS = 4_096;

  private static final int DIRECTION_EAST = 0;
  private static final int DIRECTION_SOUTH = 1;
  private static final int DIRECTION_WEST = 2;
  private static final int DIRECTION_NORTH = 3;

  private MagicWandMaskContour() {
  }

  private static List<Point> traceOuterContour(
      final BitSet region,
      final int width,
      final int height,
      final int seedX,
      final int seedY) {
    if (!region.get(MagicWandMask.index(width, seedX, seedY))) {
      return List.of();
    }
    final List<Point> contour = selectSeedContour(
        traceBoundaryLoops(region, width, height),
        (seedX + 0.5) / width,
        (seedY + 0.5) / height,
        width,
        height);
    return contour.size() > MAX_CONTOUR_POINTS
        ? subsampleContour(contour, MAX_CONTOUR_POINTS)
        : contour;
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

  private static List<List<Point>> traceBoundaryLoops(
      final BitSet region,
      final int width,
      final int height) {
    final List<BorderEdge> edges = buildBoundaryEdges(region, width, height);
    final Map<Point, List<BorderEdge>> edgesByStart = groupEdgesByStart(edges);
    final Set<BorderEdge> unused = new HashSet<>(edges);
    final List<List<Point>> loops = new ArrayList<>();
    for (final BorderEdge edge : edges) {
      if (!unused.contains(edge)) {
        continue;
      }
      final List<Point> loop = traceBoundaryLoop(edge, edgesByStart, unused, edges.size());
      if (loop.size() >= MIN_POLYGON_VERTICES) {
        loops.add(loop);
      }
    }
    return loops;
  }

  private static List<BorderEdge> buildBoundaryEdges(
      final BitSet region,
      final int width,
      final int height) {
    final List<BorderEdge> edges = new ArrayList<>();
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        if (!region.get(MagicWandMask.index(width, x, y))) {
          continue;
        }
        addBoundaryEdges(region, width, height, edges, x, y);
      }
    }
    return edges;
  }

  private static void addBoundaryEdges(
      final BitSet region,
      final int width,
      final int height,
      final List<BorderEdge> edges,
      final int x,
      final int y) {
    if (!hasRegionPixel(region, width, height, x, y - 1)) {
      edges.add(new BorderEdge(new Point(x, y), new Point(x + 1, y), DIRECTION_EAST));
    }
    if (!hasRegionPixel(region, width, height, x + 1, y)) {
      edges.add(new BorderEdge(new Point(x + 1, y), new Point(x + 1, y + 1), DIRECTION_SOUTH));
    }
    if (!hasRegionPixel(region, width, height, x, y + 1)) {
      edges.add(new BorderEdge(new Point(x + 1, y + 1), new Point(x, y + 1), DIRECTION_WEST));
    }
    if (!hasRegionPixel(region, width, height, x - 1, y)) {
      edges.add(new BorderEdge(new Point(x, y + 1), new Point(x, y), DIRECTION_NORTH));
    }
  }

  private static boolean hasRegionPixel(final BitSet region, final int width, final int height,
                                        final int x, final int y) {
    return MagicWandMask.isInside(width, height, x, y)
        && region.get(MagicWandMask.index(width, x, y));
  }

  private static Map<Point, List<BorderEdge>> groupEdgesByStart(final List<BorderEdge> edges) {
    final Map<Point, List<BorderEdge>> edgesByStart = new HashMap<>();
    for (final BorderEdge edge : edges) {
      edgesByStart.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge);
    }
    return edgesByStart;
  }

  private static List<Point> traceBoundaryLoop(
      final BorderEdge first,
      final Map<Point, List<BorderEdge>> edgesByStart,
      final Set<BorderEdge> unused,
      final int maxSteps) {
    BorderEdge edge = first;
    final List<Point> loop = new ArrayList<>();
    for (int steps = 0; steps < maxSteps && unused.remove(edge); steps++) {
      loop.add(edge.from());
      if (edge.to().equals(first.from())) {
        return loop;
      }
      final Optional<BorderEdge> next = chooseNextEdge(edge, edgesByStart, unused);
      if (next.isEmpty()) {
        return List.of();
      }
      edge = next.get();
    }
    return List.of();
  }

  private static Optional<BorderEdge> chooseNextEdge(
      final BorderEdge current,
      final Map<Point, List<BorderEdge>> edgesByStart,
      final Set<BorderEdge> unused) {
    BorderEdge best = null;
    int bestTurn = Integer.MAX_VALUE;
    for (final BorderEdge candidate : edgesByStart.getOrDefault(current.to(), List.of())) {
      if (!unused.contains(candidate)) {
        continue;
      }
      final int turn = Math.floorMod(candidate.direction() - current.direction(), 4);
      if (turn < bestTurn) {
        best = candidate;
        bestTurn = turn;
      }
    }
    return Optional.ofNullable(best);
  }

  private static List<Point> selectSeedContour(
      final List<List<Point>> contours,
      final double seedNormX,
      final double seedNormY,
      final int width,
      final int height) {
    List<Point> best = List.of();
    double bestArea = 0.0;
    for (final List<Point> contour : contours) {
      final double area = signedPointArea(contour);
      if (area <= 0.0 || area <= bestArea ||
          !containsPoint(seedNormX, seedNormY, toNormalizedContour(contour, width, height))) {
        continue;
      }
      best = contour;
      bestArea = area;
    }
    return best.isEmpty() ? largestClockwiseContour(contours) : best;
  }

  private static List<Point> largestClockwiseContour(final List<List<Point>> contours) {
    List<Point> best = List.of();
    double bestArea = 0.0;
    for (final List<Point> contour : contours) {
      final double area = signedPointArea(contour);
      if (area > bestArea) {
        best = contour;
        bestArea = area;
      }
    }
    return best;
  }

  private static double signedPointArea(final List<Point> points) {
    double area = 0.0;
    for (int i = 0; i < points.size(); i++) {
      final Point current = points.get(i);
      final Point next = points.get((i + 1) % points.size());
      area += (double) current.x * next.y - (double) next.x * current.y;
    }
    return area * 0.5;
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
    return List.copyOf(fullContour);
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

  private record BorderEdge(Point from, Point to, int direction) {
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
