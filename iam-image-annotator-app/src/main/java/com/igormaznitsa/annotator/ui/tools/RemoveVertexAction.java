package com.igormaznitsa.annotator.ui.tools;

import com.igormaznitsa.annotator.ui.api.EditorPanelContext;
import com.igormaznitsa.annotator.ui.api.ImageViewAction;
import com.igormaznitsa.annotator.ui.editor.VertexEditOperations;

public final class RemoveVertexAction implements ImageViewAction {

  @Override
  public String id() {
    return "remove-vertex";
  }

  @Override
  public String tooltip() {
    return "Remove selected point";
  }

  @Override
  public String iconFileName() {
    return "chart_curve_delete.png";
  }

  @Override
  public String tooltip(final EditorPanelContext context) {
    return VertexEditOperations.canRemoveVertex(context)
        ? "Remove the selected point handle (Delete)"
        : "Select a point handle on an unlocked polygon or OBB";
  }

  @Override
  public boolean isEnabled(final EditorPanelContext context) {
    return VertexEditOperations.canRemoveVertex(context);
  }

  @Override
  public void execute(final EditorPanelContext context) {
    VertexEditOperations.removeSelectedVertex(context);
  }
}
