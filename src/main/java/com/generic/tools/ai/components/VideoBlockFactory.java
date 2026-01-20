package com.generic.tools.ai.components;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.Cursor;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.io.File;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VideoBlockFactory {

    static {
        try { Platform.startup(() -> {}); } catch (IllegalStateException ex) {}
        Platform.setImplicitExit(false);
    }

    public static JPanel createVideoComponent(String urlOrPath) {
        JPanel container = new JPanel(new BorderLayout());
        container.setOpaque(false);
        container.setPreferredSize(new Dimension(480, 270));
        container.setMaximumSize(new Dimension(480, 270));
        container.setBorder(BorderFactory.createLineBorder(java.awt.Color.GRAY, 1));

        JFXPanel jfxPanel = new JFXPanel();
        container.add(jfxPanel, BorderLayout.CENTER);

        Platform.runLater(() -> {
            try {
                String path = urlOrPath.trim();
                StackPane root = new StackPane();
                root.setStyle("-fx-background-color: black;");

                // --- 1. YOUTUBE HANDLING (Gemini Style) ---
                if (path.contains("youtube.com") || path.contains("youtu.be")) {
                    setupYouTubeThumbnail(root, path);
                }
                // --- 2. SUPPORTED MEDIA (MP4, MP3) -> Play Embedded ---
                else if (isSupportedMedia(path)) {
                    setupEmbeddedPlayer(root, path, container);
                }
                // --- 3. UNSUPPORTED FORMATS (MKV, AVI) -> Open Externally ---
                else {
                    setupExternalLauncher(root, path);
                }

                Scene scene = new Scene(root);
                jfxPanel.setScene(scene);

            } catch (Exception e) {
                reportError(container, e, urlOrPath);
            }
        });

        return container;
    }

    // --- LOGIC: EMBEDDED PLAYER (MP4/MP3) ---
    private static void setupEmbeddedPlayer(StackPane root, String path, JPanel container) {
        String source = resolveSource(path);
        Media media = new Media(source);
        MediaPlayer player = new MediaPlayer(media);
        MediaView mediaView = new MediaView(player);

        mediaView.setFitWidth(480);
        mediaView.setFitHeight(270);
        mediaView.setPreserveRatio(true);

        StackPane playBtn = createPlayButton();

        player.setOnError(() -> reportError(container, player.getError(), path));
        media.setOnError(() -> reportError(container, media.getError(), path));

        Runnable togglePlay = () -> {
            if (player.getStatus() == MediaPlayer.Status.PLAYING) {
                player.pause();
                playBtn.setVisible(true);
            } else {
                player.play();
                playBtn.setVisible(false);
            }
        };

        playBtn.setOnMouseClicked(e -> togglePlay.run());
        mediaView.setOnMouseClicked(e -> togglePlay.run());
        player.setOnEndOfMedia(() -> { player.stop(); playBtn.setVisible(true); });

        root.getChildren().addAll(mediaView, playBtn);
    }

    // --- LOGIC: YOUTUBE (Thumbnail -> System Browser) ---
    private static void setupYouTubeThumbnail(StackPane root, String url) {
        String id = extractYouTubeId(url);
        if (id != null) {
            String thumbUrl = "https://img.youtube.com/vi/" + id + "/mqdefault.jpg";
            ImageView thumbnail = new ImageView(new Image(thumbUrl));
            thumbnail.setFitWidth(480);
            thumbnail.setFitHeight(270);
            thumbnail.setPreserveRatio(true);
            root.getChildren().add(thumbnail);
        }

        StackPane playBtn = createPlayButton();
        root.getChildren().add(playBtn);
        
        root.setCursor(Cursor.HAND);
        root.setOnMouseClicked(e -> {
             try { Desktop.getDesktop().browse(new URI(url)); } catch (Exception ex) {}
        });
    }

    // --- LOGIC: EXTERNAL LAUNCHER ---
    private static void setupExternalLauncher(StackPane root, String path) {
        // Just show the play button and open system player on click
        StackPane playBtn = createPlayButton();
        root.getChildren().add(playBtn);
        root.setCursor(Cursor.HAND);
        root.setOnMouseClicked(e -> {
             try { 
                 if (path.startsWith("http")) Desktop.getDesktop().browse(new URI(path));
                 else Desktop.getDesktop().open(new File(path));
             } catch (Exception ex) {}
        });
    }

    // --- HELPERS ---
    private static boolean isSupportedMedia(String path) {
        String p = path.toLowerCase();
        return p.endsWith(".mp4") || p.endsWith(".mp3") || p.endsWith(".m4a") || p.endsWith(".wav");
    }

    private static StackPane createPlayButton() {
        Circle bgCircle = new Circle(30, Color.rgb(0, 0, 0, 0.6));
        bgCircle.setStroke(Color.WHITE);
        bgCircle.setStrokeWidth(2);
        Polygon playTriangle = new Polygon(0.0, 0.0, 0.0, 30.0, 25.0, 15.0);
        playTriangle.setFill(Color.WHITE);
        StackPane btn = new StackPane(bgCircle, playTriangle);
        btn.setCursor(Cursor.HAND);
        return btn;
    }

    private static String extractYouTubeId(String url) {
        Matcher m = Pattern.compile("(?:v=|\\/|youtu\\.be\\/)([0-9A-Za-z_-]{11})").matcher(url);
        return m.find() ? m.group(1) : null;
    }

    private static String resolveSource(String path) {
        // FIX: Encode spaces to %20 or JavaFX Media will crash
        path = path.replace(" ", "%20").replace("\\", "/");
        if (path.startsWith("http")) return path;
        try { return new File(path).toURI().toString(); } catch (Exception e) { return path; }
    }

    private static void reportError(JPanel container, Throwable e, String path) {
        SwingUtilities.invokeLater(() -> {
            container.removeAll();
            container.setLayout(new BorderLayout());
            container.setBackground(java.awt.Color.BLACK);
            JLabel err = new JLabel("<html><center style='color:white'><b>Cannot Play Embedded</b><br>Click to open externally</center></html>", JLabel.CENTER);
            container.add(err, BorderLayout.CENTER);
            container.addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseClicked(java.awt.event.MouseEvent evt) {
                    try { 
                        if (path.startsWith("http")) Desktop.getDesktop().browse(new URI(path));
                        else Desktop.getDesktop().open(new File(path));
                    } catch (Exception ex) {}
                }
            });
            container.revalidate(); container.repaint();
        });
    }
}