package com.igormaznitsa.annotator.ui.tools;

import com.igormaznitsa.annotator.api.model.AnnotationCoords;
import com.igormaznitsa.annotator.api.model.AnnotationType;
import com.igormaznitsa.annotator.api.model.NormPoint;
import com.igormaznitsa.annotator.api.model.ObbCorners;
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
import java.util.Optional;
import javax.swing.JOptionPane;

public final class ObboxTool extends AbstractMouseEditTool {

  private final List<NormPoint> corners = new ArrayList<>();
  private boolean freeQuadMode;

  @Override
  public String id() {
    return "obb";
  }

  @Override
  public String tooltip() {
    return "OBB: 3 clicks or Shift+4 corners (class auto-assigned)";
  }

  @Override
  public String iconFileName() {
    return "box_resize.png";
  }

  @Override
  public void deactivate(final EditorPanelContext context) {
    this.clearDrawingState(context);
    super.deactivate(context);
  }

  @Override
  protected boolean hasDrawingInProgress(final EditorPanelContext context) {
    return !this.corners.isEmpty() || this.freeQuadMode;
  }

  @Override
  protected void clearDrawingState(final EditorPanelContext context) {
    this.corners.clear();
    this.freeQuadMode = false;
  }

  @Override
  public boolean completeDrawing(final EditorPanelContext context) {
    if (this.corners.isEmpty()) {
      return false;
    }
    if (this.freeQuadMode) {
      if (this.corners.size() < 4) {
        context.updateStatus("OBB free quad needs 4 corners (%d/4)".formatted(this.corners.size()));
        return true;
      }
      try {
        this.finish(context, ObbCorners.normalize(this.corners));
      } catch (final IllegalArgumentException exception) {
        this.showError(exception.getMessage());
        this.reset(context);
      }
      return true;
    }
    if (this.corners.size() < 3) {
      context.updateStatus(
          "OBB rotated mode needs 3 corners (%d/3)".formatted(this.corners.size()));
      return true;
    }
    try {
      this.finish(context, ObbCorners.fromThreePoints(
          this.corners.get(0),
          this.corners.get(1),
          this.corners.get(2)));
    } catch (final IllegalArgumentException exception) {
      this.showError(exception.getMessage());
      this.reset(context);
    }
    return true;
  }

  @Override
  protected MouseToolAdapter createMouseAdapter() {
    return new MouseToolAdapter() {
      @Override
      public void mousePressed(final EditorPanelContext context, final MouseEvent event) {
        if (event.isShiftDown()) {
          ObboxTool.this.freeQuadMode = true;
        }
      }

      @Override
      public void mouseClicked(final EditorPanelContext context, final MouseEvent event) {
        final Point point = this.imagePoint(context, event);
        final int w = context.image().getWidth();
        final int h = context.image().getHeight();
        final NormPoint norm = Geometry.normalize(w, h, point.x, point.y);
        if (ObboxTool.this.freeQuadMode) {
          ObboxTool.this.addFreeCorner(context, norm);
          return;
        }
        ObboxTool.this.addRotatedCorner(context, norm);
      }

      @Override
      public void mouseMoved(final EditorPanelContext context, final MouseEvent event) {
        ObboxTool.this.updatePreview(context, event);
      }

      @Override
      public void mouseDragged(final EditorPanelContext context, final MouseEvent event) {
        ObboxTool.this.updatePreview(context, event);
      }
    };
  }

  private void addRotatedCorner(final EditorPanelContext context, final NormPoint norm) {
    this.corners.add(norm);
    if (this.corners.size() == 1) {
      context.updateStatus("OBB: click second corner of edge (1/3)");
    } else if (this.corners.size() == 2) {
      context.updateStatus("OBB: click width point (2/3), or Enter/right-click to finish");
    } else if (this.corners.size() == 3) {
      try {
        final List<NormPoint> quad = ObbCorners.fromThreePoints(
            this.corners.get(0),
            this.corners.get(1),
            this.corners.get(2));
        this.finish(context, quad);
      } catch (final IllegalArgumentException exception) {
        this.showError(exception.getMessage());
        this.reset(context);
      }
    }
    this.publishDraft(context, Optional.empty());
  }

  private void addFreeCorner(final EditorPanelContext context, final NormPoint norm) {
    this.corners.add(norm);
    if (this.corners.size() >= 4) {
      context.updateStatus("OBB free quad: 4/4 — Enter or right-click to finish");
    } else {
      context.updateStatus("OBB free quad: corner %d/4 (Shift)".formatted(this.corners.size()));
    }
    if (this.corners.size() == 4) {
      try {
        this.finish(context, ObbCorners.normalize(this.corners));
      } catch (final IllegalArgumentException exception) {
        this.showError(exception.getMessage());
        context.repaintCanvas();
      }
    }
    this.publishDraft(context, Optional.empty());
  }

  private void updatePreview(final EditorPanelContext context, final MouseEvent event) {
    if (!(context instanceof ImageCanvas) || this.corners.isEmpty()) {
      return;
    }
    final Point point = context.imagePointFromScreen(event.getPoint());
    final int w = context.image().getWidth();
    final int h = context.image().getHeight();
    final NormPoint cursor = Geometry.normalize(w, h, point.x, point.y);
    this.publishDraft(context, Optional.of(cursor));
  }

  private void publishDraft(final EditorPanelContext context, final Optional<NormPoint> cursor) {
    if (context instanceof ImageCanvas canvas) {
      canvas.setDraft(new EditorDraft.Obbox(List.copyOf(this.corners), cursor));
    }
    context.repaintCanvas();
  }

  private void finish(final EditorPanelContext context, final List<NormPoint> quad) {
    final AnnotationCoords coords = AnnotationCoords.obb(quad);
    AnnotationCreateFlow.createAndSelect(context, AnnotationType.OBB, coords);
    this.reset(context);
    context.updateStatus("OBB created (4 corners, YOLO order)");
  }

  private void reset(final EditorPanelContext context) {
    this.clearDrawingState(context);
    if (context instanceof ImageCanvas canvas) {
      canvas.clearDraft();
    }
  }

  private void showError(final String message) {
    JOptionPane.showMessageDialog(null, message, "OBB", JOptionPane.ERROR_MESSAGE);
  }
}
