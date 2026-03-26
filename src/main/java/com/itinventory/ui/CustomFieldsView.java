package com.itinventory.ui;

import com.itinventory.service.InventoryService;
import com.itinventory.util.ChangeLogger;
import com.itinventory.util.ChangeLogger.Action;
import com.itinventory.util.CustomFieldsManager;
import com.itinventory.util.CustomFieldsManager.CustomEntry;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Admin-only screen for managing custom categories and statuses.
 * Enforces all nine guardrails defined in CustomFieldsManager.
 */
public class CustomFieldsView extends Stage {

    private final CustomFieldsManager cfm;
    private final InventoryService    service;
    private final ChangeLogger        logger;

    private final ObservableList<CustomEntry> catList  = FXCollections.observableArrayList();
    private final ObservableList<CustomEntry> statList = FXCollections.observableArrayList();

    private ListView<CustomEntry> catListView;
    private ListView<CustomEntry> statListView;
    private final Label statusLabel = new Label();

    // ── Constructor ───────────────────────────────────────────────────────────

    public CustomFieldsView(Stage owner, CustomFieldsManager cfm,
                            InventoryService service, ChangeLogger logger) {
        this.cfm     = cfm;
        this.service = service;
        this.logger  = logger;

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Custom Fields");
        setResizable(true);
        setMinWidth(700);
        setMinHeight(540);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("wizard-root");
        root.setTop(buildHeader());
        root.setCenter(buildBody());
        root.setBottom(buildFooter());

        Scene scene = new Scene(root, 760, 580);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        setScene(scene);

        refresh();
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private VBox buildHeader() {
        VBox header = new VBox(4);
        header.getStyleClass().add("wizard-header");
        header.setPadding(new Insets(18, 24, 14, 24));

        Label title = new Label("Custom Categories and Statuses");
        title.getStyleClass().add("wizard-title");
        Label sub = new Label(
            "Admin only. Built-in values are locked. Changes affect all users immediately.");
        sub.getStyleClass().add("wizard-subtitle");
        header.getChildren().addAll(title, sub);
        return header;
    }

    private HBox buildBody() {
        HBox body = new HBox(16);
        body.setPadding(new Insets(16, 24, 8, 24));
        VBox.setVgrow(body, Priority.ALWAYS);
        HBox.setHgrow(body, Priority.ALWAYS);

        body.getChildren().addAll(
            buildPanel("Categories", catList,
                () -> addEntry(true),
                () -> renameEntry(true),
                () -> toggleEntry(true),
                () -> deleteEntry(true)),
            buildPanel("Statuses", statList,
                () -> addEntry(false),
                () -> renameEntry(false),
                () -> toggleEntry(false),
                () -> deleteEntry(false))
        );
        return body;
    }

    private VBox buildPanel(String title,
                            ObservableList<CustomEntry> items,
                            Runnable onAdd, Runnable onRename,
                            Runnable onToggle, Runnable onDelete) {
        VBox panel = new VBox(8);
        HBox.setHgrow(panel, Priority.ALWAYS);

        Label heading = new Label(title);
        heading.getStyleClass().add("wizard-page-heading");

        // Built-in list (locked)
        boolean isCat = title.equals("Categories");
        VBox builtinBox = new VBox(4);
        builtinBox.getStyleClass().add("settings-section");
        builtinBox.setPadding(new Insets(8, 10, 8, 10));
        Label builtinLabel = new Label("BUILT-IN (LOCKED)");
        builtinLabel.getStyleClass().add("form-label");
        builtinBox.getChildren().add(builtinLabel);

        java.util.List<String> builtins = isCat
            ? CustomFieldsManager.BUILTIN_CATEGORIES
            : CustomFieldsManager.BUILTIN_STATUSES;
        for (String b : builtins) {
            Label lbl = new Label("🔒  " + b);
            lbl.getStyleClass().add("wizard-page-body");
            builtinBox.getChildren().add(lbl);
        }

        // Custom list
        ListView<CustomEntry> listView = new ListView<>(items);
        listView.getStyleClass().add("asset-table");
        listView.setPrefHeight(160);
        VBox.setVgrow(listView, Priority.ALWAYS);

        listView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(CustomEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                if (empty || entry == null) { setText(null); setGraphic(null); return; }
                Label nameLbl  = new Label(entry.name());
                nameLbl.getStyleClass().add(entry.active() ? "org-banner-name" : "status-label");
                Label statLbl  = new Label(entry.active() ? "Active" : "Disabled");
                statLbl.getStyleClass().addAll("badge",
                        entry.active() ? "badge-active" : "badge-inactive");
                HBox row = new HBox(10, nameLbl, statLbl);
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
            }
        });

