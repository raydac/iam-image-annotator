package com.igormaznitsa.annotator.ui.editor;

import com.igormaznitsa.annotator.ui.api.EditImageTool;
import com.igormaznitsa.annotator.ui.api.EditorPanelContext;
import com.igormaznitsa.annotator.ui.api.ImageViewAction;
import com.igormaznitsa.annotator.ui.api.ImageViewToggle;
import com.igormaznitsa.annotator.ui.dialog.LayerOrderDialog;
import com.igormaznitsa.annotator.ui.icons.IconService;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.border.EmptyBorder;

public final class ImageToolBar extends JPanel {

  private static final int TOOLBAR_BUTTON_PX = IconService.ICON_PX + 12;

  private final IconService icons;
  private final ImageCanvas canvas;
  private final Frame frame;
  private final List<EditImageTool> tools;
  private final List<ImageViewToggle> shapeToggles;
  private final List<ImageViewAction> shapeActions;
  private final List<ImageViewAction> actions;
  private final List<ImageViewToggle> viewToggles;
  private final ButtonGroup exclusiveGroup = new ButtonGroup();
  private final Map<EditImageTool, JToggleButton> toolButtons = new LinkedHashMap<>();
  private final Map<ImageViewToggle, JToggleButton> viewToggleButtons = new LinkedHashMap<>();
  private final Map<ImageViewAction, JButton> shapeActionButtons = new LinkedHashMap<>();

  public ImageToolBar(
      final IconService icons,
      final List<EditImageTool> tools,
      final List<ImageViewToggle> shapeToggles,
      final List<ImageViewAction> shapeActions,
      final List<ImageViewAction> actions,
      final List<ImageViewToggle> viewToggles,
      final ImageCanvas canvas,
      final Frame frame) {
    super();
    this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    this.setBorder(new EmptyBorder(4, 4, 4, 4));
    this.icons = icons;
    this.tools = List.copyOf(tools);
    this.shapeToggles = List.copyOf(shapeToggles);
    this.shapeActions = List.copyOf(shapeActions);
    this.actions = List.copyOf(actions);
    this.viewToggles = List.copyOf(viewToggles);
    this.canvas = Objects.requireNonNull(canvas, "canvas");
    this.frame = Objects.requireNonNull(frame, "frame");
    this.buildButtons();
  }

  private void buildButtons() {
    for (final EditImageTool tool : this.tools) {
      if (tool.isExclusive()) {
        final JToggleButton button = tool.createToggleButton(() -> this.activateExclusive(tool));
        this.configureToolbarButton(button, tool.iconFileName());
        this.exclusiveGroup.add(button);
        this.toolButtons.put(tool, button);
        this.addToolbarButton(button);
      }
    }
    this.addViewToggles(this.shapeToggles);
    this.addShapeActions(this.shapeActions);
    for (final ImageViewAction action : this.actions) {
      final JButton button = new JButton();
      this.configureToolbarButton(button, action.iconFileName());
      button.setToolTipText(action.tooltip());
      button.addActionListener(event -> action.execute(this.canvas));
      this.addToolbarButton(button);
    }
    this.addViewToggles(this.viewToggles);
    this.refreshToolbarState();
    final JButton layerButton = new JButton();
    this.configureToolbarButton(layerButton, "layer_arrange.png");
    layerButton.setToolTipText("Edit annotation layer order and names");
    layerButton.addActionListener(
        event -> LayerOrderDialog.showFor(this.frame, this.canvas));
    this.addToolbarButton(layerButton);
    this.add(Box.createVerticalGlue());
  }

  private void addShapeActions(final List<ImageViewAction> actions) {
    for (final ImageViewAction action : actions) {
      final JButton button = new JButton();
      this.configureToolbarButton(button, action.iconFileName());
      button.setToolTipText(action.tooltip());
      button.addActionListener(event -> {
        action.execute(this.canvas);
        this.refreshToolbarState(this.canvas);
      });
      this.shapeActionButtons.put(action, button);
      this.addToolbarButton(button);
    }
  }

