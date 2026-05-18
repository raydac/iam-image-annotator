package com.igormaznitsa.annotator.ui.tree;

import java.nio.file.Path;

public record FileTreeNode(Path path) {

  @Override
  public String toString() {
    return this.path.getFileName().toString();
  }
}
