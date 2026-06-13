import com.igormaznitsa.annotator.ui.MainFrame;
import com.igormaznitsa.annotator.ui.dialog.SettingsDialog;
import javax.swing.SwingUtilities;

public final class IamImageAnnotator {

  private IamImageAnnotator() {
  }

  public static void main(final String[] args) {
    SwingUtilities.invokeLater(() -> {
      SettingsDialog.installStartupLookAndFeel();
      final MainFrame frame = new MainFrame();
      frame.setLocationRelativeTo(null);
      frame.setVisible(true);
    });
  }
}
