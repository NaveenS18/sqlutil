package in.mystrn.sqlutil.utils;

import java.util.function.Consumer;

/**
 * A functional interface for a task that can be run in the ProcessingDialog.
 * It's like a Runnable that can throw an Exception and can
 * provide String updates back to the UI thread.
 */
@FunctionalInterface
public interface ProcessingTask {

    /**
     * The background task to execute.
     *
     * @param messageUpdater A consumer function. Call messageUpdater.accept("New message")
     * to update the text on the processing dialog. This is
     * thread-safe.
     * @throws Exception Any exception thrown will be caught and displayed in an
     * error dialog.
     */
    void run(Consumer<String> messageUpdater) throws Exception;
}