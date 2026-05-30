package com.igormaznitsa.annotator.api.render;

import com.igormaznitsa.annotator.api.model.AnnotationCoords;
import com.igormaznitsa.annotator.api.model.AnnotationDocument;
import com.igormaznitsa.annotator.api.model.AnnotationEntry;
import com.igormaznitsa.annotator.api.model.NormPoint;
import com.igormaznitsa.annotator.api.model.ObbCorners;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class AnnotationOverlayRenderer {

  private static final float FILL_ALPHA = 0.35f;
  private static final float DRAFT_FILL_ALPHA = 0.48f;
  private static final float STROKE_ALPHA = 0.9f;
  private static final Color DRAFT_COLOR = new Color(255, 220, 0);
  private static final Color DRAFT_OUTLINE = new Color(0, 0, 0, 200);
  private static final Color HIDDEN_PREVIEW_STROKE = new Color(210, 210, 210);
  private static final BasicStroke HIDDEN_PREVIEW_DASHED_STROKE = new BasicStroke(
      2.0f,
      BasicStroke.CAP_ROUND,
      BasicStroke.JOIN_ROUND,
      0.0f,
      new float[] {8.0f, 6.0f},
      0.0f);

  public BufferedImage compose(final BufferedImage base, final AnnotationDocument document) {
    Objects.requireNonNull(base, "base");
    final BufferedImage composed = new BufferedImage(
        base.getWidth(),
        base.getHeight(),
        BufferedImage.TYPE_INT_ARGB);
    final Graphics2D graphics = composed.createGraphics();
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    graphics.drawImage(base, 0, 0, null);
    this.paintPreviewAnnotations(graphics, base.getWidth(), base.getHeight(), document);
    graphics.dispose();
    return composed;
  }

  private void paintPreviewAnnotations(
      final Graphics2D graphics,
      final int imageWidth,
      final int imageHeight,
      final AnnotationDocument document) {
    for (final AnnotationEntry entry : document.entries()) {
      if (entry.visible()) {
        this.paintAnnotation(graphics, imageWidth, imageHeight, entry, true, false);
      } else {
        this.paintHiddenPreviewAnnotation(graphics, imageWidth, imageHeight, entry);
      }
    }
  }

  public void paintAnnotations(
      final Graphics2D graphics,
      final int imageWidth,
      final int imageHeight,
      final AnnotationDocument document,
      final boolean filled) {
    for (final AnnotationEntry entry : document.entries()) {
      if (!entry.visible()) {
        continue;
      }
      this.paintAnnotation(graphics, imageWidth, imageHeight, entry, filled, false);
    }
  }

  public void paintAnnotation(
      final Graphics2D graphics,
      final int imageWidth,
      final int imageHeight,
      final AnnotationEntry entry,
      final boolean filled,
      final boolean selected) {
    this.paintAnnotation(graphics, imageWidth, imageHeight, entry, filled, selected, 1.0);
  }

  public void paintAnnotation(
      final Graphics2D graphics,
      final int imageWidth,
      final int imageHeight,
      final AnnotationEntry entry,
      final boolean filled,
      final boolean selected,
      final double zoom) {
    if (!entry.visible()) {
      return;
    }
    final Color strokeColor = entry.strokeColor();
    final Color fillColor = entry.fillColor();
    final AnnotationCoords coords = entry.coords();
    final float strokeWidth = (float) Math.max(1.0, (selected ? 3.0 : 2.0) / zoom);
    graphics.setStroke(new BasicStroke(strokeWidth));
    switch (entry.type()) {
      case RECTANGLE ->
          this.paintRectangle(graphics, imageWidth, imageHeight, strokeColor, fillColor, coords,
              filled);
      case POLYGON -> this.paintPolygon(
          graphics, imageWidth, imageHeight, strokeColor, fillColor, coords.points(), filled);
      case POSE2D -> {
        this.paintRectangle(graphics, imageWidth, imageHeight, strokeColor, fillColor, coords,
            false);
        this.paintKeypoints(graphics, imageWidth, imageHeight, strokeColor, coords.points(), zoom);
      }
      case OBB -> this.paintObb(
          graphics, imageWidth, imageHeight, strokeColor, fillColor, coords.corners(), filled,
          selected, zoom);
    }
  }

  private void paintHiddenPreviewAnnotation(
      final Graphics2D graphics,
      final int imageWidth,
      final int imageHeight,
      final AnnotationEntry entry) {
    final AnnotationCoords coords = entry.coords();
    graphics.setStroke(HIDDEN_PREVIEW_DASHED_STROKE);
    switch (entry.type()) {
      case RECTANGLE, POSE2D -> this.paintHiddenPreviewRectangle(
          graphics, imageWidth, imageHeight, coords);
      case POLYGON -> this.paintHiddenPreviewPolygon(graphics, imageWidth, imageHeight,
          coords.points());
      case OBB -> this.paintHiddenPreviewPolygon(graphics, imageWidth, imageHeight,
          coords.corners());
    }
  }

  private void paintHiddenPreviewRectangle(
      final Graphics2D graphics,
      final int imageWidth,
      final int imageHeight,
      final AnnotationCoords coords) {
    graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
    graphics.setColor(HIDDEN_PREVIEW_STROKE);
    graphics.draw(new Rectangle2D.Double(
        coords.x() * imageWidth,
        coords.y() * imageHeight,
        coords.width() * imageWidth,
        coords.height() * imageHeight));
  }

  private void paintHiddenPreviewPolygon(
      final Graphics2D graphics,
      final int imageWidth,
      final int imageHeight,
      final List<NormPoint> points) {
    if (points.size() < 2) {
      return;
    }
    graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
    graphics.setColor(HIDDEN_PREVIEW_STROKE);
    graphics.drawPolygon(this.toPolygon(points, imageWidth, imageHeight));
  }

  private void paintObb(
      final Graphics2D graphics,
      final int imageWidth,
      final int imageHeight,
      final Color strokeColor,
      final Color fillColor,
      final List<NormPoint> corners,
      final boolean filled,
      final boolean selected,
      final double zoom) {
    this.paintPolygon(graphics, imageWidth, imageHeight, strokeColor, fillColor, corners, filled);
    if (!selected || corners.size() != 4) {
      return;
    }
    graphics.setColor(Color.WHITE);
    graphics.setStroke(new BasicStroke((float) Math.max(1.0, 1.0 / zoom)));
    for (int i = 0; i < 4; i++) {
      final NormPoint point = corners.get(i);
      final double px = point.x() * imageWidth;
      final double py = point.y() * imageHeight;
      graphics.drawString(String.valueOf(i + 1), (float) px + 6f, (float) py - 4f);
    }
  }

  public void paintDraftObbox(
      final Graphics2D graphics,
      final int imageWidth,
      final int imageHeight,
      final List<NormPoint> corners,
      final NormPoint cursor,
      final double zoom) {
    if (corners.size() == 1 && cursor != null) {
      this.paintDraftPolyline(graphics, imageWidth, imageHeight, List.of(corners.get(0), cursor),
          false, zoom);
      return;
    }
    if (corners.size() == 2 && cursor != null) {
      try {
        final List<NormPoint> preview = ObbCorners.fromThreePoints(
            corners.get(0),
            corners.get(1),
            cursor);
        this.paintDraftPolyline(graphics, imageWidth, imageHeight, preview, true, zoom);
        this.paintDraftPolyline(graphics, imageWidth, imageHeight, corners, false, zoom);
      } catch (final IllegalArgumentException ignored) {
        this.paintDraftPolyline(graphics, imageWidth, imageHeight,
            List.of(corners.get(0), corners.get(1), cursor), false, zoom);
      }
      return;
    }
    if (corners.size() >= 2) {
      final boolean closed = corners.size() >= 4;
      this.paintDraftPolyline(graphics, imageWidth, imageHeight, corners, closed, zoom);
    }
  }

  private void paintRectangle(
      final Graphics2D graphics,
      final int imageWidth,
      final int imageHeight,
      final Color strokeColor,
      final Color fillColor,
      final AnnotationCoords coords,
      final boolean filled) {
    final Rectangle2D.Double rect = new Rectangle2D.Double(
        coords.x() * imageWidth,
        coords.y() * imageHeight,
        coords.width() * imageWidth,
        coords.height() * imageHeight);
    if (filled) {
      graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, FILL_ALPHA));
      graphics.setColor(fillColor);
      graphics.fill(rect);
    }
    graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, STROKE_ALPHA));
    graphics.setColor(strokeColor);
    graphics.draw(rect);
  }

  private void paintPolygon(
      final Graphics2D graphics,
      final int imageWidth,
      final int imageHeight,
      final Color strokeColor,
      final Color fillColor,
      final List<NormPoint> points,
      final boolean filled) {
    if (points.size() < 2) {
      return;
    }
    final Polygon polygon = new Polygon();
    for (final NormPoint point : points) {
      polygon.addPoint(
          (int) Math.round(point.x() * imageWidth),
          (int) Math.round(point.y() * imageHeight));
    }
    if (filled && points.size() >= 3) {
      graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, FILL_ALPHA));
      graphics.setColor(fillColor);
      graphics.fillPolygon(polygon);
    }
    graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, STROKE_ALPHA));
    graphics.setColor(strokeColor);
    graphics.drawPolygon(polygon);
  }

  public void paintDraftRectangle(
      final Graphics2D graphics,
      final int imageWidth,
      final int imageHeight,
      final NormPoint from,
      final NormPoint to,
      final double zoom) {
    final double x = Math.min(from.x(), to.x());
    final double y = Math.min(from.y(), to.y());
    final double width = Math.abs(to.x() - from.x());
    final double height = Math.abs(to.y() - from.y());
    final AnnotationCoords coords = AnnotationCoords.rectangle(x, y, width, height);
    this.paintDraftFilledShape(graphics, imageWidth, imageHeight, coords, zoom, true);
  }

  public void paintDraftPolyline(
      final Graphics2D graphics,
      final int imageWidth,
      final int imageHeight,
      final List<NormPoint> points,
      final boolean closed,
      final double zoom) {
    this.paintDraftPolyline(graphics, imageWidth, imageHeight, points, closed, Optional.empty(),
        zoom);
  }

  public void paintDraftPolyline(
      final Graphics2D graphics,
      final int imageWidth,
      final int imageHeight,
      final List<NormPoint> points,
      final boolean closed,
      final Optional<NormPoint> cursor,
      final double zoom) {
    if (points.isEmpty()) {
      return;
    }
    final boolean showFill = points.size() >= 3;
    if (showFill) {
      graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DRAFT_FILL_ALPHA));
      graphics.setColor(DRAFT_COLOR);
      final Polygon fill = this.toPolygon(points, imageWidth, imageHeight);
      graphics.fillPolygon(fill);
    }
    if (points.size() >= 2) {
      this.paintDraftStroke(graphics, imageWidth, imageHeight, points, closed || showFill, zoom);
    }
    cursor.ifPresent(point -> {
      if (points.isEmpty()) {
        return;
      }
      final NormPoint last = points.get(points.size() - 1);
      this.paintDraftSegment(graphics, imageWidth, imageHeight, last, point, zoom, true);
    });
    if (showFill && !closed) {
      this.paintDraftSegment(
          graphics,
          imageWidth,
          imageHeight,
          points.get(points.size() - 1),
          points.get(0),
          zoom,
          true);
    }
    for (final NormPoint point : points) {
      this.paintDraftVertex(graphics, imageWidth, imageHeight, point, zoom);
    }
  }

  private void paintDraftFilledShape(
      final Graphics2D graphics,
      final int imageWidth,
      final int imageHeight,
      final AnnotationCoords coords,
      final double zoom,
      final boolean dashedOutline) {
    graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DRAFT_FILL_ALPHA));
    graphics.setColor(DRAFT_COLOR);
    final Rectangle2D.Double rect = new Rectangle2D.Double(
        coords.x() * imageWidth,
        coords.y() * imageHeight,
        coords.width() * imageWidth,
        coords.height() * imageHeight);
    graphics.fill(rect);
    final float strokeWidth = this.draftStrokeWidth(zoom);
    final BasicStroke outlineStroke = dashedOutline
        ? new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0,
        new float[] {10f, 7f}, 0)
        : new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    graphics.setStroke(
        new BasicStroke(strokeWidth + 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.setColor(DRAFT_OUTLINE);
    graphics.draw(rect);
    graphics.setStroke(outlineStroke);
    graphics.setColor(DRAFT_COLOR);
    graphics.draw(rect);
  }

  private void paintDraftStroke(
      final Graphics2D graphics,
      final int imageWidth,
      final int imageHeight,
      final List<NormPoint> points,
      final boolean closed,
      final double zoom) {
    final Polygon poly = this.toPolygon(points, imageWidth, imageHeight);
    final float strokeWidth = this.draftStrokeWidth(zoom);
    graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    graphics.setStroke(
        new BasicStroke(strokeWidth + 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.setColor(DRAFT_OUTLINE);
    if (closed) {
      graphics.drawPolygon(poly);
    } else {
      graphics.drawPolyline(poly.xpoints, poly.ypoints, points.size());
    }
    graphics.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.setColor(DRAFT_COLOR);
    if (closed) {
      graphics.drawPolygon(poly);
    } else {
      graphics.drawPolyline(poly.xpoints, poly.ypoints, points.size());
    }
  }

  private void paintDraftSegment(
      final Graphics2D graphics,
      final int imageWidth,
      final int imageHeight,
      final NormPoint from,
      final NormPoint to,
      final double zoom,
      final boolean dashed) {
    final int x1 = (int) Math.round(from.x() * imageWidth);
    final int y1 = (int) Math.round(from.y() * imageHeight);
    final int x2 = (int) Math.round(to.x() * imageWidth);
    final int y2 = (int) Math.round(to.y() * imageHeight);
    final float strokeWidth = this.draftStrokeWidth(zoom);
    final BasicStroke stroke = dashed
        ? new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0,
        new float[] {8f, 6f}, 0)
        : new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    graphics.setStroke(
        new BasicStroke(strokeWidth + 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    graphics.setColor(DRAFT_OUTLINE);
    graphics.drawLine(x1, y1, x2, y2);
    graphics.setStroke(stroke);
    graphics.setColor(DRAFT_COLOR);
    graphics.drawLine(x1, y1, x2, y2);
  }

  private void paintDraftVertex(
      final Graphics2D graphics,
      final int imageWidth,
      final int imageHeight,
      final NormPoint point,
      final double zoom) {
    final double px = point.x() * imageWidth;
    final double py = point.y() * imageHeight;
    final double radius = this.draftVertexRadius(zoom);
    graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    graphics.setColor(DRAFT_OUTLINE);
    graphics.fill(
        new Ellipse2D.Double(px - radius - 1, py - radius - 1, (radius + 1) * 2, (radius + 1) * 2));
    graphics.setColor(Color.WHITE);
    graphics.fill(new Ellipse2D.Double(px - radius, py - radius, radius * 2, radius * 2));
    graphics.setColor(DRAFT_COLOR);
    graphics.fill(
        new Ellipse2D.Double(px - radius * 0.55, py - radius * 0.55, radius * 1.1, radius * 1.1));
  }

  private Polygon toPolygon(final List<NormPoint> points, final int imageWidth,
                            final int imageHeight) {
    final Polygon polygon = new Polygon();
    for (final NormPoint point : points) {
      polygon.addPoint(
          (int) Math.round(point.x() * imageWidth),
          (int) Math.round(point.y() * imageHeight));
    }
    return polygon;
  }

  private float draftStrokeWidth(final double zoom) {
    return (float) Math.max(2.5, 3.5 / zoom);
  }

  private double draftVertexRadius(final double zoom) {
    return Math.max(5.0, 7.0 / zoom);
  }

  private void paintKeypoints(
      final Graphics2D graphics,
      final int imageWidth,
      final int imageHeight,
      final Color color,
      final List<NormPoint> points,
      final double zoom) {
    final double radius = Math.max(3.0, 5.0 / zoom);
    graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, STROKE_ALPHA));
    graphics.setColor(color);
    for (final NormPoint point : points) {
      if (point.visibility() == 0) {
        continue;
      }
      final double px = point.x() * imageWidth;
      final double py = point.y() * imageHeight;
      graphics.fill(new Ellipse2D.Double(px - radius, py - radius, radius * 2, radius * 2));
    }
  }
}
