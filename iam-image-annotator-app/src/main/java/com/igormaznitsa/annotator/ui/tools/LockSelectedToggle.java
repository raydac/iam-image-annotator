package com.igormaznitsa.annotator.ui.tools;

import com.igormaznitsa.annotator.api.model.AnnotationEntry;
import com.igormaznitsa.annotator.ui.api.EditorPanelContext;
import com.igormaznitsa.annotator.ui.api.ImageViewToggle;

import java.util.Optional;

public final class LockSelectedToggle implements ImageViewToggle {

  private static final String ICON_LOCKED = "lock.png";
  private static final String ICON_UNLOCKED = "lock_open.png";

  @Override
  public String id() {
    return "lock-selected";
  }

  @Override
  public String tooltip() {
    return "Lock selected shape";
  }

  @Override
  public String iconFileName() {
    return ICON_UNLOCKED;
  }

  @Override
  public String iconFileName(final EditorPanelContext context) {
    return this.isSelected(context) ? ICON_LOCKED : ICON_UNLOCKED;
  }

  @Override
  public String tooltip(final EditorPanelContext context) {
    return this.selectedEntry(context)
        .map(entry -> entry.locked()
            ? "Unlock \"%s\" (allow point edits and rename)".formatted(entry.id())
            : "Lock \"%s\" (block point edits and rename)".formatted(entry.id()))
        .orElse("Select a shape to lock or unlock");
  }

  @Override
  public boolean isSelected(final EditorPanelContext context) {
    return this.selectedEntry(context).map(AnnotationEntry::locked).orElse(false);
  }

  @Override
  public boolean isEnabled(final EditorPanelContext context) {
    return this.selectedEntry(context).isPresent();
  }

  @Override
  public void setSelected(final EditorPanelContext context, final boolean selected) {
    this.selectedEntry(context).ifPresent(entry -> {
      context.session().recordUndoCheckpoint();
      context.session().document().setLocked(entry.id(), selected);
      if (selected) {
        context.session().clearVertexSelection();
      }
      context.markDirty();
      context.updateStatus(selected
          ? "Locked " + entry.id()
          : "Unlocked " + entry.id());
      context.repaintCanvas();
    });
  }

  private Optional<AnnotationEntry> selectedEntry(final EditorPanelContext context) {
    return context.selectedAnnotation()
        .flatMap(name -> context.session().document().findById(name));
  }
}
