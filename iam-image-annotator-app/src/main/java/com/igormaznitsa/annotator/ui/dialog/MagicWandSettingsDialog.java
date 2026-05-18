package com.igormaznitsa.annotator.ui.dialog;

import com.igormaznitsa.annotator.ui.selection.MagicWandMode;
import com.igormaznitsa.annotator.ui.selection.MagicWandSettings;
import java.awt.Component;
import java.util.Locale;
import java.util.Optional;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;

public final class MagicWandSettingsDialog {

  private MagicWandSettingsDialog() {
  }

  public static Optional<MagicWandSettings> show(final Component parent,
                                                 final MagicWandSettings initial) {
    final MagicWandSettings defaults = initial != null ? initial : MagicWandSettings.defaults();
    final JLabel toleranceLabel = new JLabel(formatTolerance(defaults.tolerance()));
    final JSlider toleranceSlider =
        new JSlider(0, 100, (int) Math.round(defaults.tolerance() * 100));
    toleranceSlider.setMajorTickSpacing(25);
    toleranceSlider.setMinorTickSpacing(5);
    toleranceSlider.setPaintTicks(true);
    toleranceSlider.addChangeListener((final ChangeEvent event) ->
        toleranceLabel.setText(formatTolerance(toleranceSlider.getValue() / 100.0)));

    final JLabel smoothnessLabel = new JLabel(formatTolerance(defaults.smoothness()));
    final JSlider smoothnessSlider =
        new JSlider(0, 100, (int) Math.round(defaults.smoothness() * 100));
    smoothnessSlider.setMajorTickSpacing(25);
    smoothnessSlider.setMinorTickSpacing(5);
    smoothnessSlider.setPaintTicks(true);
    smoothnessSlider.addChangeListener((final ChangeEvent event) ->
        smoothnessLabel.setText(formatTolerance(smoothnessSlider.getValue() / 100.0)));

    final JRadioButton colorMode =
        new JRadioButton("Color (RGB distance)", defaults.mode() == MagicWandMode.COLOR);
    final JRadioButton luminanceMode =
        new JRadioButton("Luminance only", defaults.mode() == MagicWandMode.LUMINANCE);
    final ButtonGroup modeGroup = new ButtonGroup();
    modeGroup.add(colorMode);
    modeGroup.add(luminanceMode);
    final JCheckBox livePreview = new JCheckBox(
        "Live preview on hover (uses more CPU on large images)",
        defaults.livePreview());

    final JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.add(new JLabel("Tolerance (higher = larger region):"));
    panel.add(toleranceSlider);
    panel.add(toleranceLabel);
    panel.add(new JLabel(" "));
    panel.add(new JLabel("Contour smoothness (higher = fewer vertices):"));
    panel.add(smoothnessSlider);
    panel.add(smoothnessLabel);
    panel.add(new JLabel(" "));
    panel.add(new JLabel("Match mode:"));
    panel.add(colorMode);
    panel.add(luminanceMode);
    panel.add(new JLabel(" "));
    panel.add(livePreview);

    final int choice = JOptionPane.showConfirmDialog(
        parent,
        panel,
        "Magic wand",
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.PLAIN_MESSAGE);
    if (choice != JOptionPane.OK_OPTION) {
      return Optional.empty();
    }
    final MagicWandMode mode =
        colorMode.isSelected() ? MagicWandMode.COLOR : MagicWandMode.LUMINANCE;
    return Optional.of(new MagicWandSettings(
        toleranceSlider.getValue() / 100.0,
        mode,
        smoothnessSlider.getValue() / 100.0,
        livePreview.isSelected()));
  }

  private static String formatTolerance(final double tolerance) {
    return String.format(Locale.US, "%.2f", tolerance);
  }
}
