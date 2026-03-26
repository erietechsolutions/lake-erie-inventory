package com.itinventory.ui;

import com.itinventory.service.InventoryService;
import com.itinventory.util.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.InetAddress;

public class App extends Application {

    public static final String APP_NAME   = "Lake Erie Inventory";
    public static final String VERSION    = "v1.0.4";
    public static final String COPYRIGHT  =
        "\u00A9 Lake Erie Technical Solutions LLC  \u2014  All Rights Reserved 2026";

    private static InventoryService    service;
    private static UserManager         userManager;
    private static CustomFieldsManager customFields;
    private static ChangeLogger        changeLogger;

    public static InventoryService    getService()      { return service; }
    public static UserManager         getUserManager()  { return userManager; }
    public static CustomFieldsManager getCustomFields() { return customFields; }
    public static ChangeLogger        getChangeLogger() { return changeLogger; }

    @Override
    public void start(Stage stage) throws IOException {
        OrgConfig config = new OrgConfig();

        // ── Step 1: License ───────────────────────────────────────────────────
        if (!config.isLicenseAccepted()) {
            SetupWizard wizard = new SetupWizard(config);
            wizard.showAndWait();
            if (!config.isLicenseAccepted()) { Platform.exit(); return; }
        }

        // ── Step 2: Setup wizard ──────────────────────────────────────────────
        if (!config.isConfigured()) {
            SetupWizard wizard = new SetupWizard(config);
            wizard.showAndWait();
        }

        // ── Step 3: Resolve data directory ────────────────────────────────────
        config.reload();
        String dataDir = config.getDataPath();

        // ── Step 4: Login ─────────────────────────────────────────────────────
        userManager = new UserManager(dataDir);
        LoginView login = new LoginView(config, userManager);
        login.showAndWait();
        if (!login.isLoginSuccessful()) { Platform.exit(); return; }

        // ── Step 5: Initialize shared services ────────────────────────────────
        customFields = new CustomFieldsManager(dataDir);

        String hostname = "unknown";
        try { hostname = InetAddress.getLocalHost().getHostName(); }
        catch (Exception ignored) {}

        changeLogger = new ChangeLogger(
            dataDir,
            userManager.getCurrentUser().getUsername(),
            hostname
        );

        // Prune old log entries on startup (silently in background)
        final String hn = hostname;
        Thread pruneThread = new Thread(() -> {
            try { changeLogger.prune(); }
            catch (IOException e) { /* non-fatal */ }
        }, "log-prune");
        pruneThread.setDaemon(true);
        pruneThread.start();

        // Log successful login
        changeLogger.log(ChangeLogger.Action.USER_LOGIN,
            userManager.getCurrentUser().getUsername(),
            "Logged in from " + hn);

        // ── Step 6: File locking ──────────────────────────────────────────────
        boolean readOnly = false;
        if (userManager.getCurrentUser().canEdit()) {
            String existingLock = userManager.checkLock();
            if (existingLock != null) {
                readOnly = true;
                javafx.scene.control.Alert lockAlert =
                    new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.INFORMATION);
                lockAlert.setTitle("Read-Only Mode");
                lockAlert.setHeaderText("Inventory is currently in use");
                lockAlert.setContentText(
                    UserManager.describeLock(existingLock) + "\n\n" +
                    "You can still view and export data.");
                lockAlert.showAndWait();
            } else {
                userManager.acquireLock();
                changeLogger.log(ChangeLogger.Action.LOCK_ACQUIRED,
                    dataDir, "Lock acquired");
            }
        }

        // ── Step 7: Load inventory ────────────────────────────────────────────
        service = new InventoryService(dataDir);

        // ── Step 8: Show main window ──────────────────────────────────────────
        MainView mainView = new MainView(stage, service, config,
                userManager, customFields, changeLogger, readOnly);

        Scene scene = new Scene(mainView.getRoot(), 1100, 740);
        scene.getStylesheets().add(
                getClass().getResource("/styles.css").toExternalForm());

        String orgName = config.getOrgName().isBlank() ? APP_NAME : config.getOrgName();
        stage.setTitle(orgName + " - " + APP_NAME + " " + VERSION +
                " [" + userManager.getCurrentUser().getUsername() + "]" +
                (readOnly ? " (Read Only)" : ""));
        stage.setMinWidth(820);
        stage.setMinHeight(580);
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            userManager.releaseLock();
            changeLogger.log(ChangeLogger.Action.LOCK_RELEASED,
                dataDir, "App closed");
        });
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
