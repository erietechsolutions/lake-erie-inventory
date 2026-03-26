package com.itinventory.ui;

import com.itinventory.util.OrgConfig;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.util.List;

/**
 * Lake Erie Inventory - First-Launch Setup Wizard.
 *
 * Page order:
 *   0 - License Agreement  (must accept to proceed; decline closes the app)
 *   1 - Welcome
 *   2 - Organization details
 *   3 - Branding color
 *   4 - Done / summary
 */
public class SetupWizard extends Stage {

    // ── Constants ─────────────────────────────────────────────────────────────

    public static final List<String> ORG_TYPES = List.of(
            "Company", "School", "Non-Profit", "Library", "Government", "Other"
    );

    private static final String[][] PRESETS = {
            { "Ocean Blue",  "#4f7cff" },
            { "Emerald",     "#10b981" },
            { "Violet",      "#8b5cf6" },
            { "Rose",        "#f43f5e" },
            { "Amber",       "#f59e0b" },
            { "Slate",       "#64748b" },
    };

    private static final int TOTAL_PAGES = 5;

    // Full license text displayed on page 0
    private static final String LICENSE_TEXT =
        "SOFTWARE USE LICENSE\n" +
        "Lake Erie Inventory - IT Asset Inventory Management System\n" +
        "Copyright (c) " + java.time.Year.now().getValue() + " Lake Erie Technical Solutions LLC\n" +
        "Version 1.0\n\n" +
        "IMPORTANT - READ CAREFULLY BEFORE USING THIS SOFTWARE\n\n" +
        "This Software Use License (\"License\") is a legal agreement between you, the end user " +
        "(\"User\" or \"You\"), and Lake Erie Technical Solutions LLC (\"Licensor\"), a limited " +
        "liability company organized under the laws of the State of Michigan, United States of " +
        "America, governing your use of the Lake Erie Inventory software and any associated " +
        "documentation files (collectively, the \"Software\").\n\n" +
        "By clicking \"I Accept\" below, you agree to be bound by the terms of this License. " +
        "If you do not agree, click \"Decline\" and the application will close.\n\n" +
        "-----------------------------------------------------------------------\n" +
        "SECTION 1 - GRANT OF LICENSE\n" +
        "-----------------------------------------------------------------------\n\n" +
        "Subject to the terms of this License, Licensor grants You a limited, non-exclusive, " +
        "non-transferable, revocable license to: (a) download, install, and use the Software " +
        "solely for personal, educational, or internal organizational purposes; (b) view and " +
        "study the source code for personal learning and non-commercial reference; (c) modify " +
        "the Software for your own internal, non-commercial use, provided modifications are not " +
        "distributed without prior written authorization from Licensor.\n\n" +
        "-----------------------------------------------------------------------\n" +
        "SECTION 2 - RESTRICTIONS\n" +
        "-----------------------------------------------------------------------\n\n" +
        "Without prior express written authorization from Lake Erie Technical Solutions LLC, " +
        "You may NOT:\n\n" +
        "2.1 REDISTRIBUTION\n" +
        "Distribute, sublicense, sell, lease, rent, loan, transfer, or otherwise make the " +
        "Software available to any third party; publish or upload the Software to any public " +
        "or private repository without prior written authorization; bundle the Software into " +
        "another product without prior written authorization.\n\n" +
        "2.2 COMMERCIAL USE AND MONETIZATION\n" +
        "Use the Software for any commercial purpose; accept compensation in exchange for " +
        "access to or services derived from the Software; generate advertising revenue using " +
        "the Software.\n\n" +
        "2.3 ATTRIBUTION AND MISREPRESENTATION\n" +
        "Remove, alter, or obscure any copyright notice or attribution; represent yourself as " +
        "the original author; use the name or trademarks of Lake Erie Technical Solutions LLC " +
        "without prior written permission.\n\n" +
        "2.4 OTHER PROHIBITED ACTS\n" +
        "Reverse engineer any compiled portions of the Software except as permitted by law; " +
        "use the Software in violation of applicable law; use the Software in the State of " +
        "California or the State of Colorado (see Section 6).\n\n" +
        "-----------------------------------------------------------------------\n" +
        "SECTION 3 - ATTRIBUTION REQUIREMENTS\n" +
        "-----------------------------------------------------------------------\n\n" +
        "Any authorized redistribution must: retain this License in full; include the notice " +
        "\"This software is based on Lake Erie Inventory, originally developed by Lake Erie " +
        "Technical Solutions LLC. Used with permission.\"; reference the original repository; " +
        "and clearly describe any modifications made.\n\n" +
        "-----------------------------------------------------------------------\n" +
        "SECTION 4 - AUTHORIZATION REQUESTS\n" +
        "-----------------------------------------------------------------------\n\n" +
        "Requests for authorization to redistribute or commercially use the Software must be " +
        "submitted in writing to Lake Erie Technical Solutions LLC prior to such use. " +
        "Authorization requires a signed written agreement. Verbal or informal approvals do " +
        "not constitute authorization.\n\n" +
        "-----------------------------------------------------------------------\n" +
        "SECTION 5 - INTELLECTUAL PROPERTY\n" +
        "-----------------------------------------------------------------------\n\n" +
        "The Software and all associated intellectual property is and shall remain the " +
        "exclusive property of Lake Erie Technical Solutions LLC. All rights not expressly " +
        "granted herein are reserved.\n\n" +
        "-----------------------------------------------------------------------\n" +
        "SECTION 6 - GEOGRAPHIC RESTRICTIONS\n" +
        "-----------------------------------------------------------------------\n\n" +
        "USE OF THIS SOFTWARE IS EXPRESSLY PROHIBITED IN THE STATE OF CALIFORNIA AND THE " +
        "STATE OF COLORADO. If You are located in or subject to the laws of California or " +
        "Colorado, You are not permitted to download, install, access, or use this Software " +
        "in any manner. This restriction applies due to the complex regulatory environments " +
        "in those states, including but not limited to the CCPA, CPRA, and CPA.\n\n" +
        "-----------------------------------------------------------------------\n" +
        "SECTION 7 - FEDERAL LAW COMPLIANCE\n" +
        "-----------------------------------------------------------------------\n\n" +
        "This Software is subject to applicable federal laws including the Computer Fraud and " +
        "Abuse Act (18 U.S.C. Section 1030), the Digital Millennium Copyright Act (17 U.S.C. " +
        "Section 512), the Electronic Communications Privacy Act (18 U.S.C. Section 2510), " +
        "and Title 17 of the United States Code.\n\n" +
        "-----------------------------------------------------------------------\n" +
        "SECTION 8 - DISCLAIMER OF WARRANTIES\n" +
        "-----------------------------------------------------------------------\n\n" +
        "THE SOFTWARE IS PROVIDED \"AS IS,\" WITHOUT WARRANTY OF ANY KIND, EXPRESS OR " +
        "IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS " +
        "FOR A PARTICULAR PURPOSE, AND NON-INFRINGEMENT.\n\n" +
        "-----------------------------------------------------------------------\n" +
        "SECTION 9 - LIMITATION OF LIABILITY\n" +
        "-----------------------------------------------------------------------\n\n" +
        "TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE LAW, LAKE ERIE TECHNICAL SOLUTIONS " +
        "LLC SHALL NOT BE LIABLE FOR ANY INDIRECT, INCIDENTAL, SPECIAL, CONSEQUENTIAL, OR " +
        "PUNITIVE DAMAGES ARISING OUT OF OR IN CONNECTION WITH THIS LICENSE OR THE USE OF " +
        "THE SOFTWARE. LICENSOR'S TOTAL CUMULATIVE LIABILITY SHALL NOT EXCEED ZERO U.S. " +
        "DOLLARS ($0.00), AS THE SOFTWARE IS PROVIDED AT NO CHARGE.\n\n" +
        "-----------------------------------------------------------------------\n" +
        "SECTION 10 - TERMINATION\n" +
        "-----------------------------------------------------------------------\n\n" +
        "This License terminates automatically if You fail to comply with any term. Upon " +
        "termination You must cease all use and destroy all copies of the Software.\n\n" +
        "-----------------------------------------------------------------------\n" +
        "SECTION 11 - GOVERNING LAW\n" +
        "-----------------------------------------------------------------------\n\n" +
        "This License is governed by the laws of the State of Michigan. Any dispute shall " +
        "be subject to the exclusive jurisdiction of the state and federal courts of Michigan. " +
        "Both parties agree to attempt good-faith negotiation for 30 days before initiating " +
        "formal legal proceedings.\n\n" +
        "-----------------------------------------------------------------------\n" +
        "SECTION 12 - GENERAL PROVISIONS\n" +
        "-----------------------------------------------------------------------\n\n" +
        "This License constitutes the entire agreement between You and Lake Erie Technical " +
        "Solutions LLC regarding the Software. If any provision is held unenforceable, the " +
        "remaining provisions continue in full force. Licensor reserves the right to modify " +
        "this License at any time; continued use constitutes acceptance of any updates.\n\n" +
        "For authorization requests or licensing inquiries, contact Lake Erie Technical " +
        "Solutions LLC through the official GitHub repository.\n\n" +
        "-----------------------------------------------------------------------\n\n" +
        "Copyright (c) " + java.time.Year.now().getValue() + " Lake Erie Technical Solutions LLC. " +
        "All Rights Reserved.";

