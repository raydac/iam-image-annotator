package com.igormaznitsa.annotator.ui.editor;

import com.igormaznitsa.annotator.api.model.AnnotationCoords;
import com.igormaznitsa.annotator.api.model.AnnotationEntry;
import com.igormaznitsa.annotator.api.model.AnnotationType;
import com.igormaznitsa.annotator.api.model.NormPoint;
import com.igormaznitsa.annotator.api.model.ObbCorners;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AnnotationSelectionEditor {

  private static final double HANDLE_RADIUS_NORM = 0.012;
  private static final double ROTATION_HANDLE_RADIUS_PX = 10.0;
  private static final double ROTATION_ARM_RADIUS_PX = 56.0;
  /**
   * Upward arm in image pixel space (y grows downward).
   */
  private static final double ROTATION_ARM_ANGLE_RAD = -Math.PI / 2.0;
  private static final double EDGE_HIT_TOLERANCE_PX = 10.0;
  private static final int POLYGON_MIN_VERTICES = 3;

  private AnnotationSelectionEditor() {
  }

  public static boolean supportsRotation(final AnnotationType type) {
    return switch (type) {
      case RECTANGLE, OBB, POLYGON, POSE2D -> true;
    };
  }

  public static boolean supportsMutableVertices(final AnnotationType type) {
    return switch (type) {
      case POLYGON, POSE2D, OBB -> true;
      case RECTANGLE -> false;
    };
  }

  public static Optional<VertexInsertion> findVertexInsertion(
      final AnnotationEntry entry,
      final double normX,
      final double normY,
      final int imageWidth,
      final int imageHeight) {
    return switch (entry.type()) {
      case POLYGON ->
          findPolygonEdgeInsertion(entry.coords().points(), normX, normY, imageWidth, imageHeight);
      case OBB ->
          findPolygonEdgeInsertion(entry.coords().corners(), normX, normY, imageWidth, imageHeight);
      case POSE2D -> findPoseKeypointInsertion(entry, normX, normY);
      case RECTANGLE -> Optional.empty();
    };
  }

  public static Optional<ShapeTransformResult> insertVertex(
      final AnnotationEntry entry,
      final VertexInsertion insertion) {
    return switch (entry.type()) {
      case POLYGON -> insertPolygonVertex(entry.coords(), insertion)
          .map(coords -> new ShapeTransformResult(coords, AnnotationType.POLYGON));
      case OBB -> insertCornerVertex(entry.coords(), insertion);
      case POSE2D -> insertPoseKeypoint(entry.coords(), insertion)
          .map(coords -> new ShapeTransformResult(coords, AnnotationType.POSE2D));
      case RECTANGLE -> Optional.empty();
    };
  }

  public static Optional<VertexInsertion> vertexInsertionAfter(
      final AnnotationEntry entry,
      final int selectedVertexIndex) {
    return switch (entry.type()) {
      case POLYGON -> closedChainInsertionAfter(entry.coords().points(), selectedVertexIndex);
      case OBB -> closedChainInsertionAfter(entry.coords().corners(), selectedVertexIndex);
      case POSE2D -> poseKeypointInsertionAfter(entry.coords(), selectedVertexIndex);
      case RECTANGLE -> Optional.empty();
    };
  }

  public static boolean canRemoveVertex(final AnnotationEntry entry, final int vertexIndex) {
    return switch (entry.type()) {
      case POLYGON -> entry.coords().points().size() > POLYGON_MIN_VERTICES;
      case OBB -> entry.coords().corners().size() > POLYGON_MIN_VERTICES;
      case POSE2D -> vertexIndex >= 4;
      case RECTANGLE -> false;
    };
  }

  public static Optional<ShapeTransformResult> removeVertex(final AnnotationEntry entry,
                                                            final int vertexIndex) {
    if (!canRemoveVertex(entry, vertexIndex)) {
      return Optional.empty();
    }
    return switch (entry.type()) {
      case POLYGON -> removePolygonVertex(entry.coords(), vertexIndex)
          .map(coords -> new ShapeTransformResult(coords, AnnotationType.POLYGON));
      case OBB -> removeCornerVertex(entry.coords(), vertexIndex);
      case POSE2D -> removePoseKeypoint(entry.coords(), vertexIndex)
          .map(coords -> new ShapeTransformResult(coords, AnnotationType.POSE2D));
      case RECTANGLE -> Optional.empty();
    };
  }

  public static List<NormPoint> handlePositions(final AnnotationEntry entry) {
    return switch (entry.type()) {
      case RECTANGLE -> rectangleCorners(entry.coords());
      case POLYGON -> List.copyOf(entry.coords().points());
      case OBB -> List.copyOf(entry.coords().corners());
      case POSE2D -> poseHandles(entry.coords());
    };
  }

  public static Optional<NormPoint> rotationHandlePosition(
      final AnnotationEntry entry,
      final int imageWidth,
      final int imageHeight) {
    return rotationHandlePosition(entry, imageWidth, imageHeight, Optional.empty());
  }

  public static Optional<NormPoint> rotationHandlePosition(
      final AnnotationEntry entry,
      final int imageWidth,
      final int imageHeight,
      final Optional<Double> armAngleRad) {
    if (!supportsRotation(entry.type())) {
      return Optional.empty();
    }
    final NormPoint center = centroid(entry);
    final double angle = armAngleRad.orElse(ROTATION_ARM_ANGLE_RAD);
    return Optional.of(Geometry.pointOnCircleInImageBounded(
        center.x(),
        center.y(),
        angle,
        ROTATION_ARM_RADIUS_PX,
        imageWidth,
        imageHeight));
  }

  public static Optional<AnnotationHandle> hitHandle(
      final AnnotationEntry entry,
      final double normX,
      final double normY,
      final int imageWidth,
      final int imageHeight) {
    if (!entry.visible()) {
      return Optional.empty();
    }
    final Optional<NormPoint> rotation = rotationHandlePosition(entry, imageWidth, imageHeight);
    if (rotation.isPresent()
        && isNearInImage(rotation.get(), normX, normY, ROTATION_HANDLE_RADIUS_PX, imageWidth,
        imageHeight)) {
      return Optional.of(new AnnotationHandle.Rotate(entry.id()));
    }
    final List<NormPoint> handles = handlePositions(entry);
    for (int i = 0; i < handles.size(); i++) {
      if (isNear(handles.get(i), normX, normY, HANDLE_RADIUS_NORM)) {
        return Optional.of(new AnnotationHandle.Vertex(entry.id(), i));
      }
    }
    if (contains(entry, normX, normY)) {
      return Optional.of(new AnnotationHandle.Body(entry.id()));
    }
    return Optional.empty();
  }

  public static Optional<String> hitAnnotation(
      final List<AnnotationEntry> entries,
      final double normX,
      final double normY) {
    for (int i = entries.size() - 1; i >= 0; i--) {
      final AnnotationEntry entry = entries.get(i);
      if (entry.visible() && contains(entry, normX, normY)) {
        return Optional.of(entry.id());
      }
    }
    return Optional.empty();
  }

  public static NormPoint centroid(final AnnotationEntry entry) {
    return centroid(entry.type(), entry.coords());
  }

  public static ShapeTransformResult rotate(
      final AnnotationType type,
      final AnnotationCoords coords,
      final double angleRad,
      final int imageWidth,
      final int imageHeight) {
    final NormPoint center = centroid(type, coords);
    final ShapeTransformResult result = switch (type) {
      case RECTANGLE -> {
        final List<NormPoint> corners = Geometry.rotatePointsInImage(
            rectangleCorners(coords),
            center.x(),
            center.y(),
            angleRad,
            imageWidth,
            imageHeight);
        yield new ShapeTransformResult(
            AnnotationCoords.obb(ObbCorners.normalize(corners)),
            AnnotationType.OBB);
      }
      case OBB -> new ShapeTransformResult(
          AnnotationCoords.obb(ObbCorners.normalize(Geometry.rotatePointsInImage(
              coords.corners(),
              center.x(),
              center.y(),
              angleRad,
              imageWidth,
              imageHeight))),
          AnnotationType.OBB);
      case POLYGON -> new ShapeTransformResult(
          AnnotationCoords.polygon(Geometry.rotatePointsInImage(
              coords.points(),
              center.x(),
              center.y(),
              angleRad,
              imageWidth,
              imageHeight)),
          AnnotationType.POLYGON);
      case POSE2D -> new ShapeTransformResult(
          rotatePose(coords, center, angleRad, imageWidth, imageHeight),
          AnnotationType.POSE2D);
    };
    return new ShapeTransformResult(clampCoords(result.coords(), result.type()), result.type());
  }

  public static AnnotationCoords translate(final AnnotationCoords coords, final AnnotationType type,
                                           final double dx, final double dy) {
    return clampCoords(switch (type) {
      case RECTANGLE -> {
        final double[] delta =
            Geometry.constrainedTranslationDelta(rectangleCorners(coords), dx, dy);
        yield AnnotationCoords.rectangle(
            coords.x() + delta[0],
            coords.y() + delta[1],
            coords.width(),
            coords.height());
      }
      case POLYGON -> AnnotationCoords.polygon(translatePoints(coords.points(), dx, dy));
      case OBB -> AnnotationCoords.obb(translatePoints(coords.corners(), dx, dy));
      case POSE2D -> {
        final List<NormPoint> anchors = new ArrayList<>(rectangleCorners(coords));
        anchors.addAll(coords.points());
        final double[] delta = Geometry.constrainedTranslationDelta(anchors, dx, dy);
        final List<NormPoint> keypoints = coords.points().stream()
            .map(point -> new NormPoint(
                point.x() + delta[0],
                point.y() + delta[1],
                point.visibility()))
            .toList();
        yield AnnotationCoords.pose(
            coords.x() + delta[0],
            coords.y() + delta[1],
            coords.width(),
            coords.height(),
            keypoints);
      }
    }, type);
  }

  public static AnnotationCoords moveVertex(
      final AnnotationEntry entry,
      final int vertexIndex,
      final double normX,
      final double normY) {
    final double x = Geometry.clamp01(normX);
    final double y = Geometry.clamp01(normY);
    return clampCoords(switch (entry.type()) {
      case RECTANGLE -> resizeRectangle(entry.coords(), vertexIndex, x, y);
      case POLYGON -> movePolygonVertex(entry.coords(), vertexIndex, x, y);
      case OBB -> moveObbCorner(entry.coords(), vertexIndex, x, y);
      case POSE2D -> movePoseVertex(entry.coords(), vertexIndex, x, y);
    }, entry.type());
  }

  private static NormPoint centroid(final AnnotationType type, final AnnotationCoords coords) {
    return switch (type) {
      case RECTANGLE, POSE2D -> NormPoint.of(
          coords.x() + coords.width() / 2.0,
          coords.y() + coords.height() / 2.0);
      case POLYGON -> Geometry.centroid(coords.points());
      case OBB -> Geometry.centroid(coords.corners());
    };
  }

  private static AnnotationCoords rotatePose(
      final AnnotationCoords coords,
      final NormPoint center,
      final double angleRad,
      final int imageWidth,
      final int imageHeight) {
    final List<NormPoint> rotatedBox = Geometry.rotatePointsInImage(
        rectangleCorners(coords),
        center.x(),
        center.y(),
        angleRad,
        imageWidth,
        imageHeight);
    final List<NormPoint> rotatedKeypoints = Geometry.rotatePointsInImage(
        coords.points(),
        center.x(),
        center.y(),
        angleRad,
        imageWidth,
        imageHeight);
    double minX = Double.MAX_VALUE;
    double minY = Double.MAX_VALUE;
    double maxX = Double.MIN_VALUE;
    double maxY = Double.MIN_VALUE;
    for (final NormPoint corner : rotatedBox) {
      minX = Math.min(minX, corner.x());
      minY = Math.min(minY, corner.y());
      maxX = Math.max(maxX, corner.x());
      maxY = Math.max(maxY, corner.y());
    }
    return clampCoords(
        AnnotationCoords.pose(minX, minY, maxX - minX, maxY - minY, rotatedKeypoints),
        AnnotationType.POSE2D);
  }

  private static AnnotationCoords clampCoords(final AnnotationCoords coords,
                                              final AnnotationType type) {
    return switch (type) {
      case RECTANGLE, POSE2D -> {
        final double x = Geometry.clamp01(coords.x());
        final double y = Geometry.clamp01(coords.y());
        final double width = Math.max(0.0, Math.min(coords.width(), 1.0 - x));
        final double height = Math.max(0.0, Math.min(coords.height(), 1.0 - y));
        if (type == AnnotationType.RECTANGLE) {
          yield AnnotationCoords.rectangle(x, y, width, height);
        }
        yield AnnotationCoords.pose(x, y, width, height, Geometry.clampNormPoints(coords.points()));
      }
      case POLYGON -> AnnotationCoords.polygon(Geometry.clampNormPoints(coords.points()));
      case OBB ->
          AnnotationCoords.obb(ObbCorners.normalize(Geometry.clampNormPoints(coords.corners())));
    };
  }

  private static List<NormPoint> rectangleCorners(final AnnotationCoords coords) {
    final double x = coords.x();
    final double y = coords.y();
    final double w = coords.width();
    final double h = coords.height();
    return List.of(
        NormPoint.of(x, y),
        NormPoint.of(x + w, y),
        NormPoint.of(x + w, y + h),
        NormPoint.of(x, y + h));
  }

  private static List<NormPoint> poseHandles(final AnnotationCoords coords) {
    final List<NormPoint> handles = new ArrayList<>(rectangleCorners(coords));
    handles.addAll(coords.points());
    return List.copyOf(handles);
  }

  private static List<NormPoint> translatePoints(final List<NormPoint> points, final double dx,
                                                 final double dy) {
    final double[] delta = Geometry.constrainedTranslationDelta(points, dx, dy);
    return points.stream()
        .map(point -> new NormPoint(
            point.x() + delta[0],
            point.y() + delta[1],
            point.visibility()))
        .toList();
  }

  private static AnnotationCoords resizeRectangle(
      final AnnotationCoords coords,
      final int cornerIndex,
      final double x,
      final double y) {
    double left = coords.x();
    double top = coords.y();
    double right = coords.x() + coords.width();
    double bottom = coords.y() + coords.height();
    switch (cornerIndex) {
      case 0 -> {
        left = x;
        top = y;
      }
      case 1 -> {
        right = x;
        top = y;
      }
      case 2 -> {
        right = x;
        bottom = y;
      }
      case 3 -> {
        left = x;
        bottom = y;
      }
      default -> {
      }
    }
    final double minX = Geometry.clamp01(Math.min(left, right));
    final double minY = Geometry.clamp01(Math.min(top, bottom));
    final double width = Math.max(0.0, Math.min(Math.abs(right - left), 1.0 - minX));
    final double height = Math.max(0.0, Math.min(Math.abs(bottom - top), 1.0 - minY));
    return AnnotationCoords.rectangle(minX, minY, width, height);
  }

  private static Optional<VertexInsertion> closedChainInsertionAfter(
      final List<NormPoint> points,
      final int selectedVertexIndex) {
    if (selectedVertexIndex < 0 || selectedVertexIndex >= points.size() || points.size() < 2) {
      return Optional.empty();
    }
    final NormPoint current = points.get(selectedVertexIndex);
    final NormPoint next = points.get((selectedVertexIndex + 1) % points.size());
    return Optional.of(new VertexInsertion(
        selectedVertexIndex + 1,
        midpoint(current, next)));
  }

  private static Optional<VertexInsertion> poseKeypointInsertionAfter(
      final AnnotationCoords coords,
      final int selectedVertexIndex) {
    if (selectedVertexIndex < 4) {
      return Optional.empty();
    }
    final List<NormPoint> keypoints = coords.points();
    final int keypointIndex = selectedVertexIndex - 4;
    if (keypointIndex < 0 || keypointIndex >= keypoints.size()) {
      return Optional.empty();
    }
    final NormPoint current = keypoints.get(keypointIndex);
    final NormPoint newPoint;
    if (keypointIndex + 1 < keypoints.size()) {
      newPoint = midpoint(current, keypoints.get(keypointIndex + 1));
    } else if (keypointIndex > 0) {
      final NormPoint previous = keypoints.get(keypointIndex - 1);
      newPoint = NormPoint.of(
          Geometry.clamp01(current.x() + (current.x() - previous.x())),
          Geometry.clamp01(current.y() + (current.y() - previous.y())));
    } else {
      newPoint = NormPoint.of(Geometry.clamp01(current.x() + 0.02), current.y());
    }
    return Optional.of(new VertexInsertion(selectedVertexIndex + 1, newPoint));
  }

  private static NormPoint midpoint(final NormPoint start, final NormPoint end) {
    return new NormPoint(
        (start.x() + end.x()) / 2.0,
        (start.y() + end.y()) / 2.0,
        start.visibility());
  }

  private static Optional<VertexInsertion> findPolygonEdgeInsertion(
      final List<NormPoint> points,
      final double normX,
      final double normY,
      final int imageWidth,
      final int imageHeight) {
    if (points.size() < 2) {
      return Optional.empty();
    }
    double bestDistance = EDGE_HIT_TOLERANCE_PX;
    VertexInsertion best = null;
    for (int i = 0; i < points.size(); i++) {
      final NormPoint start = points.get(i);
      final NormPoint end = points.get((i + 1) % points.size());
      final Geometry.SegmentProjection projection = Geometry.projectToSegmentInImage(
          start,
          end,
          normX,
          normY,
          imageWidth,
          imageHeight);
      if (projection.distancePixels() > bestDistance) {
        continue;
      }
      bestDistance = projection.distancePixels();
      best = new VertexInsertion(i + 1, projection.point());
    }
    return Optional.ofNullable(best);
  }

  private static Optional<VertexInsertion> findPoseKeypointInsertion(
      final AnnotationEntry entry,
      final double normX,
      final double normY) {
    if (!contains(entry, normX, normY)) {
      return Optional.empty();
    }
    final int handleIndex = 4 + entry.coords().points().size();
    return Optional.of(new VertexInsertion(
        handleIndex,
        NormPoint.of(Geometry.clamp01(normX), Geometry.clamp01(normY))));
  }

  private static Optional<AnnotationCoords> insertPolygonVertex(
      final AnnotationCoords coords,
      final VertexInsertion insertion) {
    final List<NormPoint> points = new ArrayList<>(coords.points());
    final int index = Math.max(0, Math.min(insertion.vertexIndex(), points.size()));
    points.add(index, insertion.point());
    return Optional.of(AnnotationCoords.polygon(points));
  }

  private static Optional<AnnotationCoords> insertPoseKeypoint(
      final AnnotationCoords coords,
      final VertexInsertion insertion) {
    if (insertion.vertexIndex() < 4) {
      return Optional.empty();
    }
    final List<NormPoint> keypoints = new ArrayList<>(coords.points());
    keypoints.add(insertion.vertexIndex() - 4, insertion.point());
    return Optional.of(AnnotationCoords.pose(
        coords.x(),
        coords.y(),
        coords.width(),
        coords.height(),
        keypoints));
  }

  private static Optional<AnnotationCoords> removePolygonVertex(
      final AnnotationCoords coords,
      final int vertexIndex) {
    final List<NormPoint> points = new ArrayList<>(coords.points());
    if (vertexIndex < 0 || vertexIndex >= points.size()) {
      return Optional.empty();
    }
    points.remove(vertexIndex);
    return Optional.of(AnnotationCoords.polygon(points));
  }

  private static Optional<ShapeTransformResult> insertCornerVertex(
      final AnnotationCoords coords,
      final VertexInsertion insertion) {
    final List<NormPoint> corners = new ArrayList<>(coords.corners());
    final int index = Math.max(0, Math.min(insertion.vertexIndex(), corners.size()));
    corners.add(index, insertion.point());
    if (corners.size() == 4) {
      return Optional.of(new ShapeTransformResult(
          AnnotationCoords.obb(ObbCorners.normalize(corners)),
          AnnotationType.OBB));
    }
    return Optional.of(
        new ShapeTransformResult(AnnotationCoords.polygon(corners), AnnotationType.POLYGON));
  }

  private static Optional<ShapeTransformResult> removeCornerVertex(
      final AnnotationCoords coords,
      final int vertexIndex) {
    final List<NormPoint> corners = new ArrayList<>(coords.corners());
    if (vertexIndex < 0 || vertexIndex >= corners.size()) {
      return Optional.empty();
    }
    corners.remove(vertexIndex);
    if (corners.size() == 4) {
      return Optional.of(new ShapeTransformResult(
          AnnotationCoords.obb(ObbCorners.normalize(corners)),
          AnnotationType.OBB));
    }
    if (corners.size() >= POLYGON_MIN_VERTICES) {
      return Optional.of(
          new ShapeTransformResult(AnnotationCoords.polygon(corners), AnnotationType.POLYGON));
    }
    return Optional.empty();
  }

  private static Optional<AnnotationCoords> removePoseKeypoint(
      final AnnotationCoords coords,
      final int vertexIndex) {
    if (vertexIndex < 4) {
      return Optional.empty();
    }
    final int keypointIndex = vertexIndex - 4;
    final List<NormPoint> keypoints = new ArrayList<>(coords.points());
    if (keypointIndex < 0 || keypointIndex >= keypoints.size()) {
      return Optional.empty();
    }
    keypoints.remove(keypointIndex);
    return Optional.of(AnnotationCoords.pose(
        coords.x(),
        coords.y(),
        coords.width(),
        coords.height(),
        keypoints));
  }

  private static AnnotationCoords movePolygonVertex(
      final AnnotationCoords coords,
      final int vertexIndex,
      final double x,
      final double y) {
    final List<NormPoint> points = new ArrayList<>(coords.points());
    final NormPoint current = points.get(vertexIndex);
    points.set(vertexIndex, new NormPoint(x, y, current.visibility()));
    return AnnotationCoords.polygon(points);
  }

  private static AnnotationCoords moveObbCorner(
      final AnnotationCoords coords,
      final int cornerIndex,
      final double x,
      final double y) {
    final List<NormPoint> corners = new ArrayList<>(coords.corners());
    final NormPoint current = corners.get(cornerIndex);
    corners.set(cornerIndex, new NormPoint(x, y, current.visibility()));
    ObbCorners.requireValid(corners);
    return new AnnotationCoords(null, null, null, null, List.of(), List.copyOf(corners));
  }

  private static AnnotationCoords movePoseVertex(
      final AnnotationCoords coords,
      final int vertexIndex,
      final double x,
      final double y) {
    if (vertexIndex < 4) {
      return resizeRectangle(coords, vertexIndex, x, y);
    }
    final int keypointIndex = vertexIndex - 4;
    final List<NormPoint> keypoints = new ArrayList<>(coords.points());
    final NormPoint current = keypoints.get(keypointIndex);
    keypoints.set(keypointIndex, new NormPoint(x, y, current.visibility()));
    return AnnotationCoords.pose(coords.x(), coords.y(), coords.width(), coords.height(),
        keypoints);
  }

  private static boolean isNear(
      final NormPoint point,
      final double normX,
      final double normY,
      final double radius) {
    final double dx = point.x() - normX;
    final double dy = point.y() - normY;
    return Math.hypot(dx, dy) <= radius;
  }

  private static boolean isNearInImage(
      final NormPoint point,
      final double normX,
      final double normY,
      final double radiusPixels,
      final int imageWidth,
      final int imageHeight) {
    final double dx = (point.x() - normX) * imageWidth;
    final double dy = (point.y() - normY) * imageHeight;
    return Math.hypot(dx, dy) <= radiusPixels;
  }

  private static boolean contains(final AnnotationEntry entry, final double nx, final double ny) {
    final AnnotationCoords coords = entry.coords();
    return switch (entry.type()) {
      case RECTANGLE, POSE2D -> nx >= coords.x()
          && ny >= coords.y()
          && nx <= coords.x() + coords.width()
          && ny <= coords.y() + coords.height();
      case POLYGON -> pointInPolygon(nx, ny, coords.points());
      case OBB -> pointInPolygon(nx, ny, coords.corners());
    };
  }

  private static boolean pointInPolygon(final double x, final double y,
                                        final List<NormPoint> polygon) {
    boolean inside = false;
    for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
      final NormPoint pi = polygon.get(i);
      final NormPoint pj = polygon.get(j);
      final boolean intersect = (pi.y() > y) != (pj.y() > y)
          && x < (pj.x() - pi.x()) * (y - pi.y()) / (pj.y() - pi.y()) + pi.x();
      if (intersect) {
        inside = !inside;
      }
    }
    return inside;
  }

  public record VertexInsertion(int vertexIndex, NormPoint point) {
  }
}
