package com.igormaznitsa.annotator.ui.selection;

public enum MagicWandMode {

  /**
   * Match by RGB distance (best for colored regions).
   */
  COLOR,

  /**
   * Match by luminance only (best for grayscale or high-contrast edges).
   */
  LUMINANCE
}
