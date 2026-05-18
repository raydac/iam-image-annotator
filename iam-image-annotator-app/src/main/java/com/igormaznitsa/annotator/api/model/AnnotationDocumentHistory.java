package com.igormaznitsa.annotator.api.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

public final class AnnotationDocumentHistory {

  private static final int MAX_DEPTH = 100;

  private final Deque<List<AnnotationEntry>> undoStack = new ArrayDeque<>();
  private final Deque<List<AnnotationEntry>> redoStack = new ArrayDeque<>();

  public void clear() {
    this.undoStack.clear();
    this.redoStack.clear();
  }

  public void recordCheckpoint(final List<AnnotationEntry> currentEntries) {
    this.undoStack.push(this.copyEntries(currentEntries));
    this.redoStack.clear();
    while (this.undoStack.size() > MAX_DEPTH) {
      this.undoStack.removeLast();
    }
  }

  public Optional<List<AnnotationEntry>> undo(final List<AnnotationEntry> currentEntries) {
    if (this.undoStack.isEmpty()) {
      return Optional.empty();
    }
    this.redoStack.push(this.copyEntries(currentEntries));
    return Optional.of(this.copyEntries(this.undoStack.pop()));
  }

  public Optional<List<AnnotationEntry>> redo(final List<AnnotationEntry> currentEntries) {
    if (this.redoStack.isEmpty()) {
      return Optional.empty();
    }
    this.undoStack.push(this.copyEntries(currentEntries));
    return Optional.of(this.copyEntries(this.redoStack.pop()));
  }

  public boolean canUndo() {
    return !this.undoStack.isEmpty();
  }

  public boolean canRedo() {
    return !this.redoStack.isEmpty();
  }

  private List<AnnotationEntry> copyEntries(final List<AnnotationEntry> source) {
    return List.copyOf(new ArrayList<>(source));
  }
}
