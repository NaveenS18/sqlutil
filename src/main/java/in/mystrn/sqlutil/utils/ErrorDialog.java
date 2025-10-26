package in.mystrn.sqlutil.utils;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Component;

/**
 * A utility class for displaying standardized error dialogs in a Swing application.
 * This class ensures that all dialogs are shown on the Event Dispatch Thread (EDT).
 */
public final class ErrorDialog {

    private static final String DEFAULT_TITLE = "Error";

    private ErrorDialog() {
        // Private constructor to prevent instantiation
    }

    /**
     * Shows a simple, custom error message.
     *
     * @param parent  The parent component (can be null).
     * @param message The error message to display.
     */
    public static void showError(Component parent, String message) {
        showMessage(parent, message, DEFAULT_TITLE, JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Shows a user-friendly message while logging the full Throwable.
     * This is the recommended method for caught exceptions.
     *
     * @param parent      The parent component (can be null).
     * @param userMessage A user-friendly message (e.g., "Could not save file.").
     * @param t           The throwable (Exception or Error) that was caught.
     */
    public static void showError(Component parent, String userMessage, Throwable t) {
        // Log the full throwable for the developer
        System.err.println("User Message: " + userMessage);
        if (t != null) {
            t.printStackTrace();
        }
        
        // Show the user-friendly message on the EDT
        showMessage(parent, userMessage, DEFAULT_TITLE, JOptionPane.ERROR_MESSAGE);
    }

    /**
     * NEW METHOD: Shows an error message extracted directly from a Throwable.
     * This may show a technical message to the user.
     *
     * @param parent The parent component (can be null).
     * @param t      The throwable (Exception or Error) that was caught.
     */
    public static void showError(Component parent, Throwable t) {
        if (t == null) {
            showError(parent, "An unknown error occurred.");
            return;
        }

        // Log the full throwable for the developer
        t.printStackTrace();

        // Attempt to get a useful message from the throwable
        String errorMessage = t.getMessage();
        
        // Handle cases where getMessage() is null or blank
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            errorMessage = "An error occurred: " + t.getClass().getName();
        }
        
        // Show the extracted message on the EDT
        showMessage(parent, errorMessage, DEFAULT_TITLE, JOptionPane.ERROR_MESSAGE);
    }


    /**
     * Internal helper to ensure the message dialog is displayed on the
     * Event Dispatch Thread (EDT).
     */
    private static void showMessage(Component parent, String message, String title, int messageType) {
        // Check if we are on the EDT
        if (SwingUtilities.isEventDispatchThread()) {
            JOptionPane.showMessageDialog(parent, message, title, messageType);
        } else {
            // If not, queue the dialog to run on the EDT
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(parent, message, title, messageType);
            });
        }
    }
}