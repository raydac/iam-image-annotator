package com.igormaznitsa.annotator.ui.tools;

import com.igormaznitsa.annotator.api.model.AnnotationCoords;
import com.igormaznitsa.annotator.api.model.AnnotationType;
import com.igormaznitsa.annotator.api.model.NormPoint;
import com.igormaznitsa.annotator.ui.api.EditorPanelContext;
import com.igormaznitsa.annotator.ui.editor.AnnotationCreateFlow;
import com.igormaznitsa.annotator.ui.editor.EditorDraft;
import com.igormaznitsa.annotator.ui.editor.Geometry;
import com.igormaznitsa.annotator.ui.editor.ImageCanvas;
import com.igormaznitsa.annotator.ui.editor.MouseToolAdapter;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class PolygonTool extends AbstractMouseEditTool {

  private static final int MIN_VERTICES = 3;

  private final List<NormPoint> points = new ArrayList<>();

  @Override
  public String id() {
    return "polygon";
  }

  @Override
  public String tooltip() {
    return "Draw polygon (class auto-assigned); Enter or right-click to finish";
  }

  @Override
  public String iconFileName() {
    return "draw_polygon.png";
  }

  @Override
  public void deactivate(final EditorPanelContext context) {
    this.clearDrawingState(context);
    super.deactivate(context);
  }

  @Override
  public boolean completeDrawing(final EditorPanelContext context) {
    if (this.points.isEmpty()) {
      return false;
    }
    if (this.points.size() < MIN_VERTICES) {
      context.updateStatus(String.format(
          Locale.ROOT,
          "Polygon needs at least %d points (%d placed)",
          MIN_VERTICES,
          this.points.size()));
      return true;
    }
    this.finish(context);
    return true;
  }

  @Override
  protected boolean hasDrawingInProgress(final EditorPanelContext context) {
    return !this.points.isEmpty();
  }

  @Override
  protected void clearDrawingState(final EditorPanelContext context) {
    this.points.clear();
  }

  @Override
  protected MouseToolAdapter createMouseAdapter() {
    return new MouseToolAdapter() {
      @Override
      public void mousePressed(final EditorPanelContext context, final MouseEvent event) {
        if (event.getButton() != MouseEvent.BUTTON1 || event.getClickCount() != 1) {
          return;
        }
        final Point point = this.imagePoint(context, event);
        final int w = context.image().getWidth();
        final int h = context.image().getHeight();
        PolygonTool.this.points.add(Geometry.normalize(w, h, point.x, point.y));
        PolygonTool.this.publishDraft(context, Optional.empty());
        PolygonTool.this.updatePlacementStatus(context);
        context.repaintCanvas();
      }

      @Override
      public void mouseMoved(final EditorPanelContext context, final MouseEvent event) {
        if (PolygonTool.this.points.isEmpty() || !(context instanceof ImageCanvas)) {
          return;
        }
        final Point point = this.imagePoint(context, event);
        final int w = context.image().getWidth();
        final int h = context.image().getHeight();
        PolygonTool.this.publishDraft(
            context,
            Optional.of(Geometry.normalize(w, h, point.x, point.y)));
        context.repaintCanvas();
      }
    };
  }

  private void updatePlacementStatus(final EditorPanelContext context) {
    if (this.points.size() >= MIN_VERTICES) {
      context.updateStatus(String.format(
          Locale.ROOT,
          "Polygon points: %d — Enter or right-click to finish",
          this.points.size()));
      return;
    }
    context.updateStatus(String.format(
        Locale.ROOT,
        "Polygon points: %d/%d",
        this.points.size(),
        MIN_VERTICES));
  }

  private void publishDraft(final EditorPanelContext context, final Optional<NormPoint> cursor) {
    if (context instanceof ImageCanvas canvas) {
      canvas.setDraft(new EditorDraft.Polyline(
          List.copyOf(this.points),
          this.points.size() >= MIN_VERTICES,
          cursor));
    }
  }

  private void finish(final EditorPanelContext context) {
    final AnnotationCoords coords = AnnotationCoords.polygon(List.copyOf(this.points));
    AnnotationCreateFlow.createAndSelect(context, AnnotationType.POLYGON, coords);
    this.points.clear();
  }
}
