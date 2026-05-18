package com.igormaznitsa.annotator.api.json;

import com.igormaznitsa.annotator.api.model.AnnotationCoords;
import com.igormaznitsa.annotator.api.model.AnnotationDocument;
import com.igormaznitsa.annotator.api.model.AnnotationEntry;
import com.igormaznitsa.annotator.api.model.AnnotationType;
import com.igormaznitsa.annotator.api.model.NormPoint;
import com.igormaznitsa.annotator.api.model.ObbCorners;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class AnnotationJsonMapper {

  private AnnotationJsonMapper() {
  }

  static AnnotationRootJson toRoot(final AnnotationDocument document) {
    final List<AnnotationEntryJson> entries = new ArrayList<>();
    for (final AnnotationEntry entry : document.entries()) {
      entries.add(toEntryJson(entry));
    }
    return new AnnotationRootJson(entries);
  }

  static AnnotationDocument toDocument(final AnnotationRootJson root) {
    final AnnotationDocument document = new AnnotationDocument();
    if (root.annotations == null) {
      return document;
    }
    final List<AnnotationEntry> entries = new ArrayList<>();
    for (final AnnotationEntryJson item : root.annotations) {
      entries.add(toEntry(item));
    }
    document.replaceAll(entries);
    return document;
  }

  private static AnnotationEntryJson toEntryJson(final AnnotationEntry entry) {
    return new AnnotationEntryJson(
        entry.id(),
        entry.type().jsonName(),
        entry.fillColorHex(),
        entry.locked() ? Boolean.TRUE : null,
        toCoordsJson(entry.type(), entry.coords()));
  }

  private static AnnotationEntry toEntry(final AnnotationEntryJson json) {
    Objects.requireNonNull(json, "annotation item");
    final String id = Objects.toString(json.id, "").trim();
    if (id.isEmpty()) {
      throw new IllegalArgumentException("Annotation id is required");
    }
    final AnnotationType type = AnnotationType.fromJson(Objects.toString(json.type, "rectangle"));
    final String fillColor = Objects.toString(json.fillColor, "#808080");
    if (json.coords == null) {
      throw new IllegalArgumentException("coords must be an object");
    }
    return new AnnotationEntry(id, type, fillColor, toCoords(type, json.coords),
        parseLock(json.lock));
  }

  private static boolean parseLock(final Object lock) {
    if (lock instanceof Boolean value) {
      return value;
    }
    if (lock instanceof Number number) {
      return number.intValue() != 0;
    }
    return false;
  }

  private static AnnotationCoordsJson toCoordsJson(final AnnotationType type,
                                                   final AnnotationCoords coords) {
    return switch (type) {
      case RECTANGLE ->
          new AnnotationCoordsJson(coords.x(), coords.y(), coords.width(), coords.height(), null,
              null);
      case POLYGON ->
          new AnnotationCoordsJson(null, null, null, null, toPointJsonList(coords.points()), null);
      case POSE2D -> new AnnotationCoordsJson(
          coords.x(),
          coords.y(),
          coords.width(),
          coords.height(),
          toPointJsonList(coords.points()),
          null);
      case OBB ->
          new AnnotationCoordsJson(null, null, null, null, null, toPointJsonList(coords.corners()));
    };
  }

  private static AnnotationCoords toCoords(final AnnotationType type,
                                           final AnnotationCoordsJson json) {
    return switch (type) {
      case RECTANGLE -> AnnotationCoords.rectangle(
          requireDouble(json.x, "x"),
          requireDouble(json.y, "y"),
          requireDouble(json.width, "width"),
          requireDouble(json.height, "height"));
      case POLYGON -> AnnotationCoords.polygon(toPoints(json.points));
      case POSE2D -> AnnotationCoords.pose(
          requireDouble(json.x, "x"),
          requireDouble(json.y, "y"),
          requireDouble(json.width, "width"),
          requireDouble(json.height, "height"),
          toPoints(json.points));
      case OBB -> AnnotationCoords.obb(ObbCorners.normalize(toPoints(json.corners)));
    };
  }

  private static double requireDouble(final Double value, final String field) {
    if (value == null) {
      throw new IllegalArgumentException("coords." + field + " is required");
    }
    return value;
  }

  private static List<NormPointJson> toPointJsonList(final List<NormPoint> points) {
    final List<NormPointJson> jsonPoints = new ArrayList<>(points.size());
    for (final NormPoint point : points) {
      jsonPoints.add(toPointJson(point));
    }
    return jsonPoints;
  }

  private static NormPointJson toPointJson(final NormPoint point) {
    final Integer visibility = point.visibility() == 2 ? null : point.visibility();
    return new NormPointJson(point.x(), point.y(), visibility);
  }

  private static List<NormPoint> toPoints(final List<NormPointJson> jsonPoints) {
    if (jsonPoints == null || jsonPoints.isEmpty()) {
      return List.of();
    }
    final List<NormPoint> points = new ArrayList<>(jsonPoints.size());
    for (final NormPointJson jsonPoint : jsonPoints) {
      if (jsonPoint == null) {
        continue;
      }
      final int visibility = jsonPoint.v == null ? 2 : jsonPoint.v;
      points.add(new NormPoint(jsonPoint.x, jsonPoint.y, visibility));
    }
    return List.copyOf(points);
  }
}
