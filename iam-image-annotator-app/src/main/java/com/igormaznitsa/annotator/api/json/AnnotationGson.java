package com.igormaznitsa.annotator.api.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

final class AnnotationGson {

  private AnnotationGson() {
  }

  static Gson create() {
    return builder().create();
  }

  static Gson createPretty() {
    return builder().setPrettyPrinting().create();
  }

  private static GsonBuilder builder() {
    return new GsonBuilder().disableHtmlEscaping();
  }
}
