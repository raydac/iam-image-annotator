package com.igormaznitsa.annotator.exporters.common;

import java.io.File;
import javax.swing.filechooser.FileFilter;

public final class ExporterDirectoryFileFilter extends FileFilter {

  private final String description;

  public ExporterDirectoryFileFilter(final String description) {
    this.description = description;
  }

  @Override
  public boolean accept(final File file) {
    return file != null && file.isDirectory();
  }

  @Override
  public String getDescription() {
    return this.description;
  }
}
