package com.generic.tools.ai.helper;

import javax.swing.*;

public class UIBackgroundTaskExecutor {

    public static void run(Runnable task) {

        SwingWorker<Void, Void> worker = new SwingWorker<>() {

            @Override
            protected Void doInBackground() {
                
                task.run();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get(); // rethrow exception if any
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(null,
                            ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
    }
}
