package com.itinventory.ui;

import com.itinventory.service.InventoryService;
import com.itinventory.util.OrgConfig;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class App extends Application {

    public static final String DATA_DIR   = "data";
    public static final String APP_NAME   = "Lake Erie Inventory";
    public static final String COPYRIGHT  = "\u00A9 Lake Erie Technical Solutions LLC  \u2014  All Rights Reserved 2026";

    private static InventoryService service;

    public static InventoryService getService() { return service; }

    @Override
    public void start(Stage stage) throws IOException {
        OrgConfig config = new OrgConfig();

        // ── Step 1: License agreement ─────────────────────────────────────────
        // Must be accepted on every first-time setup. Once accepted and stored,
        // the wizard only re-shows if isConfigured() is false.
        if (!config.isLicenseAccepted()) {
            SetupWizard wizard = new SetupWizard(config);
            wizard.showAndWait();

            // If the user declined, Platform.exit() was already called inside
            // the wizard. Guard here in case the window was closed another way.
            if (!config.isLicenseAccepted()) {
                Platform.exit();
                return;
            }
        }

        // ── Step 2: Setup wizard (org details + color) ────────────────────────
        if (!config.isConfigured()) {
            SetupWizard wizard = new SetupWizard(config);
            wizard.showAndWait();
            // Skip straight past the license page since it was already accepted;
            // the wizard handles this because isLicenseAccepted() will be true
            // and it will start on page 1 (Welcome) automatically.
        }

        // ── Step 3: Load inventory and show main window ───────────────────────
        service = new InventoryService(DATA_DIR);

        MainView mainView = new MainView(stage, service, config);

        Scene scene = new Scene(mainView.getRoot(), 1100, 740);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        String orgName = config.getOrgName().isBlank() ? APP_NAME : config.getOrgName();
        stage.setTitle(orgName + " - " + APP_NAME);
        stage.setMinWidth(820);
        stage.setMinHeight(580);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
