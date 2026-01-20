package com.generic.tools.ai.components;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;

public class VideoBlockFactory {

    // Helper to ensure JavaFX Platform doesn't exit when the last video closes
    static {
        try {
            Platform.startup(() -> {}); 
        } catch (IllegalStateException ex) {
            // Toolkit already initialized; ignore
        }
        Platform.setImplicitExit(false);
    }

    public static JPanel createVideoComponent(String videoUrlOrPath) {
        // 1. Swing Container
        JPanel container = new JPanel(new BorderLayout());
        container.setOpaque(false);
        container.setPreferredSize(new Dimension(480, 280)); // 16:9 Aspect Ratio
        container.setMaximumSize(new Dimension(480, 280));
        container.setBorder(BorderFactory.createLineBorder(java.awt.Color.GRAY, 1));

        // 2. JFXPanel: The bridge between Swing and JavaFX
        JFXPanel jfxPanel = new JFXPanel();
        container.add(jfxPanel, BorderLayout.CENTER);

        // 3. Initialize JavaFX Content (Must run on JavaFX Thread)
        Platform.runLater(() -> {
            try {
                String source = resolveSource(videoUrlOrPath);
                Media media = new Media(source);
                MediaPlayer player = new MediaPlayer(media);
                MediaView mediaView = new MediaView(player);

                // Resize video to fit panel
                mediaView.setFitWidth(480);
                mediaView.setFitHeight(270);
                mediaView.setPreserveRatio(true);

                // --- OVERLAY: Play Button ---
                StackPane root = new StackPane();
                root.setStyle("-fx-background-color: black;"); // Black background before load

                // Play Icon (Circle + Triangle)
                Circle bgCircle = new Circle(30, Color.rgb(255, 255, 255, 0.5));
                Polygon playTriangle = new Polygon(0.0, 0.0, 0.0, 30.0, 25.0, 15.0);
                playTriangle.setFill(Color.WHITE);
                
                StackPane playBtn = new StackPane(bgCircle, playTriangle);
                playBtn.setCursor(javafx.scene.Cursor.HAND);

                // --- CLICK LOGIC ---
                Runnable togglePlay = () -> {
                    if (player.getStatus() == MediaPlayer.Status.PLAYING) {
                        player.pause();
                        playBtn.setVisible(true);
                    } else {
                        player.play();
                        playBtn.setVisible(false);
                    }
                };

                // Allow clicking anywhere on video or button to toggle
                playBtn.setOnMouseClicked(e -> togglePlay.run());
                mediaView.setOnMouseClicked(e -> togglePlay.run());

                // Reset when video ends
                player.setOnEndOfMedia(() -> {
                    player.stop();
                    playBtn.setVisible(true);
                });

                // Assemble Scene
                root.getChildren().addAll(mediaView, playBtn);
                Scene scene = new Scene(root);
                jfxPanel.setScene(scene);

            } catch (Exception e) {
                // Handle Errors (e.g., format not supported)
                SwingUtilities.invokeLater(() -> {
                    container.removeAll();
                    JLabel errorLabel = new JLabel("<html><center>❌ Cannot Play Video<br><small>" + e.getMessage() + "</small></center></html>");
                    errorLabel.setHorizontalAlignment(JLabel.CENTER);
                    errorLabel.setForeground(java.awt.Color.RED);
                    container.add(errorLabel, BorderLayout.CENTER);
                    container.revalidate();
                    container.repaint();
                });
            }
        });

        return container;
    }

    private static String resolveSource(String path) {
        // JavaFX Media requires URI syntax (file:/... or http://...)
        if (path.startsWith("http") || path.startsWith("file:")) {
            return path;
        }
        try {
            return new java.io.File(path).toURI().toString();
        } catch (Exception e) {
            return path;
        }
    }
}