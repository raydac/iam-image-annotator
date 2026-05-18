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

public final class RectangleTool extends AbstractMouseEditTool {

  private NormPoint start;

  @Override
  public String id() {
    return "rectangle";
  }

  @Override
  public String tooltip() {
    return "Draw rectangle (class auto-assigned; double-click shape to edit class)";
  }

  @Override
  public String iconFileName() {
    return "shape_square.png";
  }

  @Override
  public void deactivate(final EditorPanelContext context) {
    this.clearDrawingState(context);
    super.deactivate(context);
  }

  @Override
  protected boolean hasDrawingInProgress(final EditorPanelContext context) {
    return this.start != null;
  }

  @Override
  protected void clearDrawingState(final EditorPanelContext context) {
    this.start = null;
  }

  @Override
  protected MouseToolAdapter createMouseAdapter() {
    return new MouseToolAdapter() {
      @Override
      public void mousePressed(final EditorPanelContext context, final MouseEvent event) {
        final Point point = this.imagePoint(context, event);
        final int w = context.image().getWidth();
        final int h = context.image().getHeight();
        RectangleTool.this.start = Geometry.normalize(w, h, point.x, point.y);
      }

      @Override
      public void mouseDragged(final EditorPanelContext context, final MouseEvent event) {
        if (RectangleTool.this.start == null || !(context instanceof ImageCanvas canvas)) {
          return;
        }
        final Point point = this.imagePoint(context, event);
        final int w = context.image().getWidth();
        final int h = context.image().getHeight();
        final NormPoint end = Geometry.normalize(w, h, point.x, point.y);
        canvas.setDraft(new EditorDraft.Rectangle(RectangleTool.this.start, end));
      }

      @Override
      public void mouseReleased(final EditorPanelContext context, final MouseEvent event) {
        if (RectangleTool.this.start == null) {
          return;
        }
        final Point point = this.imagePoint(context, event);
        final int w = context.image().getWidth();
        final int h = context.image().getHeight();
        final NormPoint end = Geometry.normalize(w, h, point.x, point.y);
        final NormPoint[] bounds = Geometry.rectangleFromDrag(RectangleTool.this.start, end);
        final double width = bounds[1].x() - bounds[0].x();
        final double height = bounds[1].y() - bounds[0].y();
        RectangleTool.this.start = null;
        if (width < 0.005 || height < 0.005) {
          if (context instanceof ImageCanvas canvas) {
            canvas.clearDraft();
          }
          return;
        }
        final AnnotationCoords coords = AnnotationCoords.rectangle(
            bounds[0].x(),
            bounds[0].y(),
            width,
            height);
        AnnotationCreateFlow.createAndSelect(context, AnnotationType.RECTANGLE, coords);
      }
    };
  }
}
