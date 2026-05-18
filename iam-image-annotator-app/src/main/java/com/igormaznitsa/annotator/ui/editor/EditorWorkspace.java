package com.igormaznitsa.annotator.ui.editor;

import com.igormaznitsa.annotator.api.service.EditorSession;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public final class EditorWorkspace {

  private final Map<Path, EditorSession> sessions = new LinkedHashMap<>();
  private Path activePath;
  private Consumer<Void> changeListener = ignored -> {
  };

  public void setChangeListener(final Consumer<Void> listener) {
    this.changeListener = listener;
  }

  public Optional<EditorSession> activeSession() {
    return Optional.ofNullable(this.activePath).map(this.sessions::get);
  }

  public Path activePath() {
    return this.activePath;
  }

  public void setActive(final Path path) {
    if (this.sessions.containsKey(path)) {
      this.activePath = path;
      this.changeListener.accept(null);
    }
  }

  public EditorSession open(final EditorSession session) {
    this.sessions.put(session.filePath(), session);
    this.activePath = session.filePath();
    this.changeListener.accept(null);
    return session;
  }

  public boolean close(final Path path) {
    final boolean removed = this.sessions.remove(path) != null;
    if (removed && path.equals(this.activePath)) {
      this.activePath = this.sessions.keySet().stream().findFirst().orElse(null);
    }
    this.changeListener.accept(null);
    return removed;
  }

  public List<EditorSession> allSessions() {
    return List.copyOf(this.sessions.values());
  }

  public List<EditorSession> dirtySessions() {
    return this.sessions.values().stream().filter(EditorSession::isDirty).toList();
  }

  public boolean hasDirtySessions() {
    return this.sessions.values().stream().anyMatch(EditorSession::isDirty);
  }

  public void closeAllUnder(final Path folder) {
    final List<Path> toClose = new ArrayList<>();
    for (final Path path : this.sessions.keySet()) {
      if (path.startsWith(folder)) {
        toClose.add(path);
      }
    }
    toClose.forEach(this::close);
  }

  public void save(final Path path) throws IOException {
    final EditorSession session = this.sessions.get(path);
    if (session != null) {
      session.save(path);
      this.changeListener.accept(null);
    }
  }

  public void saveAs(final Path path, final Path target) throws IOException {
    final EditorSession session = this.sessions.get(path);
    if (session != null) {
      session.save(target);
      this.sessions.remove(path);
      this.sessions.put(target, session);
      if (path.equals(this.activePath)) {
        this.activePath = target;
      }
      this.changeListener.accept(null);
    }
  }

  public void saveAll() throws IOException {
    for (final EditorSession session : this.dirtySessions()) {
      session.save(session.filePath());
    }
    this.changeListener.accept(null);
  }
}
