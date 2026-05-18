package com.igormaznitsa.annotator.ui.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.igormaznitsa.annotator.api.model.NormPoint;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeometryTest {

  private static double pixelDistance(final NormPoint a, final NormPoint b, final int width,
                                      final int height) {
    final double dx = (a.x() - b.x()) * width;
    final double dy = (a.y() - b.y()) * height;
    return Math.hypot(dx, dy);
  }

  @Test
  void rotateInImagePreservesPixelDistanceOnWideImage() {
    final int width = 1000;
    final int height = 500;
    final NormPoint center = NormPoint.of(0.5, 0.5);
    final NormPoint point = NormPoint.of(0.8, 0.5);
    final double beforePx = (point.x() - center.x()) * width;
    final NormPoint rotated =
        Geometry.rotateInImage(point, center.x(), center.y(), Math.PI / 2, width, height);
    final double afterPy = Math.abs(rotated.y() - center.y()) * height;
    assertEquals(beforePx, afterPy, 1e-6);
    assertEquals(center.x(), rotated.x(), 1e-6);
  }

  @Test
  void rotateInImageDoesNotShearSquareInPixelSpace() {
    final int size = 800;
    final NormPoint center = NormPoint.of(0.5, 0.5);
    final List<NormPoint> square = List.of(
        NormPoint.of(0.4, 0.4),
        NormPoint.of(0.6, 0.4),
        NormPoint.of(0.6, 0.6),
        NormPoint.of(0.4, 0.6));
    final List<NormPoint> rotated = Geometry.rotatePointsInImage(
        square,
        center.x(),
        center.y(),
        Math.PI / 4,
        size,
        size);
    final double edge01 = pixelDistance(rotated.get(0), rotated.get(1), size, size);
    final double edge12 = pixelDistance(rotated.get(1), rotated.get(2), size, size);
    assertEquals(edge01, edge12, 1e-3);
  }

  @Test
  void rotationHandleStaysInsideImageWhenCenterNearEdge() {
    final int width = 400;
    final int height = 300;
    final NormPoint handle = Geometry.pointOnCircleInImageBounded(
        0.02,
        0.5,
        Math.PI,
        80.0,
        width,
        height);
    assertEquals(0.0, handle.x(), 1e-6);
    assertEquals(0.5, handle.y(), 1e-3);
  }

  @Test
  void constrainedTranslationKeepsPointsInsideImage() {
    final List<NormPoint> points = List.of(NormPoint.of(0.95, 0.5));
    final double[] delta = Geometry.constrainedTranslationDelta(points, 0.2, 0.0);
    assertEquals(0.05, delta[0], 1e-6);
    assertEquals(1.0, points.get(0).x() + delta[0], 1e-6);
  }

  @Test
  void projectsPointOntoHorizontalSegment() {
    final Geometry.SegmentProjection projection = Geometry.projectToSegmentInImage(
        NormPoint.of(0.2, 0.5),
        NormPoint.of(0.8, 0.5),
        0.5,
        0.5,
        1000,
        500);
    assertEquals(0.0, projection.distancePixels(), 1.0);
    assertEquals(0.5, projection.point().x(), 1e-6);
    assertEquals(0.5, projection.point().y(), 1e-6);
  }
}
