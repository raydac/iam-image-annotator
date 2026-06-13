package com.igormaznitsa.annotator.exporters.boundingyolo;

import com.igormaznitsa.annotator.api.model.AnnotationCoords;
import com.igormaznitsa.annotator.api.model.AnnotationDocument;
import com.igormaznitsa.annotator.api.model.AnnotationEntry;
import com.igormaznitsa.annotator.api.service.EditorSession;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public final class BoundingYoloImageExporter extends AbstractYoloDatasetExporter {

  private static final String TITLE = "YOLO bounding boxes dataset";

  public BoundingYoloImageExporter(final int firstClassId) {
    this(firstClassId, ignored -> true);
  }

  public BoundingYoloImageExporter(
      final int firstClassId,
      final Predicate<Map<String, Integer>> classConfirmation) {
    this(firstClassId, classConfirmation, ignored -> Optional.empty());
  }

  public BoundingYoloImageExporter(
      final int firstClassId,
      final Predicate<Map<String, Integer>> classConfirmation,
      final Function<Path, Optional<EditorSession>> openSessionResolver) {
    super(TITLE, firstClassId, classConfirmation, openSessionResolver);
  }

  static String toYoloClassName(final String className) {
    return AbstractYoloDatasetExporter.toYoloClassName(className);
  }

  @Override
  protected List<YoloRawLabel> rawLabelsOf(final AnnotationDocument document) {
    return document.entries().stream()
        .filter(AnnotationEntry::visible)
        .map(this::toRawLabel)
        .flatMap(Optional::stream)
        .toList();
  }

  private Optional<YoloRawLabel> toRawLabel(final AnnotationEntry entry) {
    final AnnotationCoords coords = entry.coords();
    final Optional<YoloBoundingBox.Bounds> bounds = switch (entry.type()) {
      case RECTANGLE, POSE2D -> this.rectangleBounds(coords);
      case POLYGON -> this.pointBounds(coords.points());
      case OBB -> this.pointBounds(coords.corners());
    };
    return bounds.map(value -> this.toRawLabel(entry, value));
  }

  private YoloRawLabel toRawLabel(
      final AnnotationEntry entry,
      final YoloBoundingBox.Bounds bounds) {
    final String className = this.normalizedClassName(entry);
    return new YoloRawLabel(
        className,
        classId -> YoloBoundingBox.of(className, classId, bounds).toLabelLine(),
        bounds);
  }

  private Optional<YoloBoundingBox.Bounds> rectangleBounds(final AnnotationCoords coords) {
    if (coords.x() == null || coords.y() == null || coords.width() == null
        || coords.height() == null || coords.width() <= 0.0d || coords.height() <= 0.0d) {
      return Optional.empty();
    }
    return this.createBounds(
        coords.x(),
        coords.y(),
        coords.x() + coords.width(),
        coords.y() + coords.height());
  }

  private Optional<YoloBoundingBox.Bounds> createBounds(
      final double minX,
      final double minY,
      final double maxX,
      final double maxY) {
    try {
      return Optional.of(new YoloBoundingBox.Bounds(minX, minY, maxX, maxY));
    } catch (final IllegalArgumentException ignored) {
      return Optional.empty();
    }
  }
}
