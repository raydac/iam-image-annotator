package com.igormaznitsa.annotator.ui.dialog;

import java.awt.Component;
import java.awt.Frame;
import java.awt.Window;
import javax.swing.SwingUtilities;

public final class DialogParents {

  private DialogParents() {
  }

  public static Component frameOrSelf(final Component component) {
    final Frame frame = frameOf(component);
    return frame == null ? component : frame;
  }

  public static Frame frameOf(final Component component) {
    Window window = component instanceof Window candidate
        ? candidate
        : component == null ? null : SwingUtilities.getWindowAncestor(component);
    while (window != null) {
      if (window instanceof Frame frame) {
        return frame;
      }
      window = window.getOwner();
    }
    return null;
  }
}
