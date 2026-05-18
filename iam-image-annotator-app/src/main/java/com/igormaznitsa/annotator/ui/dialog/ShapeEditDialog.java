package com.igormaznitsa.annotator.ui.dialog;

import com.igormaznitsa.annotator.api.model.AnnotationEntry;
import com.igormaznitsa.annotator.api.model.ClassNames;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.Locale;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

public final class ShapeEditDialog {

  private ShapeEditDialog() {
  }

  public static Optional<Result> show(final Component parent, final AnnotationEntry entry) {
    final JTextField idField = new JTextField(entry.id(), 24);
    final Color[] fillColor = {entry.fillColor()};
    final JPanel swatch = new JPanel();
    swatch.setPreferredSize(new Dimension(48, 24));
    swatch.setBackground(fillColor[0]);
    swatch.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
    final JLabel fillLabel = new JLabel(toHex(fillColor[0]));
    final JButton chooseFill = new JButton("Choose…");
    chooseFill.addActionListener(event -> {
      final Color chosen = JColorChooser.showDialog(parent, "Fill color", fillColor[0]);
      if (chosen != null) {
        fillColor[0] = chosen;
        swatch.setBackground(fillColor[0]);
        fillLabel.setText(toHex(fillColor[0]));
      }
    });
    final JPanel fillRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    fillRow.add(new JLabel("Fill:"));
    fillRow.add(swatch);
    fillRow.add(fillLabel);
    fillRow.add(chooseFill);

    final JPanel panel = new JPanel(new BorderLayout(8, 8));
    panel.add(new JLabel("Label (unique per shape):"), BorderLayout.NORTH);
    panel.add(idField, BorderLayout.CENTER);
    panel.add(fillRow, BorderLayout.SOUTH);
    panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

    final int choice = JOptionPane.showConfirmDialog(
        parent,
        panel,
        "Edit shape",
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.PLAIN_MESSAGE);
    if (choice != JOptionPane.OK_OPTION) {
      return Optional.empty();
    }
    try {
      final String id = ClassNames.normalize(idField.getText().trim());
      final String fillHex = ClassNames.normalizeColor(toHex(fillColor[0]));
      return Optional.of(new Result(id, fillHex));
    } catch (final IllegalArgumentException exception) {
      JOptionPane.showMessageDialog(parent, exception.getMessage(), "Invalid input",
          JOptionPane.ERROR_MESSAGE);
      return show(parent, entry);
    }
  }

  private static String toHex(final Color color) {
    return String.format(Locale.ROOT, "#%06X", color.getRGB() & 0xFFFFFF);
  }

  public record Result(String id, String fillColorHex) {
  }
}
