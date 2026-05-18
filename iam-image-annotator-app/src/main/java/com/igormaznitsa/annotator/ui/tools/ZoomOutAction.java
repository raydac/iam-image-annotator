package com.igormaznitsa.annotator.ui.tools;

import com.igormaznitsa.annotator.ui.api.EditorPanelContext;
import com.igormaznitsa.annotator.ui.api.ImageViewAction;
import com.igormaznitsa.annotator.ui.editor.ImageCanvas;

public final class ZoomOutAction implements ImageViewAction {

  @Override
  public String id() {
    return "zoom-out";
  }

  @Override
  public String tooltip() {
    return "Zoom out";
  }

  @Override
  public String iconFileName() {
    return "zoom_out.png";
  }

  @Override
  public void execute(final EditorPanelContext context) {
    if (context instanceof ImageCanvas canvas) {
      canvas.adjustZoom(0.8);
    }
  }
}
