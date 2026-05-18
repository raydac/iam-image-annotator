package com.igormaznitsa.annotator.ui.operations;

import com.igormaznitsa.annotator.api.service.AllowedImageFiles;
import com.igormaznitsa.annotator.ui.api.TreeOperationContext;
import com.igormaznitsa.annotator.ui.api.TreeOperationIcon;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EditTreeOperation implements TreeOperationIcon {

  @Override
  public String id() {
    return "edit";
  }

  @Override
  public String tooltip() {
    return "Focus selected image in editor";
  }

  @Override
  public String iconFileName() {
    return "folder_edit.png";
  }

  @Override
  public boolean isEnabled(final TreeOperationContext context) {
    return context.hasOpenFolder()
        && context.selectedPaths().stream().anyMatch(this::isOpenImage);
  }

  @Override
  public void execute(final TreeOperationContext context) {
    context.selectedPaths().stream()
        .filter(this::isOpenImage)
        .findFirst()
        .ifPresent(path -> context.editorWorkspace().setActive(path));
  }

  private boolean isOpenImage(final Path path) {
    return Files.isRegularFile(path)
        && AllowedImageFiles.isAllowed(path)
        && Files.exists(path);
  }
}
