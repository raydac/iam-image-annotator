package com.igormaznitsa.annotator.ui.editor;

/**
 * Interactive handle on a selected annotation.
 */
public sealed interface AnnotationHandle {

  String annotationId();

  /**
   * Drag the whole annotation.
   */
  record Body(String annotationId) implements AnnotationHandle {
  }

  /**
   * Drag a rectangle or OBB corner, polygon vertex, or pose keypoint.
   */
  record Vertex(String annotationId, int index) implements AnnotationHandle {
  }

  /**
   * Rotate the shape around its centroid.
   */
  record Rotate(String annotationId) implements AnnotationHandle {
  }
}
