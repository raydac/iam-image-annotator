package com.igormaznitsa.annotator.exporters.boundingyolo;

import com.igormaznitsa.annotator.api.model.AnnotationDocument;
import com.igormaznitsa.annotator.api.model.AnnotationEntry;
import com.igormaznitsa.annotator.api.model.AnnotationType;
import com.igormaznitsa.annotator.api.model.ObbCorners;
import com.igormaznitsa.annotator.api.service.EditorSession;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public final class ObbYoloImageExporter extends AbstractYoloDatasetExporter {

  private static final String TITLE = "YOLO OBB dataset";

  public ObbYoloImageExporter(final int firstClassId) {
    this(firstClassId, ignored -> true);
  }

  public ObbYoloImageExporter(
      final int firstClassId,
      final Predicate<Map<String, Integer>> classConfirmation) {
    this(firstClassId, classConfirmation, ignored -> Optional.empty());
  }

  public ObbYoloImageExporter(
      final int firstClassId,
      final Predicate<Map<String, Integer>> classConfirmation,
      final Function<Path, Optional<EditorSession>> openSessionResolver) {
    super(TITLE, firstClassId, classConfirmation, openSessionResolver);
  }

  @Override
  protected List<YoloRawLabel> rawLabelsOf(final AnnotationDocument document) {
    return document.entries().stream()
        .filter(AnnotationEntry::visible)
        .filter(entry -> entry.type() == AnnotationType.OBB)
        .map(this::toRawLabel)
        .flatMap(Optional::stream)
        .toList();
  }

  private Optional<YoloRawLabel> toRawLabel(final AnnotationEntry entry) {
    return this.pointBounds(entry.coords().corners())
        .map(bounds -> new YoloRawLabel(
            this.normalizedClassName(entry),
            classId -> ObbCorners.toYoloLine(classId, entry.coords().corners()),
            bounds));
  }
}
