package com.igormaznitsa.annotator.ui.selection;

public record MagicWandSettings(double tolerance, MagicWandMode mode, double smoothness,
                                boolean livePreview) {

  public MagicWandSettings {
    tolerance = Math.max(0.0, Math.min(1.0, tolerance));
    smoothness = Math.max(0.0, Math.min(1.0, smoothness));
  }

  public static MagicWandSettings defaults() {
    return new MagicWandSettings(0.3, MagicWandMode.COLOR, 0.35, false);
  }
}
