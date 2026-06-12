package com.igormaznitsa.annotator.api.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class AnnotationDocument {

  private final List<AnnotationEntry> entries = new ArrayList<>();
  private int nextFillColorIndex;

  public List<AnnotationEntry> entries() {
    return Collections.unmodifiableList(this.entries);
  }

  public void replaceAll(final List<AnnotationEntry> source) {
    this.entries.clear();
    this.entries.addAll(source);
    this.syncNextFillColorIndex();
  }

  public List<AnnotationEntry> snapshotEntries() {
    return List.copyOf(this.entries);
  }

  public void restoreEntries(final List<AnnotationEntry> source) {
    this.entries.clear();
    this.entries.addAll(source);
    this.syncNextFillColorIndex();
  }

  /**
   * Next fill color from the seven-color palette; advances the cycle.
   */
  public String nextFillColor() {
    final String color = ShapeFillPalette.colorAt(this.nextFillColorIndex);
    this.nextFillColorIndex = (this.nextFillColorIndex + 1) % ShapeFillPalette.size();
    return color;
  }

  public AnnotationEntry add(final AnnotationEntry entry) {
    Objects.requireNonNull(entry, "entry");
    this.entries.add(entry);
    return entry;
  }

  public AnnotationEntry create(
      final String id,
      final AnnotationType type,
      final String fillColorHex,
      final AnnotationCoords coords) {
    final AnnotationEntry entry = new AnnotationEntry(
        id,
        type,
        ClassNames.normalizeColor(fillColorHex),
        coords,
        false,
        true);
    return this.add(entry);
  }

  public void updateCoords(final String key, final AnnotationCoords coords) {
    final int index = this.requireIndex(key);
    this.requireUnlocked(this.entries.get(index));
    this.entries.set(index, this.entries.get(index).withCoords(coords));
  }

  public void updateAnnotation(final String key, final AnnotationType type,
                               final AnnotationCoords coords) {
    final int index = this.requireIndex(key);
    this.requireUnlocked(this.entries.get(index));
    final AnnotationEntry current = this.entries.get(index);
    this.entries.set(index, current.withType(type).withCoords(coords));
  }

  public void updateFillColor(final String key, final String fillColorHex) {
    final int index = this.requireIndex(key);
    this.requireUnlocked(this.entries.get(index));
    this.entries.set(index, this.entries.get(index)
        .withFillColor(ClassNames.normalizeColor(fillColorHex)));
  }

  public void setLocked(final String key, final boolean locked) {
    final int index = this.requireIndex(key);
    final AnnotationEntry current = this.entries.get(index);
    if (current.locked() == locked) {
      return;
    }
    this.entries.set(index, current.withLocked(locked));
  }

  public void setVisible(final String key, final boolean visible) {
    final int index = this.requireIndex(key);
    final AnnotationEntry current = this.entries.get(index);
    if (current.visible() == visible) {
      return;
    }
    this.entries.set(index, current.withVisible(visible));
  }

  public void remove(final String key) {
    this.entries.removeIf(entry -> entry.key().equals(key));
  }

  public Optional<AnnotationEntry> findByKey(final String key) {
    return this.entries.stream().filter(entry -> entry.key().equals(key)).findFirst();
  }

  public void moveUp(final String key) {
    final int index = this.indexOf(key);
    if (index > 0) {
      Collections.swap(this.entries, index, index - 1);
    }
  }

  public void moveDown(final String key) {
    final int index = this.indexOf(key);
    if (index >= 0 && index < this.entries.size() - 1) {
      Collections.swap(this.entries, index, index + 1);
    }
  }

  public void rename(final String key, final String newId) {
    final String normalized = ClassNames.normalize(newId);
    final int index = this.requireIndex(key);
    if (this.entries.get(index).id().equals(normalized)) {
      return;
    }
    this.requireUnlocked(this.entries.get(index));
    final AnnotationEntry current = this.entries.get(index);
    this.entries.set(index, current.withId(normalized));
  }

  private int indexOf(final String key) {
    for (int i = 0; i < this.entries.size(); i++) {
      if (this.entries.get(i).key().equals(key)) {
        return i;
      }
    }
    return -1;
  }

  private int requireIndex(final String key) {
    final int index = this.indexOf(key);
    if (index < 0) {
      throw new IllegalArgumentException("Unknown annotation: " + key);
    }
    return index;
  }

  private void requireUnlocked(final AnnotationEntry entry) {
    if (entry.locked()) {
      throw new IllegalStateException("Annotation is locked: " + entry.id());
    }
  }

  private void syncNextFillColorIndex() {
    this.nextFillColorIndex = this.entries.size() % ShapeFillPalette.size();
  }
}
