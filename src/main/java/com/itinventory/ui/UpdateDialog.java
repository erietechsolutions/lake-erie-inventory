package com.itinventory.ui;

import com.itinventory.util.AutoUpdater;
import com.itinventory.util.AutoUpdater.Progress;
import com.itinventory.util.AutoUpdater.Step;
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
 * Update dialog for Lake Erie Inventory.
 *
 * States:
 *   1. Checking       - spinner while querying GitHub
 *   2. Update found   - shows version, release notes, and Install Update button
 *   3. Installing     - progress bar while AutoUpdater runs
 *   4. Complete       - success screen with Relaunch instructions
 *   5. Up to date     - confirmation screen
 *   6. Error          - error message with retry button
 */
public class UpdateDialog extends Stage {

    private final StackPane     contentArea = new StackPane();
    private final ExecutorService executor  = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "update-worker");
        t.setDaemon(true);
        return t;
    });

    private UpdateResult latestResult;

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

        Scene scene = new Scene(root, 500, 340);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        setScene(scene);

        showChecking();
        executor.submit(this::runCheck);
    }

    // ── Header / Footer ───────────────────────────────────────────────────────

    private VBox buildHeader() {
        VBox header = new VBox(4);
        header.getStyleClass().add("wizard-header");
        header.setPadding(new Insets(20, 28, 14, 28));

        Label title = new Label("Lake Erie Inventory - Updates");
        title.getStyleClass().add("wizard-title");
        Label sub = new Label("Current version: " + UpdateChecker.CURRENT_VERSION);
        sub.getStyleClass().add("wizard-subtitle");

        header.getChildren().addAll(title, sub);
        return header;
    }

    private VBox buildFooter() {
        HBox bar = new HBox();
        bar.setAlignment(Pos.CENTER_RIGHT);
        bar.setPadding(new Insets(12, 28, 8, 28));

        Button btnClose = new Button("Close");
        btnClose.getStyleClass().add("btn-secondary");
        btnClose.setOnAction(e -> { executor.shutdownNow(); close(); });
        bar.getChildren().add(btnClose);

        Label copyright = new Label(App.COPYRIGHT);
        copyright.getStyleClass().add("copyright-label");
        copyright.setMaxWidth(Double.MAX_VALUE);
        copyright.setAlignment(Pos.CENTER);
        copyright.setPadding(new Insets(0, 28, 10, 28));

        VBox footer = new VBox(0, bar, copyright);
        footer.getStyleClass().add("wizard-footer");
        return footer;
    }

    // ── Screen: Checking ──────────────────────────────────────────────────────

    private void showChecking() {
        VBox content = new VBox(16);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(40));

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.getStyleClass().add("update-spinner");
        spinner.setPrefSize(48, 48);

        Label label = new Label("Checking for updates...");
        label.getStyleClass().add("wizard-page-body");

        content.getChildren().addAll(spinner, label);
        contentArea.getChildren().setAll(content);
        setHeight(340);
    }

    // ── Screen: Update Available ──────────────────────────────────────────────

    private void showUpdateAvailable(UpdateResult result) {
        this.latestResult = result;

        VBox content = new VBox(12);
        content.setPadding(new Insets(20, 28, 10, 28));

        // Heading
        Label icon = new Label("\uD83C\uDF89");
        icon.setStyle("-fx-font-size: 26px;");
        Label heading = new Label("Update Available: " + result.latestVersion());
        heading.getStyleClass().add("update-available-heading");
        HBox headingRow = new HBox(10, icon, heading);
        headingRow.setAlignment(Pos.CENTER_LEFT);

        Label versionInfo = new Label(
            "You have " + result.currentVersion() + ".  " +
            result.latestVersion() + " is ready to install.");
        versionInfo.getStyleClass().add("wizard-page-body");

        // Release notes
        Label notesLabel = new Label("WHAT'S NEW");
        notesLabel.getStyleClass().add("form-label");

        TextArea notesArea = new TextArea(
            result.releaseNotes().isBlank() ? "No release notes provided." : result.releaseNotes());
        notesArea.setEditable(false);
        notesArea.setWrapText(true);
        notesArea.getStyleClass().add("license-text-area");
        notesArea.setPrefRowCount(4);

        // Action buttons
        Button btnInstall = new Button("Install Update Automatically");
        btnInstall.getStyleClass().add("btn-primary");
        btnInstall.setOnAction(e -> confirmAndInstall(result));

        Label orLabel = new Label("or");
        orLabel.getStyleClass().add("wizard-page-body");

        Button btnManual = new Button("Open Download Page");
        btnManual.getStyleClass().add("btn-secondary");
        btnManual.setOnAction(e -> openUrl(result.releaseUrl()));

        HBox btnRow = new HBox(12, btnInstall, orLabel, btnManual);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        content.getChildren().addAll(headingRow, versionInfo, notesLabel, notesArea, btnRow);
        contentArea.getChildren().setAll(content);
        setHeight(460);
    }

    // ── Screen: Confirm install ───────────────────────────────────────────────

    private void confirmAndInstall(UpdateResult result) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(this);
        confirm.setTitle("Install Update");
        confirm.setHeaderText("Install " + result.latestVersion() + " automatically?");
        confirm.setContentText(
            "The updater will:\n\n" +
            "  1. Download " + result.latestVersion() + " from GitHub\n" +
            "  2. Preserve all your inventory data and settings\n" +
            "  3. Replace the source files with the new version\n" +
            "  4. Delete compiled files so they rebuild cleanly\n\n" +
            "You will need to run run.bat once after the update to recompile.\n\n" +
            "A backup of the current source files will be saved to update_backup/\n" +
            "in case you need to roll back.\n\n" +
            "Continue?");
        confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) showInstalling(result);
        });
    }

    // ── Screen: Installing ────────────────────────────────────────────────────

    private void showInstalling(UpdateResult result) {
        VBox content = new VBox(14);
        content.setPadding(new Insets(24, 28, 10, 28));
        content.setAlignment(Pos.TOP_LEFT);

        Label heading = new Label("Installing " + result.latestVersion() + "...");
        heading.getStyleClass().add("wizard-page-heading");

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.getStyleClass().add("update-progress-bar");
        progressBar.setPrefHeight(18);

        Label stepLabel = new Label("Starting...");
        stepLabel.getStyleClass().add("wizard-page-body");

        Label detailLabel = new Label("");
        detailLabel.getStyleClass().add("status-label");
        detailLabel.setWrapText(true);

        Label warningLabel = new Label(
            "Do not close the app during the update.");
        warningLabel.getStyleClass().add("wizard-error");

        content.getChildren().addAll(heading, progressBar, stepLabel, detailLabel, warningLabel);
        contentArea.getChildren().setAll(content);
        setHeight(320);

        // Run the update in background, updating progress on FX thread
        executor.submit(() -> AutoUpdater.runUpdate(result.latestVersion(), progress ->
            Platform.runLater(() -> {
                if (progress.failed()) {
                    showInstallError(progress.errorMessage());
                    return;
                }

                // Update progress bar
                if (progress.percent() >= 0) {
                    progressBar.setProgress(progress.percent() / 100.0);
                } else {
                    progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
                }

                // Update step label
                stepLabel.setText(stepName(progress.step()));
                detailLabel.setText(progress.message());

                if (progress.step() == Step.COMPLETE) {
                    showInstallComplete(result.latestVersion());
                }
            })
        ));
    }

    // ── Screen: Install Complete ──────────────────────────────────────────────

    private void showInstallComplete(String version) {
        VBox content = new VBox(14);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(28));

        Label icon = new Label("\u2705");
        icon.setStyle("-fx-font-size: 44px;");

        Label heading = new Label("Update Complete!");
        heading.getStyleClass().add("update-available-heading");

        Label detail = new Label(
            version + " has been installed successfully.\n\n" +
            "Your inventory data and settings have been preserved.\n\n" +
            "Close this window and run run.bat to relaunch\n" +
            "the updated version of Lake Erie Inventory.");
        detail.getStyleClass().add("wizard-page-body");
        detail.setWrapText(true);
        detail.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        detail.setAlignment(Pos.CENTER);

        Label backupNote = new Label(
            "Previous source files backed up to update_backup/ if you need to roll back.");
        backupNote.getStyleClass().add("status-label");
        backupNote.setWrapText(true);
        backupNote.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        content.getChildren().addAll(icon, heading, detail, backupNote);
        contentArea.getChildren().setAll(content);
        setHeight(420);
    }

    // ── Screen: Install Error ─────────────────────────────────────────────────

    private void showInstallError(String errorMessage) {
        VBox content = new VBox(14);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(28));

        Label icon = new Label("\u26A0\uFE0F");
        icon.setStyle("-fx-font-size: 40px;");

        Label heading = new Label("Update Failed");
        heading.getStyleClass().add("wizard-page-heading");

        Label detail = new Label(errorMessage);
        detail.getStyleClass().add("wizard-error");
        detail.setWrapText(true);
        detail.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        Label recovery = new Label(
            "Your data is safe. No files were permanently changed.\n" +
            "Check update_backup/ if anything looks wrong.");
        recovery.getStyleClass().add("wizard-page-body");
        recovery.setWrapText(true);
        recovery.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        Button btnRetry = new Button("Try Again");
        btnRetry.getStyleClass().add("btn-primary");
        btnRetry.setOnAction(e -> {
            if (latestResult != null) confirmAndInstall(latestResult);
        });

        Button btnManual = new Button("Download Manually");
        btnManual.getStyleClass().add("btn-secondary");
        btnManual.setOnAction(e -> {
            if (latestResult != null) openUrl(latestResult.releaseUrl());
        });

        HBox btnRow = new HBox(10, btnRetry, btnManual);
        btnRow.setAlignment(Pos.CENTER);

        content.getChildren().addAll(icon, heading, detail, recovery, btnRow);
        contentArea.getChildren().setAll(content);
        setHeight(400);
    }

    // ── Screen: Up to Date ────────────────────────────────────────────────────

    private void showUpToDate(UpdateResult result) {
        VBox content = new VBox(16);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(40));

        Label icon = new Label("\u2705");
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
        setHeight(340);
    }

    // ── Screen: No Releases ───────────────────────────────────────────────────

    private void showNoReleases() {
        VBox content = new VBox(16);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(40));

        Label icon = new Label("\uD83D\uDCE6");
        icon.setStyle("-fx-font-size: 40px;");

        Label heading = new Label("No releases yet");
        heading.getStyleClass().add("wizard-page-heading");

        Label detail = new Label(
            "No releases have been published to GitHub yet.\n" +
            "You are running the latest available build.");
        detail.getStyleClass().add("wizard-page-body");
        detail.setWrapText(true);
        detail.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        content.getChildren().addAll(icon, heading, detail);
        contentArea.getChildren().setAll(content);
        setHeight(340);
    }

    // ── Screen: Check Error ───────────────────────────────────────────────────

    private void showCheckError(UpdateResult result) {
        VBox content = new VBox(16);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(40));

        Label icon = new Label("\u26A0\uFE0F");
        icon.setStyle("-fx-font-size: 40px;");

        Label heading = new Label("Could not check for updates");
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
        setHeight(360);
    }

    // ── Background check ──────────────────────────────────────────────────────

    private void runCheck() {
        UpdateResult result = UpdateChecker.check();
        Platform.runLater(() -> {
            switch (result.status()) {
                case UPDATE_AVAILABLE -> showUpdateAvailable(result);
                case UP_TO_DATE       -> showUpToDate(result);
                case NO_RELEASES      -> showNoReleases();
                case ERROR            -> showCheckError(result);
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String stepName(Step step) {
        return switch (step) {
            case FINDING_DOWNLOAD -> "Step 1 of 6 - Finding download...";
            case DOWNLOADING      -> "Step 2 of 6 - Downloading...";
            case EXTRACTING       -> "Step 3 of 6 - Extracting...";
            case MIGRATING_DATA   -> "Step 4 of 6 - Preserving your data...";
            case REPLACING_FILES  -> "Step 5 of 6 - Installing new files...";
            case CLEANING_UP      -> "Step 6 of 6 - Cleaning up...";
            case COMPLETE         -> "Complete!";
            case FAILED           -> "Failed.";
        };
    }

    private void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported() &&
                Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                "Visit this URL to download:\n" + url, ButtonType.OK);
            alert.setTitle("Download Update");
            alert.initOwner(this);
            alert.showAndWait();
        }
    }
}
