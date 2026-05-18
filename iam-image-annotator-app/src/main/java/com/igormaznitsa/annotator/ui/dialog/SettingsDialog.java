package com.igormaznitsa.annotator.ui.dialog;

import java.awt.Component;
import javax.swing.JOptionPane;

public final class SettingsDialog {

  private SettingsDialog() {
  }

  public static void show(final Component parent) {
    JOptionPane.showMessageDialog(
        parent,
        "Application settings will be available in a future release.",
        "Settings",
        JOptionPane.INFORMATION_MESSAGE);
  }
}
