package com.igormaznitsa.annotator.api.model;

import static java.util.UUID.randomUUID;

import java.awt.Color;
import java.util.Objects;

public final class AnnotationEntry {

  private final String key;
  private final String id;
  private final AnnotationType type;
  private final String fillColorHex;
  private final AnnotationCoords coords;
  private final boolean locked;
  private final boolean visible;

  public AnnotationEntry(
      final String id,
      final AnnotationType type,
      final String fillColorHex,
      final AnnotationCoords coords) {
    this(id, type, fillColorHex, coords, false);
  }

  public AnnotationEntry(
      final String id,
      final AnnotationType type,
      final String fillColorHex,
      final AnnotationCoords coords,
      final boolean locked) {
    this(id, type, fillColorHex, coords, locked, true);
  }

  public AnnotationEntry(
      final String id,
      final AnnotationType type,
      final String fillColorHex,
      final AnnotationCoords coords,
      final boolean locked,
      final boolean visible) {
    this(randomUUID().toString(), id, type, fillColorHex, coords, locked, visible);
  }

  private AnnotationEntry(
      final String key,
      final String id,
      final AnnotationType type,
      final String fillColorHex,
      final AnnotationCoords coords,
      final boolean locked,
      final boolean visible) {
    this.key = Objects.requireNonNull(key, "key");
    this.id = ClassNames.normalize(id);
    this.type = Objects.requireNonNull(type, "type");
    this.fillColorHex = ClassNames.normalizeColor(fillColorHex);
    this.coords = Objects.requireNonNull(coords, "coords");
    this.locked = locked;
    this.visible = visible;
  }

  public String key() {
    return this.key;
  }

  public String id() {
    return this.id;
  }

  public AnnotationType type() {
    return this.type;
  }

  public String fillColorHex() {
    return this.fillColorHex;
  }

  public AnnotationCoords coords() {
    return this.coords;
  }

  public boolean locked() {
    return this.locked;
  }

  public boolean visible() {
    return this.visible;
  }

  public Color fillColor() {
    return Color.decode(this.fillColorHex);
  }

  /**
   * Border color derived from {@link #fillColor()} for contrast.
   */
  public Color strokeColor() {
    return AnnotationColors.contrastStrokeColor(this.fillColorHex);
  }

  public AnnotationEntry withId(final String newId) {
    return new AnnotationEntry(
        this.key, newId, this.type, this.fillColorHex, this.coords, this.locked, this.visible);
  }

  public AnnotationEntry withCoords(final AnnotationCoords newCoords) {
    return new AnnotationEntry(
        this.key, this.id, this.type, this.fillColorHex, newCoords, this.locked, this.visible);
  }

  public AnnotationEntry withFillColor(final String newFillColorHex) {
    return new AnnotationEntry(
        this.key, this.id, this.type, newFillColorHex, this.coords, this.locked, this.visible);
  }

  public AnnotationEntry withType(final AnnotationType newType) {
    return new AnnotationEntry(
        this.key, this.id, newType, this.fillColorHex, this.coords, this.locked, this.visible);
  }

  public AnnotationEntry withLocked(final boolean newLocked) {
    return new AnnotationEntry(
        this.key, this.id, this.type, this.fillColorHex, this.coords, newLocked, this.visible);
  }

  public AnnotationEntry withVisible(final boolean newVisible) {
    return new AnnotationEntry(
        this.key, this.id, this.type, this.fillColorHex, this.coords, this.locked, newVisible);
  }
}
