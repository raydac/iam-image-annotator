package com.igormaznitsa.annotator.ui.tools;

import com.igormaznitsa.annotator.ui.api.EditorPanelContext;
import com.igormaznitsa.annotator.ui.api.ImageViewAction;
import com.igormaznitsa.annotator.ui.editor.ImageCanvas;

public final class ZoomInAction implements ImageViewAction {

  @Override
  public String id() {
    return "zoom-in";
  }

  @Override
  public String tooltip() {
    return "Zoom in";
  }

  @Override
  public String iconFileName() {
    return "zoom_in.png";
  }

  @Override
  public void execute(final EditorPanelContext context) {
    if (context instanceof ImageCanvas canvas) {
      canvas.adjustZoom(1.25);
    }
  }
}