  private void addViewToggles(final List<ImageViewToggle> toggles) {
    for (final ImageViewToggle toggle : toggles) {
      final JToggleButton button = new JToggleButton();
      this.configureToolbarButton(button, toggle.iconFileName());
      button.setToolTipText(toggle.tooltip());
      button.addActionListener(event -> {
        toggle.setSelected(this.canvas, button.isSelected());
        this.refreshViewToggles(this.canvas);
      });
      this.viewToggleButtons.put(toggle, button);
      this.addToolbarButton(button);
    }
  }

  private void addToolbarButton(final JComponent button) {
    button.setAlignmentX(Component.CENTER_ALIGNMENT);
    button.setFocusable(false);
    this.applyToolbarButtonSize(button);
    this.add(button);
    this.add(Box.createVerticalStrut(2));
  }

  private void configureToolbarButton(final AbstractButton button, final String iconFileName) {
    button.setIcon(this.icons.icon16(iconFileName));
    button.setText(null);
  }

  private void applyToolbarButtonSize(final JComponent button) {
    final Dimension square = new Dimension(TOOLBAR_BUTTON_PX, TOOLBAR_BUTTON_PX);
    button.setMinimumSize(square);
    button.setPreferredSize(square);
    button.setMaximumSize(square);
  }

  private void activateExclusive(final EditImageTool selected) {
    for (final EditImageTool tool : this.tools) {
      if (tool != selected) {
        tool.deactivate(this.canvas);
      }
    }
    selected.activate(this.canvas);
  }

  public void selectDefaultTool() {
    if (this.tools.isEmpty()) {
      return;
    }
    final EditImageTool first = this.tools.get(0);
    final JToggleButton button = this.toolButtons.get(first);
    if (button != null) {
      button.setSelected(true);
    }
    this.activateExclusive(first);
  }

  public void refreshToolbarState() {
    this.refreshToolbarState(this.canvas);
  }

  public void refreshToolbarState(final EditorPanelContext context) {
    this.refreshViewToggles(context);
    this.refreshShapeActions(context);
  }

  public void refreshViewToggles() {
    this.refreshViewToggles(this.canvas);
  }

  private void refreshShapeActions(final EditorPanelContext context) {
    for (final Map.Entry<ImageViewAction, JButton> entry : this.shapeActionButtons.entrySet()) {
      final ImageViewAction action = entry.getKey();
      final JButton button = entry.getValue();
      button.setEnabled(action.isEnabled(context));
      this.configureToolbarButton(button, action.iconFileName());
      button.setToolTipText(action.tooltip(context));
      this.applyToolbarButtonSize(button);
    }
  }

  public void refreshViewToggles(final EditorPanelContext context) {
    for (final Map.Entry<ImageViewToggle, JToggleButton> entry : this.viewToggleButtons.entrySet()) {
      final ImageViewToggle toggle = entry.getKey();
      final JToggleButton button = entry.getValue();
      button.setEnabled(toggle.isEnabled(context));
      this.configureToolbarButton(button, toggle.iconFileName(context));
      button.setToolTipText(toggle.tooltip(context));
      button.setSelected(toggle.isSelected(context));
      this.applyToolbarButtonSize(button);
    }
  }

  public void activateTool(final String toolId) {
    for (final EditImageTool tool : this.tools) {
      if (!tool.id().equals(toolId)) {
        continue;
      }
      final JToggleButton button = this.toolButtons.get(tool);
      if (button != null) {
        button.setSelected(true);
      }
      this.activateExclusive(tool);
      return;
    }
  }

  public void reactivateSelectedTool() {
    for (final EditImageTool tool : this.tools) {
      final JToggleButton button = this.toolButtons.get(tool);
      if (button != null && button.isSelected()) {
        this.activateExclusive(tool);
        return;
      }
    }
    this.selectDefaultTool();
  }
}
