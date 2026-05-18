package com.igormaznitsa.annotator.ui.editor;

import com.igormaznitsa.annotator.api.model.AnnotationCoords;
import com.igormaznitsa.annotator.api.model.AnnotationType;

public record ShapeTransformResult(AnnotationCoords coords, AnnotationType type) {
}
