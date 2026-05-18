package com.igormaznitsa.annotator.ui.operations;

import com.igormaznitsa.annotator.api.service.AllowedImageFiles;
import com.igormaznitsa.annotator.ui.api.TreeOperationContext;
import com.igormaznitsa.annotator.ui.api.TreeOperationIcon;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.io.FileUtils;

public final class DeleteTreeOperation implements TreeOperationIcon {

  @Override
  public String id() {
    return "delete";
  }

  @Override
  public String tooltip() {
    return "Delete selected folder or image";
  }

  @Override
  public String iconFileName() {
    return "delete.png";
  }

  @Override
  public boolean isEnabled(final TreeOperationContext context) {
    return context.hasOpenFolder() && !context.selectedPaths().isEmpty();
  }

  @Override
  public void execute(final TreeOperationContext context) {
    final List<Path> selected = context.selectedPaths();
    if (selected.isEmpty()) {
      return;
    }
    if (!context.confirm("Delete", "Delete " + selected.size() + " selected item(s)?")) {
      return;
    }
    for (final Path path : selected) {
      try {
        if (Files.isDirectory(path)) {
          context.editorWorkspace().closeAllUnder(path);
          FileUtils.deleteDirectory(path.toFile());
        } else if (Files.isRegularFile(path)) {
          if (AllowedImageFiles.isAllowed(path)) {
            context.closeEditorTabs(path);
            context.editorWorkspace().close(path);
          }
          Files.deleteIfExists(path);
        }
      } catch (final IOException exception) {
        context.showError("Delete failed: " + path + " — " + exception.getMessage());
      }
    }
    context.refreshTree();
  }
}
