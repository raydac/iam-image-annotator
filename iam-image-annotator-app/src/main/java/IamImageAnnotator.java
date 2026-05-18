import com.igormaznitsa.annotator.ui.MainFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class IamImageAnnotator {

  private IamImageAnnotator() {
  }

  public static void main(final String[] args) {
    SwingUtilities.invokeLater(() -> {
      try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      } catch (final Exception ignored) {
        // keep default LAF
      }
      final MainFrame frame = new MainFrame();
      frame.setLocationRelativeTo(null);
      frame.setVisible(true);
    });
  }
}
