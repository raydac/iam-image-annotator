package com.igormaznitsa.annotator.ui.operations;

import com.igormaznitsa.annotator.ui.api.TreeOperationContext;
import com.igormaznitsa.annotator.ui.api.TreeOperationIcon;

public final class RefreshTreeOperation implements TreeOperationIcon {

  @Override
  public String id() {
    return "refresh";
  }

  @Override
  public String tooltip() {
    return "Refresh root folder content";
  }

  @Override
  public String iconFileName() {
    return "folder_explorer.png";
  }

  @Override
  public boolean isEnabled(final TreeOperationContext context) {
    return context.hasOpenFolder();
  }

  @Override
  public void execute(final TreeOperationContext context) {
    context.refreshTree();
  }
}
