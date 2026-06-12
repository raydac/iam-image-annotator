package com.igormaznitsa.annotator.api.service;

import com.igormaznitsa.annotator.api.model.AnnotationDocument;
import com.igormaznitsa.annotator.api.model.AnnotationDocumentHistory;
import com.igormaznitsa.annotator.api.model.AnnotationEntry;
import com.igormaznitsa.annotator.api.model.ClassNames;
import com.igormaznitsa.annotator.api.png.AnnotatedPng;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

public final class EditorSession {

  private final BufferedImage baseImage;
  private final AnnotationDocument document;
  private final AnnotationDocumentHistory history = new AnnotationDocumentHistory();
  private Path filePath;
  private boolean dirty;
  private String selectedAnnotationKey;
  private int selectedVertexIndex = -1;
  private String lastClassId;

  private EditorSession(
      final Path filePath,
      final BufferedImage baseImage,
      final AnnotationDocument document) {
    this.filePath = Objects.requireNonNull(filePath, "filePath");
    this.baseImage = Objects.requireNonNull(baseImage, "baseImage");
    this.document = Objects.requireNonNull(document, "document");
  }

  public static EditorSession open(final Path filePath) throws IOException {
    try (InputStream input = Files.newInputStream(filePath)) {
      final AnnotatedPng annotated = AnnotatedPng.read(input);
      return new EditorSession(filePath, annotated.baseImage(), annotated.document());
    }
  }

  public static EditorSession fromImage(final Path filePath, final BufferedImage image) {
    return new EditorSession(filePath, image, new AnnotationDocument());
  }

  public Path filePath() {
    return this.filePath;
  }

  public BufferedImage baseImage() {
    return this.baseImage;
  }

  public AnnotationDocument document() {
    return this.document;
  }

  public void recordUndoCheckpoint() {
    this.history.recordCheckpoint(this.document.snapshotEntries());
  }

  public boolean canUndo() {
    return this.history.canUndo();
  }

  public boolean canRedo() {
    return this.history.canRedo();
  }

  public boolean undo() {
    final List<AnnotationEntry> restored =
        this.history.undo(this.document.snapshotEntries()).orElse(null);
    if (restored == null) {
      return false;
    }
    this.document.restoreEntries(restored);
    this.clearSelection();
    this.markDirty();
    return true;
  }

  public boolean redo() {
    final List<AnnotationEntry> restored =
        this.history.redo(this.document.snapshotEntries()).orElse(null);
    if (restored == null) {
      return false;
    }
    this.document.restoreEntries(restored);
    this.clearSelection();
    this.markDirty();
    return true;
  }

  public void clearHistory() {
    this.history.clear();
  }

  public boolean isDirty() {
    return this.dirty;
  }

  public void markDirty() {
    this.dirty = true;
  }

  public void markClean() {
    this.dirty = false;
  }

  public Optional<String> selectedAnnotation() {
    return Optional.ofNullable(this.selectedAnnotationKey);
  }

  public void selectAnnotation(final String key) {
    this.selectedAnnotationKey = key;
    this.selectedVertexIndex = -1;
  }

  public void clearSelection() {
    this.selectedAnnotationKey = null;
    this.selectedVertexIndex = -1;
  }

  public OptionalInt selectedVertexIndex() {
    return this.selectedVertexIndex < 0
        ? OptionalInt.empty()
        : OptionalInt.of(this.selectedVertexIndex);
  }

  public void selectVertex(final int index) {
    this.selectedVertexIndex = index;
  }

  public void clearVertexSelection() {
    this.selectedVertexIndex = -1;
  }

  public Optional<String> lastClassId() {
    return Optional.ofNullable(this.lastClassId);
  }

  public void rememberClassId(final String classId) {
    this.lastClassId = ClassNames.normalize(classId);
  }

  public void save(final Path target) throws IOException {
    AllowedImageFiles.ensureParentExists(target);
    final AnnotatedPng annotated = new AnnotatedPng(this.baseImage, this.document);
    try (var output = Files.newOutputStream(target)) {
      annotated.write(output);
    }
    this.filePath = target;
    this.markClean();
    this.clearHistory();
  }

  public void rekey(final Path newPath) {
    this.filePath = Objects.requireNonNull(newPath, "newPath");
  }
}
