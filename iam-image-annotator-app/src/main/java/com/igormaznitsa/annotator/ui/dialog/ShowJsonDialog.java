package com.igormaznitsa.annotator.ui.dialog;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public final class ShowJsonDialog {

  private ShowJsonDialog() {
  }

  public static void show(final Component parent, final String title, final String json) {
    final Component owner = DialogParents.frameOrSelf(parent);
    final JTextArea text = new JTextArea(json);
    text.setEditable(false);
    text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    text.setLineWrap(false);
    text.setTabSize(2);
    text.setCaretPosition(0);
    final JScrollPane scroll = new JScrollPane(text);
    scroll.setPreferredSize(new Dimension(720, 520));
    JOptionPane.showMessageDialog(owner, scroll, title, JOptionPane.PLAIN_MESSAGE);
  }
}
