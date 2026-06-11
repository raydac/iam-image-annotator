package com.igormaznitsa.annotator.ui.tools;

import com.igormaznitsa.annotator.ui.api.EditImageTool;
import com.igormaznitsa.annotator.ui.api.EditorPanelContext;
import com.igormaznitsa.annotator.ui.editor.ImageCanvas;
import com.igormaznitsa.annotator.ui.editor.MouseToolAdapter;

public abstract class AbstractMouseEditTool implements EditImageTool {

  private final MouseToolAdapter mouseAdapter = this.createMouseAdapter();

  protected abstract MouseToolAdapter createMouseAdapter();

  @Override
  public boolean isExclusive() {
    return true;
  }

  @Override
  public void activate(final EditorPanelContext context) {
    if (this.clearsSelectionOnActivate() && context.selectedAnnotation().isPresent()) {
      context.clearSelection();
      context.repaintCanvas();
    }
    this.mouseAdapter.activate(context);
    if (context instanceof ImageCanvas canvas) {
      canvas.setActiveMouseTool(this.mouseAdapter);
      canvas.setActiveEditTool(this);
    }
  }

  @Override
  public void deactivate(final EditorPanelContext context) {
    this.mouseAdapter.deactivate(context);
    if (context instanceof ImageCanvas canvas) {
      canvas.setActiveMouseTool(null);
      canvas.setActiveEditTool(null);
    }
  }

  @Override
  public boolean cancelDrawing(final EditorPanelContext context) {
    if (!this.hasDrawingInProgress(context)) {
      return false;
    }
    this.clearDrawingState(context);
    if (context instanceof ImageCanvas canvas) {
      canvas.clearDraft();
    }
    context.repaintCanvas();
    context.updateStatus("Drawing cancelled");
    return true;
  }

  protected boolean hasDrawingInProgress(final EditorPanelContext context) {
    return context instanceof ImageCanvas canvas && canvas.hasDraft();
  }

  protected void clearDrawingState(final EditorPanelContext context) {
  }

  protected boolean clearsSelectionOnActivate() {
    return true;
  }
}
