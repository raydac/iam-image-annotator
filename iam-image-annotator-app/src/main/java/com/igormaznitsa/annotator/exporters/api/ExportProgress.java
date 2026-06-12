package com.igormaznitsa.annotator.exporters.api;

public record ExportProgress(String stage, int percent) {

  public ExportProgress {
    if (stage == null || stage.isBlank()) {
      throw new IllegalArgumentException("stage must not be blank");
    }
    percent = Math.clamp(percent, 0, 100);
  }
}
