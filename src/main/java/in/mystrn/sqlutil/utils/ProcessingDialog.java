package in.mystrn.sqlutil.utils;
import javax.swing.*;
import java.awt.*;
import java.net.URL; // Import URL
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * A utility to show a modal "Processing..." dialog, styled like ngx-spinner.
 * It uses a SwingWorker to ensure the long-running task doesn't block the EDT.
 */
public final class ProcessingDialog {

    private ProcessingDialog() {
        // Private constructor
    }

    /**
     * Shows a modal processing dialog and allows the task to update the
     * dialog's message.
     */
    public static void show(Component parent, String initialMessage, ProcessingTask task) {
        JLabel messageLabel = new JLabel(initialMessage, SwingConstants.CENTER);
        JDialog dialog = createDialog(parent, messageLabel);

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                Consumer<String> messageUpdater = this::publish;
                task.run(messageUpdater);
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                if (chunks != null && !chunks.isEmpty()) {
                    String latestMessage = chunks.get(chunks.size() - 1);
                    messageLabel.setText("<html><body style='text-align: center;'>" + latestMessage + "</body></html>");
                }
            }

            @Override
            protected void done() {
                dialog.dispose();
                try {
                    get();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    JOptionPane.showMessageDialog(
                        parent,
                        "Task failed: " + cause.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                    );
                    cause.printStackTrace();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };

        worker.execute();
        dialog.setVisible(true);
    }

    /**
     * (Original method for backward compatibility)
     * Shows a modal processing dialog with a static message.
     */
    public static void show(Component parent, String message, Runnable task) {
        ProcessingTask adaptableTask = (updater) -> {
            task.run();
        };
        show(parent, message, adaptableTask);
    }

    /**
     * --- THIS IS THE UPDATED METHOD ---
     * * Internal helper to create the ngx-spinner styled, non-closable,
     * modal JDialog.
     */
    private static JDialog createDialog(Component parent, JLabel messageLabel) {
        Window parentWindow = null;
        if (parent != null) {
            parentWindow = SwingUtilities.getWindowAncestor(parent);
        }

        JDialog dialog = new JDialog(parentWindow, Dialog.ModalityType.APPLICATION_MODAL);
        
        // --- Style Changes ---
        dialog.setUndecorated(true);
        // Set a semi-transparent black background (150/255 alpha)
        dialog.setBackground(Color.decode("0xFFeae0d5"));
        dialog.setResizable(false);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        // --- Full-Screen Logic ---
        // Get the bounds of the correct screen
        GraphicsConfiguration gc;
        if (parentWindow != null) {
            gc = parentWindow.getGraphicsConfiguration();
        } else {
            // If parent is null, use the default screen
            gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration();
        }
        Rectangle screenBounds = gc.getBounds();
        // Set the dialog's bounds to fill the entire screen
        dialog.setBounds(screenBounds);
        // --- End Full-Screen Logic ---


        // --- Centering Logic ---
        // We create a panel that holds the spinner and message
        JPanel spinnerPanel = new JPanel(new BorderLayout(15, 15));
        spinnerPanel.setOpaque(false); // Make it transparent
        spinnerPanel.setBorder(BorderFactory.createEmptyBorder(30, 40, 30, 40));

        // --- Spinner Component (GIF with JProgressBar fallback) ---
        JComponent spinnerComponent;
        try {
            URL spinnerUrl = ProcessingDialog.class.getResource("/spinner.gif");
            if (spinnerUrl != null) {
                ImageIcon spinnerIcon = new ImageIcon(spinnerUrl);
                spinnerComponent = new JLabel(spinnerIcon);
            } else {
                spinnerComponent = new JProgressBar();
                ((JProgressBar) spinnerComponent).setIndeterminate(true);
            }
            
            spinnerComponent.setBackground(Color.decode("0xFFeae0d5"));
        } catch (Exception e) {
            System.err.println("Could not load spinner.gif, falling back to JProgressBar.");
            spinnerComponent = new JProgressBar();
            ((JProgressBar) spinnerComponent).setIndeterminate(true);
        }
        spinnerPanel.add(spinnerComponent, BorderLayout.CENTER);
        // --- End Spinner Component ---

        // --- Message Label ---
        messageLabel.setForeground(Color.WHITE);
        messageLabel.setText("<html><body style='text-align: center;'>" + messageLabel.getText() + "</body></html>");
        spinnerPanel.add(messageLabel, BorderLayout.SOUTH);

        // --- Add the spinnerPanel to the dialog's content pane ---
        // Set the dialog's content pane to use GridBagLayout,
        // which will center the spinnerPanel by default.
        Container contentPane = dialog.getContentPane();
        contentPane.setLayout(new GridBagLayout());
        contentPane.add(spinnerPanel, new GridBagConstraints());
        
        // --- NEW SIZING AND LOCATION LOGIC ---
        if (parentWindow != null) {
            // If we have a parent, match its size and location
            dialog.setBounds(parentWindow.getBounds());
        } else {
            // Otherwise, just pack and center on screen (fallback)
            dialog.pack();
            dialog.setLocationRelativeTo(null);
        }
        
        return dialog;
    }
}