package com.igormaznitsa.annotator.ui.operations;

import com.igormaznitsa.annotator.api.export.AnnotationExporter;
import com.igormaznitsa.annotator.api.export.YoloObbExporter;
import com.igormaznitsa.annotator.api.service.AllowedImageFiles;
import com.igormaznitsa.annotator.api.service.EditorSession;
import com.igormaznitsa.annotator.ui.api.TreeOperationContext;
import com.igormaznitsa.annotator.ui.api.TreeOperationIcon;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

public final class ExportTreeOperation implements TreeOperationIcon {

  @Override
  public String id() {
    return "export";
  }

  @Override
  public String tooltip() {
    return "Export annotations (JSON or YOLO OBB .txt labels)";
  }

  @Override
  public String iconFileName() {
    return "layer_export.png";
  }

  @Override
  public boolean isEnabled(final TreeOperationContext context) {
    return context.hasOpenFolder()
        && context.selectedPaths().stream().anyMatch(path -> Files.isRegularFile(path)
        && AllowedImageFiles.isAllowed(path));
  }

  @Override
  public void execute(final TreeOperationContext context) {
    final int format = JOptionPane.showOptionDialog(
        null,
        "Choose export format",
        "Export annotations",
        JOptionPane.DEFAULT_OPTION,
        JOptionPane.QUESTION_MESSAGE,
        null,
        new String[] {"JSON", "YOLO OBB (.txt per image)"},
        "JSON");
    if (format == JOptionPane.CLOSED_OPTION) {
      return;
    }
    if (format == 0) {
      this.exportJson(context);
    } else {
      this.exportYoloObb(context);
    }
  }

  private void exportJson(final TreeOperationContext context) {
    final JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Export annotations JSON");
    chooser.setFileFilter(new FileNameExtensionFilter("JSON", "json"));
    if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    Path target = chooser.getSelectedFile().toPath();
    if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
      target = target.resolveSibling(target.getFileName() + ".json");
    }
    final AnnotationExporter exporter = new AnnotationExporter();
    try {
      for (final Path path : context.selectedPaths()) {
        if (!Files.isRegularFile(path) || !AllowedImageFiles.isAllowed(path)) {
          continue;
        }
        exporter.exportJson(this.loadSession(context, path), target);
      }
      context.showInfo("Exported JSON to " + target);
    } catch (final Exception exception) {
      context.showError("Export failed: " + exception.getMessage());
    }
  }

  private void exportYoloObb(final TreeOperationContext context) {
    final JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Export YOLO OBB label folder");
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    final Path folder = chooser.getSelectedFile().toPath();
    final YoloObbExporter exporter = new YoloObbExporter();
    int count = 0;
    try {
      for (final Path path : context.selectedPaths()) {
        if (!Files.isRegularFile(path) || !AllowedImageFiles.isAllowed(path)) {
          continue;
        }
        final Path labelFile = folder.resolve(YoloObbExporter.labelFileNameFor(path));
        exporter.exportLabelFile(this.loadSession(context, path), labelFile);
        count++;
      }
      context.showInfo("Exported YOLO OBB labels for " + count + " image(s) to " + folder);
    } catch (final Exception exception) {
      context.showError("YOLO OBB export failed: " + exception.getMessage());
    }
  }

  private EditorSession loadSession(final TreeOperationContext context, final Path path) {
    return context.editorWorkspace().allSessions().stream()
        .filter(open -> open.filePath().equals(path))
        .findFirst()
        .orElseGet(() -> {
          try {
            return EditorSession.open(path);
          } catch (final IOException exception) {
            throw new IllegalStateException(exception);
          }
        });
  }
}