    // ── Fields ────────────────────────────────────────────────────────────────

    private final OrgConfig config;
    private boolean completed    = false;
    private boolean licenseDeclined = false;

    // Form controls
    private final TextField        tfOrgName     = styledField("e.g. Acme Corporation");
    private final ComboBox<String> cbOrgType     = new ComboBox<>();
    private final TextField        tfAdmin       = styledField("e.g. Jane Smith");
    private final ToggleGroup      colorGroup    = new ToggleGroup();
    private final TextField        tfCustomColor = styledField("#4f7cff");
    private CheckBox               cbAccept;

    // Wizard navigation
    private int currentPage = 0;
    private final StackPane pageContainer = new StackPane();
    private final HBox      dotIndicator  = new HBox(8);
    private final Button    btnBack       = new Button("Back");
    private final Button    btnNext       = new Button("Next");
    private final Label     errorLabel    = new Label();

    // ── Constructor ───────────────────────────────────────────────────────────

    public SetupWizard(OrgConfig config) {
        this.config = config;

        initStyle(StageStyle.UNDECORATED);
        initModality(Modality.APPLICATION_MODAL);
        setResizable(false);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("wizard-root");
        root.setTop(buildHeader());
        root.setCenter(buildBody());
        root.setBottom(buildFooter());

        Scene scene = new Scene(root, 600, 540);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        setScene(scene);

        // Skip license page if already accepted (e.g. re-running wizard from Settings)
        showPage(config.isLicenseAccepted() ? 1 : 0);
    }

