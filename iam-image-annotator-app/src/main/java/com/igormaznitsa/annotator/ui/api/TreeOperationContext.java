package com.igormaznitsa.annotator.ui.api;

import com.igormaznitsa.annotator.ui.editor.EditorWorkspace;

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

  void runWithProgress(String title, Runnable task, Consumer<Exception> onError);
}
