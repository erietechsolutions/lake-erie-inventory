package com.itinventory.ui;

import com.itinventory.service.InventoryService;
import com.itinventory.util.OrgConfig;
import com.itinventory.util.UserManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class App extends Application {

    public static final String APP_NAME  = "Lake Erie Inventory";
    public static final String COPYRIGHT =
        "\u00A9 Lake Erie Technical Solutions LLC  \u2014  All Rights Reserved 2026";

    private static InventoryService service;
    private static UserManager      userManager;

    public static InventoryService getService()    { return service; }
    public static UserManager      getUserManager(){ return userManager; }

    @Override
    public void start(Stage stage) throws IOException {
        OrgConfig config = new OrgConfig();

        // ── Step 1: License ───────────────────────────────────────────────────
        if (!config.isLicenseAccepted()) {
            SetupWizard wizard = new SetupWizard(config);
            wizard.showAndWait();
            if (!config.isLicenseAccepted()) {
                Platform.exit();
                return;
            }
        }

        // ── Step 2: Setup wizard ──────────────────────────────────────────────
        if (!config.isConfigured()) {
            SetupWizard wizard = new SetupWizard(config);
            wizard.showAndWait();
        }

        // ── Step 3: Resolve data directory ────────────────────────────────────
        String dataDir = config.getDataPath(); // local "data" or shared path

        // ── Step 4: User management and login ─────────────────────────────────
        userManager = new UserManager(dataDir);

        LoginView login = new LoginView(config, userManager);
        login.showAndWait();

        if (!login.isLoginSuccessful()) {
            Platform.exit();
            return;
        }

        // ── Step 5: File locking ──────────────────────────────────────────────
        boolean readOnly = false;
        if (userManager.getCurrentUser().canEdit()) {
            String existingLock = userManager.checkLock();
            if (existingLock != null) {
                // Another user has the lock - show read-only notice
                readOnly = true;
                javafx.scene.control.Alert lockAlert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.INFORMATION);
                lockAlert.setTitle("Read-Only Mode");
                lockAlert.setHeaderText("Inventory is currently in use");
                lockAlert.setContentText(
                    UserManager.describeLock(existingLock) + "\n\n" +
                    "You can still view and export data.");
                lockAlert.showAndWait();
            } else {
                userManager.acquireLock();
            }
        }

        // ── Step 6: Load inventory ────────────────────────────────────────────
        service = new InventoryService(dataDir);

        // ── Step 7: Show main window ──────────────────────────────────────────
        MainView mainView = new MainView(stage, service, config,
                userManager, readOnly);

        Scene scene = new Scene(mainView.getRoot(), 1100, 740);
        scene.getStylesheets().add(
                getClass().getResource("/styles.css").toExternalForm());

        String orgName = config.getOrgName().isBlank() ? APP_NAME : config.getOrgName();
        stage.setTitle(orgName + " - " + APP_NAME +
                " [" + userManager.getCurrentUser().getUsername() + "]" +
                (readOnly ? " (Read Only)" : ""));
        stage.setMinWidth(820);
        stage.setMinHeight(580);
        stage.setScene(scene);

        // Release lock on close
        stage.setOnCloseRequest(e -> userManager.releaseLock());

        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
