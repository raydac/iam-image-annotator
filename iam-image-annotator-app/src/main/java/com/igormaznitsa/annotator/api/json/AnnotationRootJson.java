package com.igormaznitsa.annotator.api.json;

import java.util.List;

/**
 * Root object stored in the iANN PNG chunk and exported JSON files.
 */
final class AnnotationRootJson {

  List<AnnotationEntryJson> annotations;

  AnnotationRootJson() {
  }

  AnnotationRootJson(final List<AnnotationEntryJson> annotations) {
    this.annotations = annotations;
  }
}
