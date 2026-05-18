package com.igormaznitsa.annotator.ui.tools;

import com.igormaznitsa.annotator.ui.api.EditorPanelContext;
import com.igormaznitsa.annotator.ui.api.ImageViewAction;
import com.igormaznitsa.annotator.ui.editor.VertexEditOperations;

public final class AddVertexAction implements ImageViewAction {

  @Override
  public String id() {
    return "add-vertex";
  }

  @Override
  public String tooltip() {
    return "Add point on edge";
  }

  @Override
  public String iconFileName() {
    return "chart_curve_add.png";
  }

  @Override
  public String tooltip(final EditorPanelContext context) {
    return VertexEditOperations.canAddVertex(context)
        ? "Add point: click here, then click a shape edge on the image (Esc to cancel)"
        : "Select an unlocked polygon or OBB to add points";
  }

  @Override
  public boolean isEnabled(final EditorPanelContext context) {
    return VertexEditOperations.canAddVertex(context);
  }

  @Override
  public void execute(final EditorPanelContext context) {
    VertexEditOperations.armAddVertex(context);
  }
}
