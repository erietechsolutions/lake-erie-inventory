package com.itinventory.ui;

import com.itinventory.util.UpdateChecker;
import com.itinventory.util.UpdateChecker.UpdateResult;
import com.itinventory.util.UpdateChecker.Status;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Update check dialog for Lake Erie Inventory.
 *
 * Shows a "Checking..." spinner while the background thread runs,
 * then transitions to either:
 *   - An "Update Available" screen with release notes and a download link
 *   - An "Up to Date" confirmation
 *   - An error message if the check failed
 */
public class UpdateDialog extends Stage {

    private final StackPane contentArea = new StackPane();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "update-check");
        t.setDaemon(true);
        return t;
    });

    // ── Constructor ───────────────────────────────────────────────────────────

    public UpdateDialog(Stage owner) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Check for Updates");
        setResizable(false);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("wizard-root");
        root.setTop(buildHeader());
        root.setCenter(contentArea);
        root.setBottom(buildFooter());

        Scene scene = new Scene(root, 480, 320);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        setScene(scene);

        // Show spinner immediately, then kick off background check
        showChecking();
        executor.submit(this::runCheck);
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private VBox buildHeader() {
        VBox header = new VBox(4);
        header.getStyleClass().add("wizard-header");
        header.setPadding(new Insets(20, 28, 14, 28));

        Label title = new Label("Lake Erie Inventory - Update Check");
        title.getStyleClass().add("wizard-title");

        Label sub = new Label("Current version: " + UpdateChecker.CURRENT_VERSION);
        sub.getStyleClass().add("wizard-subtitle");

        header.getChildren().addAll(title, sub);
        return header;
    }

    // ── Footer ────────────────────────────────────────────────────────────────

    private HBox buildFooter() {
        HBox bar = new HBox();
        bar.getStyleClass().add("wizard-footer");
        bar.setAlignment(Pos.CENTER_RIGHT);
        bar.setPadding(new Insets(12, 28, 16, 28));

        Button btnClose = new Button("Close");
        btnClose.getStyleClass().add("btn-secondary");
        btnClose.setOnAction(e -> {
            executor.shutdownNow();
            close();
        });

        bar.getChildren().add(btnClose);
        return bar;
    }

    // ── Content states ────────────────────────────────────────────────────────

    private void showChecking() {
        VBox content = new VBox(16);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(30));

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.getStyleClass().add("update-spinner");
        spinner.setPrefSize(48, 48);

        Label label = new Label("Checking for updates...");
        label.getStyleClass().add("wizard-page-body");

        content.getChildren().addAll(spinner, label);
        contentArea.getChildren().setAll(content);
    }

    private void showUpdateAvailable(UpdateResult result) {
        VBox content = new VBox(14);
        content.setPadding(new Insets(24, 28, 10, 28));

        // Header row
        Label icon = new Label("🎉");
        icon.setStyle("-fx-font-size: 28px;");
        Label heading = new Label("Update Available!");
        heading.getStyleClass().add("update-available-heading");
        HBox headingRow = new HBox(10, icon, heading);
        headingRow.setAlignment(Pos.CENTER_LEFT);

        // Version info
        Label versionInfo = new Label(
            "Version " + result.latestVersion() + " is available  " +
            "(you have " + result.currentVersion() + ")");
        versionInfo.getStyleClass().add("wizard-page-body");

        // Release notes
        Label notesLabel = new Label("RELEASE NOTES");
        notesLabel.getStyleClass().add("form-label");

        TextArea notesArea = new TextArea(
            result.releaseNotes().isBlank() ? "No release notes provided." : result.releaseNotes());
        notesArea.setEditable(false);
        notesArea.setWrapText(true);
        notesArea.getStyleClass().add("license-text-area");
        notesArea.setPrefRowCount(5);

        // Download button
        Button btnDownload = new Button("Open Download Page");
        btnDownload.getStyleClass().add("btn-primary");
        btnDownload.setOnAction(e -> openUrl(result.releaseUrl()));

        content.getChildren().addAll(headingRow, versionInfo, notesLabel, notesArea, btnDownload);
        contentArea.getChildren().setAll(content);
        setHeight(420);
    }

    private void showUpToDate(UpdateResult result) {
        VBox content = new VBox(16);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(30));

        Label icon = new Label("✅");
        icon.setStyle("-fx-font-size: 40px;");

        Label heading = new Label("You're up to date!");
        heading.getStyleClass().add("wizard-page-heading");

        Label detail = new Label(
            UpdateChecker.CURRENT_VERSION + " is the latest version of Lake Erie Inventory.");
        detail.getStyleClass().add("wizard-page-body");
        detail.setWrapText(true);
        detail.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        content.getChildren().addAll(icon, heading, detail);
        contentArea.getChildren().setAll(content);
    }

    private void showNoReleases() {
        VBox content = new VBox(16);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(30));

        Label icon = new Label("📦");
        icon.setStyle("-fx-font-size: 40px;");

        Label heading = new Label("No releases published yet");
        heading.getStyleClass().add("wizard-page-heading");

        Label detail = new Label(
            "The GitHub repository has no releases yet.\n" +
            "You are running the latest available build.");
        detail.getStyleClass().add("wizard-page-body");
        detail.setWrapText(true);
        detail.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        content.getChildren().addAll(icon, heading, detail);
        contentArea.getChildren().setAll(content);
    }

    private void showError(UpdateResult result) {
        VBox content = new VBox(16);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(30));

        Label icon = new Label("⚠️");
        icon.setStyle("-fx-font-size: 40px;");

        Label heading = new Label("Update check failed");
        heading.getStyleClass().add("wizard-page-heading");

        Label detail = new Label(result.errorMessage());
        detail.getStyleClass().add("wizard-error");
        detail.setWrapText(true);
        detail.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        Button btnRetry = new Button("Try Again");
        btnRetry.getStyleClass().add("btn-secondary");
        btnRetry.setOnAction(e -> {
            showChecking();
            executor.submit(this::runCheck);
        });

        content.getChildren().addAll(icon, heading, detail, btnRetry);
        contentArea.getChildren().setAll(content);
    }

    // ── Background check ──────────────────────────────────────────────────────

    private void runCheck() {
        UpdateResult result = UpdateChecker.check();
        // Always update UI on the JavaFX Application Thread
        Platform.runLater(() -> {
            switch (result.status()) {
                case UPDATE_AVAILABLE -> showUpdateAvailable(result);
                case UP_TO_DATE       -> showUpToDate(result);
                case NO_RELEASES      -> showNoReleases();
                case ERROR            -> showError(result);
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception e) {
            // Show the URL in a dialog if browser won't open
            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                "Visit this URL to download:\n" + url, ButtonType.OK);
            alert.setTitle("Download Update");
            alert.initOwner(this);
            alert.showAndWait();
        }
    }
}
