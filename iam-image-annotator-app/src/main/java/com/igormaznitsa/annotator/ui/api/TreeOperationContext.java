package com.igormaznitsa.annotator.ui.api;

import com.igormaznitsa.annotator.ui.editor.EditorWorkspace;

import java.awt.Component;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public interface TreeOperationContext {

  boolean hasOpenFolder();

  List<Path> selectedPaths();

  void refreshTree();

  EditorWorkspace editorWorkspace();

  void closeEditorTabs(Path path);

  void showError(String message);

  void showInfo(String message);

  boolean confirm(String title, String message);

  default Component dialogParent() {
    return null;
  }

  void runWithProgress(String title, Runnable task, Consumer<Exception> onError);
}
