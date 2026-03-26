package com.itinventory.ui;

import com.itinventory.model.UserAccount;
import com.itinventory.util.OrgConfig;
import com.itinventory.util.UserManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;

/**
 * Login screen shown before the main window loads.
 * Authenticates against users.csv in the shared data directory.
 *
 * If no users exist yet (first-time setup), shows the Create First Admin screen.
 */
public class LoginView extends Stage {

    private final OrgConfig    config;
    private final UserManager  userManager;
    private boolean            loginSuccessful = false;

    // Form controls
    private final TextField     tfUsername   = new TextField();
    private final PasswordField pfAccessCode = new PasswordField();
    private final Label         errorLabel   = new Label();
    private final Button        btnLogin     = new Button("Sign In");

    // ── Constructor ───────────────────────────────────────────────────────────

    public LoginView(OrgConfig config, UserManager userManager) {
        this.config      = config;
        this.userManager = userManager;

        initStyle(StageStyle.UNDECORATED);
        initModality(Modality.APPLICATION_MODAL);
        setResizable(false);

        Scene scene;
        try {
            if (userManager.hasNoUsers()) {
                scene = buildFirstAdminScene();
            } else {
                scene = buildLoginScene();
            }
        } catch (IOException e) {
            scene = buildLoginScene();
        }

        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        setScene(scene);
    }

    public boolean isLoginSuccessful() { return loginSuccessful; }

    // ── Login scene ───────────────────────────────────────────────────────────

    private Scene buildLoginScene() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("wizard-root");

        // Header
        VBox header = new VBox(6);
        header.getStyleClass().add("wizard-header");
        header.setPadding(new Insets(28, 36, 20, 36));
        header.setAlignment(Pos.CENTER);

        Label appIcon = new Label("🏷");
        appIcon.setStyle("-fx-font-size: 36px;");

        Label appTitle = new Label(App.APP_NAME);
        appTitle.getStyleClass().add("wizard-title");
        appTitle.setAlignment(Pos.CENTER);

        String orgName = config.getOrgName().isBlank() ? "" : config.getOrgName();
        Label orgLabel = new Label(orgName);
        orgLabel.getStyleClass().add("wizard-subtitle");

        header.getChildren().addAll(appIcon, appTitle, orgLabel);

        // Form
        VBox form = new VBox(14);
        form.setPadding(new Insets(28, 36, 10, 36));

        tfUsername.setPromptText("Username");
        tfUsername.getStyleClass().add("form-field");
        tfUsername.setMaxWidth(Double.MAX_VALUE);

        pfAccessCode.setPromptText("Access Code");
        pfAccessCode.getStyleClass().add("form-field");
        pfAccessCode.setMaxWidth(Double.MAX_VALUE);

