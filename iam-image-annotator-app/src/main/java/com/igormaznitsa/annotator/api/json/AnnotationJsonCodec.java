package com.igormaznitsa.annotator.api.json;

import com.google.gson.Gson;
import com.igormaznitsa.annotator.api.model.AnnotationDocument;

import java.nio.charset.StandardCharsets;

public final class AnnotationJsonCodec {

  private static final Gson GSON = AnnotationGson.create();
  private static final Gson PRETTY_GSON = AnnotationGson.createPretty();

  public byte[] encode(final AnnotationDocument document) {
    return this.encodeUtf8(document).getBytes(StandardCharsets.UTF_8);
  }

  public String encodePretty(final AnnotationDocument document) {
    return PRETTY_GSON.toJson(AnnotationJsonMapper.toRoot(document));
  }

  private String encodeUtf8(final AnnotationDocument document) {
    return GSON.toJson(AnnotationJsonMapper.toRoot(document));
  }

  public AnnotationDocument decode(final byte[] utf8Json) {
    final String json = new String(utf8Json, StandardCharsets.UTF_8);
    final AnnotationRootJson root = GSON.fromJson(json, AnnotationRootJson.class);
    if (root == null) {
      throw new IllegalArgumentException("Root JSON must be an object");
    }
    return AnnotationJsonMapper.toDocument(root);
  }
}