    public boolean isCompleted()      { return completed; }
    public boolean isLicenseDeclined(){ return licenseDeclined; }

    // ── Layout ────────────────────────────────────────────────────────────────

    private VBox buildHeader() {
        VBox header = new VBox(4);
        header.getStyleClass().add("wizard-header");
        header.setPadding(new Insets(22, 32, 18, 32));

        Label title = new Label("Lake Erie Inventory");
        title.getStyleClass().add("wizard-title");
        Label subtitle = new Label("Setup Wizard");
        subtitle.getStyleClass().add("wizard-subtitle");

        header.getChildren().addAll(title, subtitle);
        return header;
    }

    private StackPane buildBody() {
        StackPane body = new StackPane(pageContainer);
        body.getStyleClass().add("wizard-body");
        body.setPadding(new Insets(0, 32, 0, 32));
        VBox.setVgrow(body, Priority.ALWAYS);
        return body;
    }

    private VBox buildFooter() {
        VBox footer = new VBox(6);
        footer.getStyleClass().add("wizard-footer");
        footer.setPadding(new Insets(14, 32, 18, 32));

        // Dot indicators - one per page
        dotIndicator.setAlignment(Pos.CENTER);
        for (int i = 0; i < TOTAL_PAGES; i++) {
            Circle dot = new Circle(4);
            dot.getStyleClass().add("wizard-dot");
            dotIndicator.getChildren().add(dot);
        }

        errorLabel.getStyleClass().add("wizard-error");
        errorLabel.setVisible(false);
        errorLabel.setMaxWidth(Double.MAX_VALUE);

        btnBack.getStyleClass().add("btn-secondary");
        btnNext.getStyleClass().add("btn-primary");
        btnNext.setPrefWidth(120);

        btnBack.setOnAction(e -> navigate(-1));
        btnNext.setOnAction(e -> navigate(1));

        HBox navRow = new HBox(10);
        navRow.setAlignment(Pos.CENTER_RIGHT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        navRow.getChildren().addAll(dotIndicator, spacer, btnBack, btnNext);

        Label copyright = new Label(App.COPYRIGHT);
        copyright.getStyleClass().add("copyright-label");
        copyright.setMaxWidth(Double.MAX_VALUE);
        copyright.setAlignment(Pos.CENTER);

        footer.getChildren().addAll(errorLabel, navRow, copyright);
        return footer;
    }

    // ── Pages ─────────────────────────────────────────────────────────────────

    /** Page 0 - License Agreement */
    private VBox buildPage0_License() {
        VBox page = new VBox(12);
        page.setPadding(new Insets(10, 0, 0, 0));

        Label heading = new Label("License Agreement");
        heading.getStyleClass().add("wizard-page-heading");

        Label instruction = new Label(
            "Please read the entire license agreement below before proceeding.\n" +
            "You must accept the agreement to use Lake Erie Inventory.");
        instruction.getStyleClass().add("wizard-page-body");
        instruction.setWrapText(true);

        // Scrollable license text area
        TextArea licenseArea = new TextArea(LICENSE_TEXT);
        licenseArea.setEditable(false);
        licenseArea.setWrapText(true);
        licenseArea.getStyleClass().add("license-text-area");
        licenseArea.setPrefRowCount(14);
        VBox.setVgrow(licenseArea, Priority.ALWAYS);

        // Accept checkbox
        cbAccept = new CheckBox("I have read and agree to the terms of this License Agreement");
        cbAccept.getStyleClass().add("settings-checkbox");
        cbAccept.setWrapText(true);
        // Enable/disable Next button based on checkbox
        cbAccept.selectedProperty().addListener((obs, o, n) -> {
            btnNext.setDisable(!n);
            errorLabel.setVisible(false);
        });

        // Decline button
        Button btnDecline = new Button("Decline and Close");
        btnDecline.getStyleClass().add("btn-danger");
        btnDecline.setOnAction(e -> {
            licenseDeclined = true;
            close();
            Platform.exit();
        });

        HBox acceptRow = new HBox(12, cbAccept, btnDecline);
        acceptRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(cbAccept, Priority.ALWAYS);

        page.getChildren().addAll(heading, instruction, licenseArea, acceptRow);
        return page;
    }

    /** Page 1 - Welcome */
    private VBox buildPage1_Welcome() {
        VBox page = new VBox(18);
        page.setAlignment(Pos.CENTER);
        page.setPadding(new Insets(20, 0, 0, 0));

        Label icon = new Label("\uD83C\uDFE2");
        icon.setStyle("-fx-font-size: 52px;");

        Label heading = new Label("Welcome!");
        heading.getStyleClass().add("wizard-page-heading");

        Label body = new Label(
            "Thank you for accepting the License Agreement.\n\n" +
            "This short setup wizard will personalize Lake Erie Inventory\n" +
            "for your organization.\n\n" +
            "It only takes about a minute and you can always\n" +
            "change these settings later from the Settings screen.");
        body.getStyleClass().add("wizard-page-body");
        body.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        body.setAlignment(Pos.CENTER);

        page.getChildren().addAll(icon, heading, body);
        return page;
    }

    /** Page 2 - Organization Details */
    private VBox buildPage2_OrgDetails() {
        VBox page = new VBox(14);
        page.setPadding(new Insets(10, 0, 0, 0));

        Label heading = new Label("Organization Details");
        heading.getStyleClass().add("wizard-page-heading");
        heading.setPadding(new Insets(0, 0, 6, 0));

        cbOrgType.getItems().addAll(ORG_TYPES);
        cbOrgType.setPromptText("Select type...");
        cbOrgType.setMaxWidth(Double.MAX_VALUE);
        cbOrgType.getStyleClass().add("filter-combo");
        if (!config.getOrgType().isBlank()) cbOrgType.setValue(config.getOrgType());

        if (!config.getOrgName().isBlank())   tfOrgName.setText(config.getOrgName());
        if (!config.getAdminName().isBlank()) tfAdmin.setText(config.getAdminName());

        page.getChildren().addAll(
            heading,
            formRow("Organization Name *", tfOrgName),
            formRow("Organization Type *", cbOrgType),
            formRow("IT Contact / Admin Name", tfAdmin)
        );
        return page;
    }

    /** Page 3 - Branding Color */
    private VBox buildPage3_Color() {
        VBox page = new VBox(14);
        page.setPadding(new Insets(10, 0, 0, 0));

        Label heading = new Label("Branding Color");
        heading.getStyleClass().add("wizard-page-heading");

        Label sub = new Label(
            "Choose an accent color for the app. " +
            "This affects buttons, highlights, and badges.");
        sub.getStyleClass().add("wizard-page-body");
        sub.setWrapText(true);

        GridPane swatches = new GridPane();
        swatches.setHgap(10);
        swatches.setVgap(10);
        swatches.setPadding(new Insets(4, 0, 4, 0));

        String savedColor = config.getAccentColor();
        for (int i = 0; i < PRESETS.length; i++) {
            String label = PRESETS[i][0];
            String hex   = PRESETS[i][1];

            RadioButton rb = new RadioButton(label);
            rb.setToggleGroup(colorGroup);
            rb.setUserData(hex);
            rb.getStyleClass().add("color-radio");

            Rectangle swatch = new Rectangle(14, 14);
            swatch.setArcWidth(4);
            swatch.setArcHeight(4);
            try   { swatch.setFill(Color.web(hex)); }
            catch (Exception ex) { swatch.setFill(Color.GRAY); }

            HBox cell = new HBox(8, swatch, rb);
            cell.setAlignment(Pos.CENTER_LEFT);
            swatches.add(cell, i % 3, i / 3);

            if (hex.equalsIgnoreCase(savedColor)) rb.setSelected(true);
        }

        Label customLabel = new Label("Custom hex color:");
        customLabel.getStyleClass().add("form-label");
        tfCustomColor.setText(savedColor);
        tfCustomColor.setPrefWidth(130);
        tfCustomColor.setMaxWidth(130);

        colorGroup.selectedToggleProperty().addListener((obs, o, n) -> {
            if (n != null) tfCustomColor.setText((String) n.getUserData());
        });

        HBox customRow = new HBox(10, customLabel, tfCustomColor);
        customRow.setAlignment(Pos.CENTER_LEFT);
        customRow.setPadding(new Insets(4, 0, 0, 0));

        page.getChildren().addAll(heading, sub, swatches, customRow);
        return page;
    }

    /** Page 4 - Done / Summary */
    private VBox buildPage4_Done() {
        VBox page = new VBox(14);
        page.setAlignment(Pos.CENTER);
        page.setPadding(new Insets(20, 0, 0, 0));

        Label icon = new Label("\u2705");
        icon.setStyle("-fx-font-size: 52px;");

        Label heading = new Label("All set!");
        heading.getStyleClass().add("wizard-page-heading");

        VBox summary = new VBox(8);
        summary.getStyleClass().add("wizard-summary");
        summary.setPadding(new Insets(14, 18, 14, 18));
        summary.setMaxWidth(340);

        summary.getChildren().addAll(
            summaryRow("Organization", config.getOrgName()),
            summaryRow("Type",         config.getOrgType()),
            summaryRow("IT Contact",   config.getAdminName().isBlank() ? "-" : config.getAdminName()),
            summaryRow("Accent color", config.getAccentColor()),
            summaryRow("License",      "Accepted")
        );

        Label note = new Label("You can update organization settings anytime from the Settings screen.");
        note.getStyleClass().add("wizard-page-body");
        note.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        note.setAlignment(Pos.CENTER);

        page.getChildren().addAll(icon, heading, summary, note);
        return page;
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void showPage(int page) {
        currentPage = page;
        pageContainer.getChildren().clear();

        VBox content = switch (page) {
            case 0  -> buildPage0_License();
            case 1  -> buildPage1_Welcome();
            case 2  -> buildPage2_OrgDetails();
            case 3  -> buildPage3_Color();
            case 4  -> buildPage4_Done();
            default -> new VBox();
        };
        pageContainer.getChildren().add(content);

        // Update dot indicators
        for (int i = 0; i < dotIndicator.getChildren().size(); i++) {
            Circle dot = (Circle) dotIndicator.getChildren().get(i);
            dot.getStyleClass().removeAll("wizard-dot-active");
            if (i == page) dot.getStyleClass().add("wizard-dot-active");
        }

        // On license page: Next is disabled until checkbox is ticked
        if (page == 0) {
            btnNext.setDisable(cbAccept == null || !cbAccept.isSelected());
        } else {
            btnNext.setDisable(false);
        }

        btnBack.setVisible(page > 1); // no Back on license or welcome pages
        btnNext.setText(page == 4 ? "Launch App" : page == 3 ? "Finish" : "Next");
        errorLabel.setVisible(false);
    }

    private void navigate(int direction) {
        if (direction == 1) {
            // Validate acceptance on license page
            if (currentPage == 0) {
                if (cbAccept == null || !cbAccept.isSelected()) {
                    errorLabel.setText("You must accept the License Agreement to continue.");
                    errorLabel.setVisible(true);
                    return;
                }
                // Save license acceptance
                config.setLicenseAccepted(true);
                try { config.save(); }
                catch (IOException e) {
                    errorLabel.setText("Could not save acceptance: " + e.getMessage());
                    errorLabel.setVisible(true);
                    return;
                }
                showPage(1);
                return;
            }

            String err = validateCurrentPage();
            if (err != null) {
                errorLabel.setText(err);
                errorLabel.setVisible(true);
                return;
            }
            saveCurrentPage();

            if (currentPage == 3) { showPage(4); return; }

            if (currentPage == 4) {
                try {
                    config.markConfigured();
                    config.save();
                    completed = true;
                    close();
                } catch (IOException e) {
                    errorLabel.setText("Could not save config: " + e.getMessage());
                    errorLabel.setVisible(true);
                }
                return;
            }
        }
        showPage(currentPage + direction);
    }

    private String validateCurrentPage() {
        return switch (currentPage) {
            case 2 -> {
                if (tfOrgName.getText().trim().isBlank()) yield "Organization name is required.";
                if (cbOrgType.getValue() == null)         yield "Please select an organization type.";
                yield null;
            }
            case 3 -> {
                String hex = tfCustomColor.getText().trim();
                if (!hex.matches("^#([0-9A-Fa-f]{6})$"))
                    yield "Please enter a valid hex color (e.g. #4f7cff).";
                yield null;
            }
            default -> null;
        };
    }

    private void saveCurrentPage() {
        switch (currentPage) {
            case 2 -> {
                config.setOrgName(tfOrgName.getText().trim());
                config.setOrgType(cbOrgType.getValue());
                config.setAdminName(tfAdmin.getText().trim());
            }
            case 3 -> config.setAccentColor(tfCustomColor.getText().trim());
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private VBox formRow(String labelText, javafx.scene.Node field) {
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("form-label");
        return new VBox(5, lbl, field);
    }

    private HBox summaryRow(String key, String value) {
        Label k = new Label(key + ":");
        k.getStyleClass().add("summary-key");
        k.setPrefWidth(120);
        Label v = new Label(value);
        v.getStyleClass().add("summary-value");
        HBox row = new HBox(10, k, v);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static TextField styledField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.getStyleClass().add("form-field");
        tf.setMaxWidth(Double.MAX_VALUE);
        return tf;
    }
}