        // Allow Enter key to submit
        pfAccessCode.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) handleLogin();
        });
        tfUsername.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) pfAccessCode.requestFocus();
        });

        errorLabel.getStyleClass().add("wizard-error");
        errorLabel.setVisible(false);
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(Double.MAX_VALUE);

        btnLogin.getStyleClass().add("btn-primary");
        btnLogin.setMaxWidth(Double.MAX_VALUE);
        btnLogin.setOnAction(e -> handleLogin());

        form.getChildren().addAll(
            formRow("Username", tfUsername),
            formRow("Access Code", pfAccessCode),
            errorLabel,
            btnLogin
        );

        // Footer
        VBox footer = new VBox(4);
        footer.getStyleClass().add("wizard-footer");
        footer.setPadding(new Insets(10, 36, 14, 36));
        footer.setAlignment(Pos.CENTER);

        Label copyright = new Label(App.COPYRIGHT);
        copyright.getStyleClass().add("copyright-label");
        copyright.setAlignment(Pos.CENTER);
        footer.getChildren().add(copyright);

        root.setTop(header);
        root.setCenter(form);
        root.setBottom(footer);

        return new Scene(root, 400, 420);
    }

    // ── First admin scene ─────────────────────────────────────────────────────

    private Scene buildFirstAdminScene() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("wizard-root");

        // Header
        VBox header = new VBox(6);
        header.getStyleClass().add("wizard-header");
        header.setPadding(new Insets(22, 36, 16, 36));

        Label title = new Label("Create Admin Account");
        title.getStyleClass().add("wizard-title");
        Label sub = new Label("No users exist yet. Create the first admin account to get started.");
        sub.getStyleClass().add("wizard-subtitle");
        sub.setWrapText(true);
        header.getChildren().addAll(title, sub);

        // Form
        VBox form = new VBox(14);
        form.setPadding(new Insets(24, 36, 10, 36));

        TextField tfNewUsername = new TextField();
        tfNewUsername.setPromptText("Choose a username");
        tfNewUsername.getStyleClass().add("form-field");
        tfNewUsername.setMaxWidth(Double.MAX_VALUE);

        PasswordField pfNewCode = new PasswordField();
        pfNewCode.setPromptText("Choose an access code");
        pfNewCode.getStyleClass().add("form-field");
        pfNewCode.setMaxWidth(Double.MAX_VALUE);

        PasswordField pfConfirm = new PasswordField();
        pfConfirm.setPromptText("Confirm access code");
        pfConfirm.getStyleClass().add("form-field");
        pfConfirm.setMaxWidth(Double.MAX_VALUE);

        Label createError = new Label();
        createError.getStyleClass().add("wizard-error");
        createError.setVisible(false);
        createError.setWrapText(true);

        Button btnCreate = new Button("Create Admin Account");
        btnCreate.getStyleClass().add("btn-primary");
        btnCreate.setMaxWidth(Double.MAX_VALUE);
        btnCreate.setOnAction(e -> {
            String uname = tfNewUsername.getText().trim();
            String code  = pfNewCode.getText();
            String conf  = pfConfirm.getText();

            if (uname.isBlank()) {
                createError.setText("Username cannot be empty.");
                createError.setVisible(true);
                return;
            }
            if (code.length() < 4) {
                createError.setText("Access code must be at least 4 characters.");
                createError.setVisible(true);
                return;
            }
            if (!code.equals(conf)) {
                createError.setText("Access codes do not match.");
                createError.setVisible(true);
                return;
            }

            try {
                userManager.createFirstAdmin(uname, code);
                UserAccount admin = userManager.authenticate(uname, code).orElseThrow();
                userManager.setCurrentUser(admin);
                loginSuccessful = true;
                close();
            } catch (IOException ex) {
                createError.setText("Could not create account: " + ex.getMessage());
                createError.setVisible(true);
            }
        });

        form.getChildren().addAll(
            formRow("Username", tfNewUsername),
            formRow("Access Code", pfNewCode),
            formRow("Confirm Access Code", pfConfirm),
            createError,
            btnCreate
        );

        // Footer
        VBox footer = new VBox(4);
        footer.getStyleClass().add("wizard-footer");
        footer.setPadding(new Insets(10, 36, 14, 36));
        footer.setAlignment(Pos.CENTER);
        Label copyright = new Label(App.COPYRIGHT);
        copyright.getStyleClass().add("copyright-label");
        footer.getChildren().add(copyright);

        root.setTop(header);
        root.setCenter(form);
        root.setBottom(footer);

        return new Scene(root, 400, 460);
    }

    // ── Login logic ───────────────────────────────────────────────────────────

    private void handleLogin() {
        String username = tfUsername.getText().trim();
        String code     = pfAccessCode.getText();

        if (username.isBlank() || code.isBlank()) {
            showError("Please enter your username and access code.");
            return;
        }

        btnLogin.setDisable(true);
        btnLogin.setText("Signing in...");

        // Run auth in background to avoid blocking UI
        Thread authThread = new Thread(() -> {
            try {
                java.util.Optional<UserAccount> user =
                        userManager.authenticate(username, code);

                Platform.runLater(() -> {
                    btnLogin.setDisable(false);
                    btnLogin.setText("Sign In");

                    if (user.isPresent()) {
                        userManager.setCurrentUser(user.get());
                        loginSuccessful = true;
                        close();
                    } else {
                        showError("Incorrect username or access code.\nPlease try again.");
                        pfAccessCode.clear();
                        pfAccessCode.requestFocus();
                    }
                });
            } catch (IOException e) {
                Platform.runLater(() -> {
                    btnLogin.setDisable(false);
                    btnLogin.setText("Sign In");
                    showError("Could not load user accounts: " + e.getMessage());
                });
            }
        }, "auth-thread");
        authThread.setDaemon(true);
        authThread.start();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
    }

    private VBox formRow(String labelText, javafx.scene.Node field) {
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("form-label");
        return new VBox(5, lbl, field);
    }
}
