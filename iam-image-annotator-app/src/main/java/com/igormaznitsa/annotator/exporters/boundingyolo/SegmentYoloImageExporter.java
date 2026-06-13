package com.igormaznitsa.annotator.exporters.boundingyolo;

import com.igormaznitsa.annotator.api.model.AnnotationCoords;
import com.igormaznitsa.annotator.api.model.AnnotationDocument;
import com.igormaznitsa.annotator.api.model.AnnotationEntry;
import com.igormaznitsa.annotator.api.model.NormPoint;
import com.igormaznitsa.annotator.api.service.EditorSession;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class SegmentYoloImageExporter extends AbstractYoloDatasetExporter {

  private static final String TITLE = "YOLO segmentation dataset";
  private static final int MIN_SEGMENT_POINTS = 3;

  public SegmentYoloImageExporter(final int firstClassId) {
    this(firstClassId, ignored -> true);
  }

  public SegmentYoloImageExporter(
      final int firstClassId,
      final Predicate<Map<String, Integer>> classConfirmation) {
    this(firstClassId, classConfirmation, ignored -> Optional.empty());
  }

  public SegmentYoloImageExporter(
      final int firstClassId,
      final Predicate<Map<String, Integer>> classConfirmation,
      final Function<Path, Optional<EditorSession>> openSessionResolver) {
    super(TITLE, firstClassId, classConfirmation, openSessionResolver);
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
    final List<NormPoint> points = this.segmentPoints(entry.coords());
    if (points.size() < MIN_SEGMENT_POINTS) {
      return Optional.empty();
    }
    return this.pointBounds(points)
        .map(bounds -> new YoloRawLabel(
            this.normalizedClassName(entry),
            classId -> this.formatSegmentLine(classId, points),
            bounds));
  }

  private List<NormPoint> segmentPoints(final AnnotationCoords coords) {
    if (!coords.corners().isEmpty()) {
      return coords.corners();
    }
    if (!coords.points().isEmpty()) {
      return coords.points();
    }
    if (coords.x() == null || coords.y() == null || coords.width() == null
        || coords.height() == null || coords.width() <= 0.0d || coords.height() <= 0.0d) {
      return List.of();
    }
    return List.of(
        NormPoint.of(coords.x(), coords.y()),
        NormPoint.of(coords.x() + coords.width(), coords.y()),
        NormPoint.of(coords.x() + coords.width(), coords.y() + coords.height()),
        NormPoint.of(coords.x(), coords.y() + coords.height()));
  }

  private String formatSegmentLine(final int classId, final List<NormPoint> points) {
    return Stream.concat(
            Stream.of(Integer.toString(classId)),
            points.stream().flatMap(point -> Stream.of(
                this.formatCoordinate(point.x()),
                this.formatCoordinate(point.y()))))
        .collect(Collectors.joining(" "));
  }

  private String formatCoordinate(final double value) {
    return String.format(Locale.US, "%.6f", Math.clamp(value, 0.0d, 1.0d));
  }
}