        if (isCat) catListView  = listView;
        else       statListView = listView;

        // Toolbar
        Button btnAdd    = new Button("+ Add");
        Button btnRename = new Button("Rename");
        Button btnToggle = new Button("Enable / Disable");
        Button btnDelete = new Button("Delete");

        btnAdd.getStyleClass().add("btn-primary");
        btnRename.getStyleClass().add("btn-secondary");
        btnToggle.getStyleClass().add("btn-secondary");
        btnDelete.getStyleClass().add("btn-danger");

        btnAdd.setOnAction(e    -> onAdd.run());
        btnRename.setOnAction(e -> onRename.run());
        btnToggle.setOnAction(e -> onToggle.run());
        btnDelete.setOnAction(e -> onDelete.run());

        HBox toolbar = new HBox(6, btnAdd, btnRename, btnToggle, btnDelete);

        Label customLabel = new Label("CUSTOM");
        customLabel.getStyleClass().add("form-label");
        customLabel.setPadding(new Insets(8, 0, 2, 0));

        panel.getChildren().addAll(heading, builtinBox, customLabel, listView, toolbar);
        return panel;
    }

    private VBox buildFooter() {
        HBox bar = new HBox();
        bar.setAlignment(Pos.CENTER_RIGHT);
        bar.setPadding(new Insets(8, 24, 8, 24));

        statusLabel.getStyleClass().add("status-label");
        statusLabel.setPadding(new Insets(0, 0, 0, 4));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnClose = new Button("Close");
        btnClose.getStyleClass().add("btn-secondary");
        btnClose.setOnAction(e -> close());

        bar.getChildren().addAll(statusLabel, spacer, btnClose);

        Label copyright = new Label(App.COPYRIGHT);
        copyright.getStyleClass().add("copyright-label");
        copyright.setMaxWidth(Double.MAX_VALUE);
        copyright.setAlignment(Pos.CENTER);
        copyright.setPadding(new Insets(0, 24, 8, 24));

        VBox footer = new VBox(0, bar, copyright);
        footer.getStyleClass().add("wizard-footer");
        return footer;
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void addEntry(boolean isCategory) {
        String type = isCategory ? "Category" : "Status";
        TextInputDialog dialog = new TextInputDialog();
        dialog.initOwner(this);
        dialog.setTitle("Add Custom " + type);
        dialog.setHeaderText("Enter the new " + type.toLowerCase() + " name:");
        dialog.setContentText("Name:");

        dialog.showAndWait().ifPresent(input -> {
            // Live validation
            String err = CustomFieldsManager.validateNameInput(input);
            if (err != null) { showError(err); return; }

            // Confirm shared change
            if (!confirmSharedChange("Add " + type,
                    "Add '" + CustomFieldsManager.normalize(input) +
                    "' as a new " + type.toLowerCase() + "?\n\n" +
                    "This will update the " + type.toLowerCase() +
                    " list for all users immediately.")) return;

            try {
                if (isCategory) {
                    cfm.addCategory(input);
                    logger.log(Action.CUSTOM_CATEGORY_ADDED,
                            CustomFieldsManager.normalize(input),
                            "Custom category added");
                } else {
                    cfm.addStatus(input);
                    logger.log(Action.CUSTOM_STATUS_ADDED,
                            CustomFieldsManager.normalize(input),
                            "Custom status added");
                }
                refresh();
                setStatus(type + " '" + CustomFieldsManager.normalize(input) + "' added.");
            } catch (Exception e) {
                showError(e.getMessage());
            }
        });
    }

    private void renameEntry(boolean isCategory) {
        CustomEntry selected = isCategory
            ? catListView.getSelectionModel().getSelectedItem()
            : statListView.getSelectionModel().getSelectedItem();
        if (selected == null) { showError("Please select an entry to rename."); return; }

        String type = isCategory ? "Category" : "Status";
        TextInputDialog dialog = new TextInputDialog(selected.name());
        dialog.initOwner(this);
        dialog.setTitle("Rename " + type);
        dialog.setHeaderText("Rename '" + selected.name() + "' to:");
        dialog.setContentText("New name:");

        dialog.showAndWait().ifPresent(input -> {
            String err = CustomFieldsManager.validateNameInput(input);
            if (err != null) { showError(err); return; }

            if (!confirmSharedChange("Rename " + type,
                    "Rename '" + selected.name() + "' to '" +
                    CustomFieldsManager.normalize(input) + "'?\n\n" +
                    "Existing assets will still show the old name.\n" +
                    "New assets will use the new name.")) return;

            try {
                String oldName = selected.name();
                String newName = CustomFieldsManager.normalize(input);
                if (isCategory) {
                    cfm.renameCategory(oldName, newName);
                    logger.log(Action.CUSTOM_CATEGORY_RENAMED, newName,
                            "Category renamed", oldName, newName);
                } else {
                    cfm.renameStatus(oldName, newName);
                    logger.log(Action.CUSTOM_STATUS_RENAMED, newName,
                            "Status renamed", oldName, newName);
                }
                refresh();
                setStatus(type + " renamed to '" + newName + "'.");
            } catch (Exception e) {
                showError(e.getMessage());
            }
        });
    }

    private void toggleEntry(boolean isCategory) {
        CustomEntry selected = isCategory
            ? catListView.getSelectionModel().getSelectedItem()
            : statListView.getSelectionModel().getSelectedItem();
        if (selected == null) { showError("Please select an entry."); return; }

        boolean enabling = !selected.active();
        String type   = isCategory ? "Category" : "Status";
        String action = enabling ? "Enable" : "Disable";

        if (!confirmSharedChange(action + " " + type,
                action + " '" + selected.name() + "'?\n\n" +
                (enabling
                    ? "It will reappear in selection dropdowns for all users."
                    : "It will be hidden from dropdowns. Existing assets keep their value.")))
            return;

        try {
            if (isCategory) {
                cfm.setCategoryActive(selected.name(), enabling);
                logger.log(enabling ? Action.CUSTOM_CATEGORY_ENABLED
                                    : Action.CUSTOM_CATEGORY_DISABLED,
                        selected.name(),
                        "Category " + action.toLowerCase() + "d");
            } else {
                cfm.setStatusActive(selected.name(), enabling);
                logger.log(enabling ? Action.CUSTOM_STATUS_ENABLED
                                    : Action.CUSTOM_STATUS_DISABLED,
                        selected.name(),
                        "Status " + action.toLowerCase() + "d");
            }
            refresh();
            setStatus(type + " '" + selected.name() + "' " + action.toLowerCase() + "d.");
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void deleteEntry(boolean isCategory) {
        CustomEntry selected = isCategory
            ? catListView.getSelectionModel().getSelectedItem()
            : statListView.getSelectionModel().getSelectedItem();
        if (selected == null) { showError("Please select an entry to delete."); return; }

        String type = isCategory ? "Category" : "Status";

        // Check usage
        Collection<String> usedValues = service.getAllAssets().stream()
            .map(a -> isCategory
                ? (a.getCategory() != null ? a.getCategory().name() : "")
                : (a.getStatus()   != null ? a.getStatus().name()   : ""))
            .collect(Collectors.toList());

        if (!confirmSharedChange("Delete " + type,
                "Permanently delete '" + selected.name() + "'?\n\n" +
                "This cannot be undone. Use Disable instead if any assets use it."))
            return;

        try {
            if (isCategory) {
                cfm.deleteCategory(selected.name(), usedValues);
                logger.log(Action.CUSTOM_CATEGORY_DELETED, selected.name(),
                        "Custom category deleted");
            } else {
                cfm.deleteStatus(selected.name(), usedValues);
                logger.log(Action.CUSTOM_STATUS_DELETED, selected.name(),
                        "Custom status deleted");
            }
            refresh();
            setStatus(type + " '" + selected.name() + "' deleted.");
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void refresh() {
        catList.setAll(cfm.getCustomCategories());
        statList.setAll(cfm.getCustomStatuses());
    }

    private boolean confirmSharedChange(String title, String message) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(this);
        confirm.setTitle(title);
        confirm.setHeaderText(title);
        confirm.setContentText(message);
        confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        return confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.initOwner(this);
        a.showAndWait();
    }

    private void setStatus(String msg) {
        statusLabel.setText(msg);
    }
}
