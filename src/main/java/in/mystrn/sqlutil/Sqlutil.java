package in.mystrn.sqlutil;

import com.formdev.flatlaf.FlatIntelliJLaf;
import in.mystrn.sqlutil.forms.FrmQueryAnalyzer;
import java.awt.Color; // <-- IMPORT THIS
import java.awt.Insets;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder; // <-- IMPORT FOR PADDING

/**
 *
 * @author hive
 */
public class Sqlutil {

    public static void main(String[] args) {
        try {
            // 1. SET THE GLOBAL THEME
            FlatIntelliJLaf.setup();

            // 2. CUSTOMIZE THE THEME USING UIMANAGER
            UIManager.put("Button.arc", 10); // Rounded corners
            UIManager.put("Button.background", new Color(0, 100, 180));
            UIManager.put("Button.foreground", Color.WHITE);
            UIManager.put("Button.hoverBackground", new Color(0, 100, 180));

            // --- Example 4: Change the color of the glowing "focus" border ---
            // (e.g., when you tab to a text field or button)
            UIManager.put("Component.focusColor", new Color(0, 100, 180, 128)); // Blue with transparency
            UIManager.put("Component.focusWidth", 2); // Make the focus border 2px thick

            // --- Example 5: Add more padding to text fields ---
            // (top, left, bottom, right)
            UIManager.put("TextField.padding", new EmptyBorder(5, 8, 5, 8));

            // --- SET PADDING ---
            // (top, left, bottom, right)
            UIManager.put("Button.padding", new Insets(5, 12, 5, 12));
            UIManager.put("TextField.padding", new Insets(5, 8, 5, 8));
            UIManager.put("TextArea.padding", new Insets(8, 8, 8, 8));
            UIManager.put("ComboBox.padding", new Insets(5, 8, 5, 8));
            UIManager.put("Spinner.padding", new Insets(5, 8, 5, 8));

        } catch (Exception e) {
            e.printStackTrace();
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        // 3. CREATE YOUR UI
        // All components will now use the custom styles defined above
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new FrmQueryAnalyzer().setVisible(true);
            }
        });
    }
}
