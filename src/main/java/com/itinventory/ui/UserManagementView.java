package com.itinventory.ui;

import com.itinventory.model.UserAccount;
import com.itinventory.model.UserAccount.Role;
import com.itinventory.util.UserManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

/**
 * Admin-only User Management screen.
 * Accessible from Settings when logged in as an ADMIN user.
 *
 * Allows: adding users, editing roles, resetting access codes, disabling accounts.
 */
public class UserManagementView extends Stage {

    private final UserManager userManager;
    private final ObservableList<UserAccount> userList = FXCollections.observableArrayList();
    private TableView<UserAccount> table;
    private final Label statusLabel = new Label();

    // ── Constructor ───────────────────────────────────────────────────────────

    public UserManagementView(Stage owner, UserManager userManager) {
        this.userManager = userManager;

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("User Management");
        setResizable(true);
        setMinWidth(640);
        setMinHeight(480);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("wizard-root");
        root.setTop(buildHeader());
        root.setCenter(buildTableArea());
        root.setBottom(buildFooter());

        Scene scene = new Scene(root, 700, 520);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        setScene(scene);

        loadUsers();
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private VBox buildHeader() {
        VBox header = new VBox(4);
        header.getStyleClass().add("wizard-header");
        header.setPadding(new Insets(18, 24, 14, 24));

        Label title = new Label("User Management");
        title.getStyleClass().add("wizard-title");
        Label sub = new Label("Add and manage user accounts. Only Admins can access this screen.");
        sub.getStyleClass().add("wizard-subtitle");

        header.getChildren().addAll(title, sub);
        return header;
    }

    private VBox buildTableArea() {
        // Toolbar
        Button btnAdd    = new Button("+ Add User");
        Button btnEdit   = new Button("Edit");
        Button btnReset  = new Button("Reset Access Code");
        Button btnToggle = new Button("Enable / Disable");
        Button btnDelete = new Button("Delete");

        btnAdd.getStyleClass().add("btn-primary");
        btnEdit.getStyleClass().add("btn-secondary");
        btnReset.getStyleClass().add("btn-secondary");
        btnToggle.getStyleClass().add("btn-secondary");
        btnDelete.getStyleClass().add("btn-danger");

        btnAdd.setOnAction(e    -> openAddDialog());
        btnEdit.setOnAction(e   -> openEditDialog());
        btnReset.setOnAction(e  -> openResetCodeDialog());
        btnToggle.setOnAction(e -> toggleSelected());
        btnDelete.setOnAction(e -> deleteSelected());

        HBox toolbar = new HBox(8, btnAdd, btnEdit, btnReset, btnToggle, btnDelete);
        toolbar.setPadding(new Insets(12, 24, 8, 24));
        toolbar.setAlignment(Pos.CENTER_LEFT);

        // Table
        table = new TableView<>(userList);
        table.getStyleClass().add("asset-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No users found."));

        TableColumn<UserAccount, String> colUser = new TableColumn<>("Username");
        colUser.setCellValueFactory(new PropertyValueFactory<>("username"));
        colUser.setPrefWidth(160);

        TableColumn<UserAccount, Role> colRole = new TableColumn<>("Role");
        colRole.setCellValueFactory(new PropertyValueFactory<>("role"));
        colRole.setPrefWidth(130);
        colRole.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Role role, boolean empty) {
                super.updateItem(role, empty);
                if (empty || role == null) { setText(null); setGraphic(null); return; }
                Label lbl = new Label(role.displayName());
                lbl.getStyleClass().addAll("badge", roleBadgeStyle(role));
                setGraphic(lbl); setText(null);
            }
        });

        TableColumn<UserAccount, Boolean> colActive = new TableColumn<>("Status");
        colActive.setCellValueFactory(new PropertyValueFactory<>("active"));
        colActive.setPrefWidth(100);
        colActive.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Boolean active, boolean empty) {
                super.updateItem(active, empty);
                if (empty || active == null) { setText(null); setGraphic(null); return; }
                Label lbl = new Label(active ? "Active" : "Disabled");
                lbl.getStyleClass().addAll("badge",
                        active ? "badge-active" : "badge-inactive");
                setGraphic(lbl); setText(null);
            }
        });

        table.getColumns().addAll(colUser, colRole, colActive);

        // Double-click to edit
        table.setRowFactory(tv -> {
            TableRow<UserAccount> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) openEditDialog();
            });
            return row;
        });

        statusLabel.getStyleClass().add("status-label");
        statusLabel.setPadding(new Insets(4, 24, 4, 24));

        VBox area = new VBox(0, toolbar, table, statusLabel);
        VBox.setVgrow(table, Priority.ALWAYS);
        return area;
    }

    private VBox buildFooter() {
        HBox bar = new HBox();
        bar.setAlignment(Pos.CENTER_RIGHT);
        bar.setPadding(new Insets(10, 24, 8, 24));

        Button btnClose = new Button("Close");
        btnClose.getStyleClass().add("btn-secondary");
        btnClose.setOnAction(e -> close());
        bar.getChildren().add(btnClose);

        Label copyright = new Label(App.COPYRIGHT);
        copyright.getStyleClass().add("copyright-label");
        copyright.setMaxWidth(Double.MAX_VALUE);
        copyright.setAlignment(Pos.CENTER);
        copyright.setPadding(new Insets(0, 24, 8, 24));

        VBox footer = new VBox(0, bar, copyright);
        footer.getStyleClass().add("wizard-footer");
        return footer;
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    private void openAddDialog() {
        Dialog<UserAccount> dialog = new Dialog<>();
        dialog.initOwner(this);
        dialog.setTitle("Add User");
        dialog.getDialogPane().getStylesheets().add(
                getClass().getResource("/styles.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("dialog-pane");

        TextField tfUser = styledField("Username");
        PasswordField pfCode = new PasswordField();
        pfCode.setPromptText("Access Code (min 4 characters)");
        pfCode.getStyleClass().add("form-field");
        PasswordField pfConfirm = new PasswordField();
        pfConfirm.setPromptText("Confirm Access Code");
        pfConfirm.getStyleClass().add("form-field");

        ComboBox<Role> cbRole = new ComboBox<>();
        cbRole.getItems().addAll(Role.values());
        cbRole.setValue(Role.READ_WRITE);
        cbRole.setMaxWidth(Double.MAX_VALUE);
        cbRole.getStyleClass().add("filter-combo");

        Label errLabel = new Label();
        errLabel.getStyleClass().add("wizard-error");
        errLabel.setVisible(false);

        VBox content = new VBox(12,
            formRow("Username", tfUser),
            formRow("Role", cbRole),
            formRow("Access Code", pfCode),
            formRow("Confirm Code", pfConfirm),
            errLabel
        );
        content.setPadding(new Insets(20, 24, 10, 24));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(360);

        ButtonType addBtn = new ButtonType("Add User", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addBtn, ButtonType.CANCEL);
        ((Button) dialog.getDialogPane().lookupButton(addBtn))
                .getStyleClass().add("btn-primary");

        dialog.setResultConverter(btn -> {
            if (btn != addBtn) return null;
            String uname = tfUser.getText().trim();
            String code  = pfCode.getText();
            String conf  = pfConfirm.getText();

            if (uname.isBlank()) { show(errLabel, "Username is required."); return null; }
            if (code.length() < 4) { show(errLabel, "Access code must be at least 4 characters."); return null; }
            if (!code.equals(conf))  { show(errLabel, "Access codes do not match."); return null; }

            return new UserAccount(uname, code, cbRole.getValue());
        });

        dialog.showAndWait().ifPresent(user -> {
            try {
                userManager.addUser(user);
                loadUsers();
                setStatus("User '" + user.getUsername() + "' added.");
            } catch (Exception e) {
                showAlert("Could not add user: " + e.getMessage());
            }
        });
    }

    private void openEditDialog() {
        UserAccount selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { showAlert("Please select a user to edit."); return; }

        // Prevent editing the only admin
        if (selected.isAdmin()) {
            try {
                long adminCount = userManager.loadAll().stream()
                        .filter(UserAccount::isAdmin).count();
                if (adminCount <= 1) {
                    showAlert("Cannot change the role of the only admin account.");
                    return;
                }
            } catch (IOException e) { /* continue */ }
        }

        Dialog<Role> dialog = new Dialog<>();
        dialog.initOwner(this);
        dialog.setTitle("Edit User: " + selected.getUsername());
        dialog.getDialogPane().getStylesheets().add(
                getClass().getResource("/styles.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("dialog-pane");

        ComboBox<Role> cbRole = new ComboBox<>();
        cbRole.getItems().addAll(Role.values());
        cbRole.setValue(selected.getRole());
        cbRole.setMaxWidth(Double.MAX_VALUE);
        cbRole.getStyleClass().add("filter-combo");

        VBox content = new VBox(12, formRow("Role", cbRole));
        content.setPadding(new Insets(20, 24, 10, 24));
        dialog.getDialogPane().setContent(content);

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);
        ((Button) dialog.getDialogPane().lookupButton(saveBtn))
                .getStyleClass().add("btn-primary");

        dialog.setResultConverter(btn -> btn == saveBtn ? cbRole.getValue() : null);

        dialog.showAndWait().ifPresent(newRole -> {
            selected.setRole(newRole);
            try {
                userManager.updateUser(selected);
                loadUsers();
                setStatus("Role updated for '" + selected.getUsername() + "'.");
            } catch (IOException e) {
                showAlert("Could not update user: " + e.getMessage());
            }
        });
    }

    private void openResetCodeDialog() {
        UserAccount selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { showAlert("Please select a user."); return; }

        Dialog<String> dialog = new Dialog<>();
        dialog.initOwner(this);
        dialog.setTitle("Reset Access Code: " + selected.getUsername());
        dialog.getDialogPane().getStylesheets().add(
                getClass().getResource("/styles.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("dialog-pane");

        PasswordField pfNew     = new PasswordField();
        pfNew.setPromptText("New access code");
        pfNew.getStyleClass().add("form-field");
        PasswordField pfConfirm = new PasswordField();
        pfConfirm.setPromptText("Confirm new access code");
        pfConfirm.getStyleClass().add("form-field");

        Label errLabel = new Label();
        errLabel.getStyleClass().add("wizard-error");
        errLabel.setVisible(false);

        VBox content = new VBox(12,
            formRow("New Access Code", pfNew),
            formRow("Confirm Code", pfConfirm),
            errLabel
        );
        content.setPadding(new Insets(20, 24, 10, 24));
        dialog.getDialogPane().setContent(content);

        ButtonType saveBtn = new ButtonType("Reset Code", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);
        ((Button) dialog.getDialogPane().lookupButton(saveBtn))
                .getStyleClass().add("btn-primary");

        dialog.setResultConverter(btn -> {
            if (btn != saveBtn) return null;
            String code = pfNew.getText();
            String conf = pfConfirm.getText();
            if (code.length() < 4) { show(errLabel, "Code must be at least 4 characters."); return null; }
            if (!code.equals(conf)) { show(errLabel, "Codes do not match."); return null; }
            return code;
        });

        dialog.showAndWait().ifPresent(newCode -> {
            selected.setAccessCode(newCode);
            try {
                userManager.updateUser(selected);
                loadUsers();
                setStatus("Access code reset for '" + selected.getUsername() + "'.");
            } catch (IOException e) {
                showAlert("Could not reset code: " + e.getMessage());
            }
        });
    }

    private void toggleSelected() {
        UserAccount selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { showAlert("Please select a user."); return; }

        // Prevent disabling the only active admin
        if (selected.isAdmin() && selected.isActive()) {
            try {
                long activeAdminCount = userManager.loadAll().stream()
                        .filter(u -> u.isAdmin() && u.isActive()).count();
                if (activeAdminCount <= 1) {
                    showAlert("Cannot disable the only active admin account.");
                    return;
                }
            } catch (IOException e) { /* continue */ }
        }

        selected.setActive(!selected.isActive());
        try {
            userManager.updateUser(selected);
            loadUsers();
            setStatus("'" + selected.getUsername() + "' " +
                      (selected.isActive() ? "enabled." : "disabled."));
        } catch (IOException e) {
            showAlert("Could not update user: " + e.getMessage());
        }
    }

    private void deleteSelected() {
        UserAccount selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { showAlert("Please select a user to delete."); return; }

        // Prevent deleting the only admin
        if (selected.isAdmin()) {
            try {
                long adminCount = userManager.loadAll().stream()
                        .filter(UserAccount::isAdmin).count();
                if (adminCount <= 1) {
                    showAlert("Cannot delete the only admin account.");
                    return;
                }
            } catch (IOException e) { /* continue */ }
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(this);
        confirm.setTitle("Delete User");
        confirm.setHeaderText("Delete user '" + selected.getUsername() + "'?");
        confirm.setContentText("This cannot be undone.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    userManager.deleteUser(selected.getUsername());
                    loadUsers();
                    setStatus("User '" + selected.getUsername() + "' deleted.");
                } catch (IOException e) {
                    showAlert("Could not delete user: " + e.getMessage());
                }
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void loadUsers() {
        try {
            List<UserAccount> users = userManager.loadAll();
            userList.setAll(users);
            setStatus(users.size() + " user(s) total.");
        } catch (IOException e) {
            showAlert("Could not load users: " + e.getMessage());
        }
    }

    private String roleBadgeStyle(Role role) {
        return switch (role) {
            case ADMIN      -> "badge-missing";
            case READ_WRITE -> "badge-active";
            case VIEW_ONLY  -> "badge-inactive";
        };
    }

    private void setStatus(String msg) { statusLabel.setText(msg); }

    private void showAlert(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.initOwner(this);
        a.showAndWait();
    }

    private void show(Label lbl, String msg) {
        lbl.setText(msg);
        lbl.setVisible(true);
    }

    private VBox formRow(String labelText, javafx.scene.Node field) {
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("form-label");
        return new VBox(5, lbl, field);
    }

    private TextField styledField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.getStyleClass().add("form-field");
        tf.setMaxWidth(Double.MAX_VALUE);
        return tf;
    }
}
