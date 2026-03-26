package com.itinventory.ui;

import com.itinventory.service.InventoryService;
import com.itinventory.util.OrgConfig;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Settings window - lets the user update organization info at any time.
 * Also contains the database reset and settings reset options,
 * which are only accessible when "Allow Reset" is enabled.
 */
public class SettingsView extends Stage {

    private final OrgConfig         config;
    private final InventoryService  service;
    private final Runnable          onSaved;
    private final Runnable          onDatabaseReset;

    // Form fields
    private final TextField        tfOrgName    = styledField();
    private final ComboBox<String> cbOrgType    = new ComboBox<>();
    private final TextField        tfAdmin      = styledField();
    private final TextField        tfColor      = styledField();
    private final Label            colorPreview = new Label();
    private final CheckBox         cbAllowReset = new CheckBox("Enable database and settings reset options");
    private final Label            errorLabel   = new Label();

    // Reset buttons - shown only when allow reset is on
    private Button btnResetDatabase;
    private Button btnResetSettings;
    private VBox   resetSection;

    // ── Constructor ───────────────────────────────────────────────────────────

    public SettingsView(Stage owner, OrgConfig config, InventoryService service,
                        Runnable onSaved, Runnable onDatabaseReset) {
        this.config          = config;
        this.service         = service;
        this.onSaved         = onSaved;
        this.onDatabaseReset = onDatabaseReset;

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Settings");
        setResizable(false);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("wizard-root");
        root.setTop(buildHeader());
        root.setCenter(buildForm());
        root.setBottom(buildButtons());

        Scene scene = new Scene(root, 480, 520);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        setScene(scene);

        populate();
        updateResetSectionVisibility();
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private VBox buildHeader() {
        VBox header = new VBox(4);
        header.getStyleClass().add("wizard-header");
        header.setPadding(new Insets(22, 28, 16, 28));

        Label title = new Label("  Organization Settings");
        title.getStyleClass().add("wizard-title");
        Label sub = new Label("Changes are saved immediately and reflected in the app");
        sub.getStyleClass().add("wizard-subtitle");

        header.getChildren().addAll(title, sub);
        return header;
    }

    private ScrollPane buildForm() {
        VBox form = new VBox(14);
        form.setPadding(new Insets(20, 28, 10, 28));

        cbOrgType.getItems().addAll(SetupWizard.ORG_TYPES);
        cbOrgType.setMaxWidth(Double.MAX_VALUE);
        cbOrgType.getStyleClass().add("filter-combo");

        colorPreview.setPrefSize(22, 22);
        colorPreview.setMinSize(22, 22);
        colorPreview.getStyleClass().add("color-swatch-preview");
        tfColor.textProperty().addListener((obs, o, n) -> updateColorPreview(n));

        HBox colorRow = new HBox(10, tfColor, colorPreview);
        colorRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(tfColor, Priority.ALWAYS);

        errorLabel.getStyleClass().add("wizard-error");
        errorLabel.setVisible(false);

        // ── Allow Reset toggle ────────────────────────────────────────────────
        cbAllowReset.getStyleClass().add("settings-checkbox");
        cbAllowReset.setOnAction(e -> {
            config.setResetAllowed(cbAllowReset.isSelected());
            updateResetSectionVisibility();
        });

        Label resetToggleNote = new Label(
            "When enabled, the Danger Zone below will allow you to permanently\n" +
            "delete all inventory data or reset all settings. Disabled by default.");
        resetToggleNote.getStyleClass().add("wizard-page-body");
        resetToggleNote.setWrapText(true);

        VBox resetToggleBox = new VBox(6, cbAllowReset, resetToggleNote);
        resetToggleBox.getStyleClass().add("settings-section");
        resetToggleBox.setPadding(new Insets(10, 12, 10, 12));

        // ── Danger Zone ───────────────────────────────────────────────────────
        resetSection = buildResetSection();

        form.getChildren().addAll(
            formRow("Organization Name", tfOrgName),
            formRow("Organization Type", cbOrgType),
            formRow("IT Contact / Admin", tfAdmin),
            formRow("Accent Color (hex)", colorRow),
            errorLabel,
            new Separator(),
            new Label("") {{ getStyleClass().add("settings-section-header"); setText("Reset Options"); }},
            resetToggleBox,
            resetSection
        );

        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("settings-scroll");
        return scroll;
    }

    private VBox buildResetSection() {
        Label dangerLabel = new Label("  Danger Zone");
        dangerLabel.getStyleClass().add("danger-zone-title");

        Label dbDesc = new Label(
            "Reset Database — Permanently deletes all inventory data.\n" +
            "A backup is saved automatically before deletion.");
        dbDesc.getStyleClass().add("wizard-page-body");
        dbDesc.setWrapText(true);

        btnResetDatabase = new Button("Reset Database");
        btnResetDatabase.getStyleClass().add("btn-danger");
        btnResetDatabase.setOnAction(e -> confirmResetDatabase());

        Label settingsDesc = new Label(
            "Reset Settings — Clears all organization settings and re-runs\n" +
            "the setup wizard on next launch.");
        settingsDesc.getStyleClass().add("wizard-page-body");
        settingsDesc.setWrapText(true);

        btnResetSettings = new Button("Reset Settings");
        btnResetSettings.getStyleClass().add("btn-danger");
        btnResetSettings.setOnAction(e -> confirmResetSettings());

        HBox dbRow = new HBox(14, dbDesc, btnResetDatabase);
        dbRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(dbDesc, Priority.ALWAYS);

        HBox settingsRow = new HBox(14, settingsDesc, btnResetSettings);
        settingsRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(settingsDesc, Priority.ALWAYS);

        VBox section = new VBox(12, dangerLabel, dbRow, new Separator(), settingsRow);
        section.getStyleClass().add("danger-zone");
        section.setPadding(new Insets(12, 14, 12, 14));
        return section;
    }

    private HBox buildButtons() {
        HBox bar = new HBox(10);
        bar.getStyleClass().add("wizard-footer");
        bar.setAlignment(Pos.CENTER_RIGHT);
        bar.setPadding(new Insets(14, 28, 20, 28));

        Button btnCancel = new Button("Cancel");
        btnCancel.getStyleClass().add("btn-secondary");
        btnCancel.setOnAction(e -> close());

        Button btnSave = new Button("Save Changes");
        btnSave.getStyleClass().add("btn-primary");
        btnSave.setOnAction(e -> save());

        Button btnWizard = new Button("Run Setup Wizard");
        btnWizard.getStyleClass().add("btn-secondary");
        btnWizard.setOnAction(e -> {
            close();
            SetupWizard wizard = new SetupWizard(config);
            wizard.showAndWait();
            if (wizard.isCompleted() && onSaved != null) onSaved.run();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bar.getChildren().addAll(btnWizard, spacer, btnCancel, btnSave);
        return bar;
    }

    // ── Logic ─────────────────────────────────────────────────────────────────

    private void populate() {
        tfOrgName.setText(config.getOrgName());
        cbOrgType.setValue(config.getOrgType().isBlank() ? null : config.getOrgType());
        tfAdmin.setText(config.getAdminName());
        tfColor.setText(config.getAccentColor());
        updateColorPreview(config.getAccentColor());
        cbAllowReset.setSelected(config.isResetAllowed());
    }

    private void updateResetSectionVisibility() {
        boolean allowed = cbAllowReset.isSelected();
        resetSection.setVisible(allowed);
        resetSection.setManaged(allowed);
    }

    private void save() {
        String name  = tfOrgName.getText().trim();
        String type  = cbOrgType.getValue();
        String admin = tfAdmin.getText().trim();
        String color = tfColor.getText().trim();

        if (name.isBlank()) {
            showError("Organization name cannot be empty.");
            return;
        }
        if (!color.matches("^#([0-9A-Fa-f]{6})$")) {
            showError("Please enter a valid hex color (e.g. #4f7cff).");
            return;
        }

        config.setOrgName(name);
        config.setOrgType(type != null ? type : "");
        config.setAdminName(admin);
        config.setAccentColor(color);
        config.setResetAllowed(cbAllowReset.isSelected());

        try {
            config.save();
            if (onSaved != null) onSaved.run();
            close();
        } catch (IOException e) {
            showError("Could not save settings: " + e.getMessage());
        }
    }

    private void confirmResetDatabase() {
        // Two-step confirmation for destructive action
        Alert step1 = new Alert(Alert.AlertType.WARNING);
        step1.initOwner(this);
        step1.setTitle("Reset Database");
        step1.setHeaderText("Are you sure you want to delete ALL inventory data?");
        step1.setContentText(
            "This will permanently delete every asset in your inventory.\n\n" +
            "A backup will be saved to data/backups/ before deletion,\n" +
            "but this action cannot be undone from within the app.\n\n" +
            "Click OK to continue to final confirmation.");
        step1.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

        step1.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;

            // Second confirmation - must type CONFIRM
            TextInputDialog step2 = new TextInputDialog();
            step2.initOwner(this);
            step2.setTitle("Final Confirmation");
            step2.setHeaderText("Type CONFIRM to permanently delete all inventory data.");
            step2.setContentText("Type CONFIRM:");

            step2.showAndWait().ifPresent(input -> {
                if (!"CONFIRM".equals(input.trim())) {
                    Alert cancelled = new Alert(Alert.AlertType.INFORMATION,
                        "Reset cancelled. Your data is safe.", ButtonType.OK);
                    cancelled.initOwner(this);
                    cancelled.showAndWait();
                    return;
                }

                try {
                    service.resetDatabase();
                    if (onDatabaseReset != null) onDatabaseReset.run();
                    close();
                    Alert done = new Alert(Alert.AlertType.INFORMATION,
                        "Database reset successfully. All inventory data has been deleted.\n" +
                        "A backup was saved to data/backups/", ButtonType.OK);
                    done.setTitle("Database Reset");
                    done.showAndWait();
                } catch (IOException e) {
                    showError("Reset failed: " + e.getMessage());
                }
            });
        });
    }

    private void confirmResetSettings() {
        Alert confirm = new Alert(Alert.AlertType.WARNING);
        confirm.initOwner(this);
        confirm.setTitle("Reset Settings");
        confirm.setHeaderText("Reset all organization settings?");
        confirm.setContentText(
            "This will clear your organization name, type, admin contact,\n" +
            "and color settings. The setup wizard will run again on next launch.\n\n" +
            "Your inventory data will NOT be affected.\n\n" +
            "This action cannot be undone.");
        confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;

            try {
                Files.deleteIfExists(Paths.get(OrgConfig.CONFIG_FILE));
                Alert done = new Alert(Alert.AlertType.INFORMATION,
                    "Settings have been reset. The setup wizard will run on next launch.",
                    ButtonType.OK);
                done.setTitle("Settings Reset");
                done.showAndWait();
                close();
                // Re-run wizard immediately
                OrgConfig fresh = new OrgConfig();
                SetupWizard wizard = new SetupWizard(fresh);
                wizard.showAndWait();
                if (onSaved != null) onSaved.run();
            } catch (IOException e) {
                showError("Could not reset settings: " + e.getMessage());
            }
        });
    }

    private void updateColorPreview(String hex) {
        try {
            Color.web(hex.trim());
            colorPreview.setStyle(String.format(
                "-fx-background-color: %s; -fx-background-radius: 4;", hex.trim()));
        } catch (Exception ex) {
            colorPreview.setStyle("-fx-background-color: #444; -fx-background-radius: 4;");
        }
    }

    private void showError(String msg) {
        errorLabel.setText("  " + msg);
        errorLabel.setVisible(true);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private VBox formRow(String labelText, javafx.scene.Node field) {
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("form-label");
        return new VBox(5, lbl, field);
    }

    private static TextField styledField() {
        TextField tf = new TextField();
        tf.getStyleClass().add("form-field");
        tf.setMaxWidth(Double.MAX_VALUE);
        return tf;
    }
}
