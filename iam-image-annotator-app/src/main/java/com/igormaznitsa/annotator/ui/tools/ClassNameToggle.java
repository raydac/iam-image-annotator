package com.igormaznitsa.annotator.ui.tools;

import com.igormaznitsa.annotator.ui.api.EditorPanelContext;
import com.igormaznitsa.annotator.ui.api.ImageViewToggle;
import com.igormaznitsa.annotator.ui.editor.ImageCanvas;

public final class ClassNameToggle implements ImageViewToggle {

  @Override
  public String id() {
    return "class-names";
  }

  @Override
  public String tooltip() {
    return "Class names on/off";
  }

  @Override
  public String iconFileName() {
    return "label.png";
  }

  @Override
  public boolean isSelected(final EditorPanelContext context) {
    return context.isClassNamesVisible();
  }

  @Override
  public void setSelected(final EditorPanelContext context, final boolean selected) {
    if (context instanceof ImageCanvas canvas) {
      canvas.setClassNamesVisible(selected);
    }
  }
}
