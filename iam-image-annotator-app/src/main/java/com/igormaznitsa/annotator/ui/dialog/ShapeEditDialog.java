package com.igormaznitsa.annotator.ui.dialog;

import com.igormaznitsa.annotator.api.model.AnnotationEntry;
import com.igormaznitsa.annotator.api.model.ClassNames;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

public final class ShapeEditDialog {

  private ShapeEditDialog() {
  }

  public static Optional<Result> show(final Component parent, final AnnotationEntry entry) {
    final Component owner = DialogParents.frameOrSelf(parent);
    final JTextField idField = new JTextField(entry.id(), 24);
    final Color[] fillColor = {entry.fillColor()};
    final JPanel swatch = new JPanel();
    swatch.setPreferredSize(new Dimension(48, 24));
    swatch.setBackground(fillColor[0]);
    swatch.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
    final JLabel fillLabel = new JLabel(ColorChooserDialog.toHex(fillColor[0]));
    final JButton chooseFill = new JButton("Choose…");
    chooseFill.addActionListener(event -> {
      final Optional<Color> chosen = ColorChooserDialog.show(owner, "Fill color", fillColor[0]);
      if (chosen.isPresent()) {
        fillColor[0] = chosen.get();
        swatch.setBackground(fillColor[0]);
        fillLabel.setText(ColorChooserDialog.toHex(fillColor[0]));
      }
    });
    final JPanel fillRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    fillRow.add(new JLabel("Fill:"));
    fillRow.add(swatch);
    fillRow.add(fillLabel);
    fillRow.add(chooseFill);

    final JPanel panel = new JPanel(new BorderLayout(8, 8));
    panel.add(new JLabel("Class label:"), BorderLayout.NORTH);
    panel.add(idField, BorderLayout.CENTER);
    panel.add(fillRow, BorderLayout.SOUTH);
    panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

    final int choice = JOptionPane.showConfirmDialog(
        owner,
        panel,
        "Edit shape",
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.PLAIN_MESSAGE);
    if (choice != JOptionPane.OK_OPTION) {
      return Optional.empty();
    }
    try {
      final String id = ClassNames.normalize(idField.getText().trim());
      final String fillHex = ClassNames.normalizeColor(ColorChooserDialog.toHex(fillColor[0]));
      return Optional.of(new Result(id, fillHex));
    } catch (final IllegalArgumentException exception) {
      JOptionPane.showMessageDialog(owner, exception.getMessage(), "Invalid input",
          JOptionPane.ERROR_MESSAGE);
      return show(owner, entry);
    }
  }

  public record Result(String id, String fillColorHex) {
  }
}
