package com.igormaznitsa.annotator.api.export;

import com.igormaznitsa.annotator.api.json.AnnotationJsonCodec;
import com.igormaznitsa.annotator.api.model.AnnotationDocument;
import com.igormaznitsa.annotator.api.service.EditorSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AnnotationExporter {

  public void exportJson(final EditorSession session, final Path target) throws IOException {
    this.writeBytes(target, new AnnotationJsonCodec().encode(session.document()));
  }

  public void exportJsonDocument(final AnnotationDocument document, final Path target)
      throws IOException {
    this.writeBytes(target, new AnnotationJsonCodec().encode(document));
  }

  private void writeBytes(final Path target, final byte[] bytes) throws IOException {
    Files.createDirectories(target.getParent());
    Files.write(target, bytes);
  }
}
