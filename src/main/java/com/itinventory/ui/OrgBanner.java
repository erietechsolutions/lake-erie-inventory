package com.itinventory.ui;

import com.itinventory.util.OrgConfig;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * Horizontal banner displayed at the top of the main window,
 * showing organization name, type, IT contact, and a Settings button.
 */
public class OrgBanner extends HBox {

    private final Label lblOrgName = new Label();
    private final Label lblOrgType = new Label();
    private final Label lblAdmin   = new Label();
    private Label       lblUpdateBadge;

    public OrgBanner(OrgConfig config, Runnable onSettings, Runnable onCheckUpdates) {
        getStyleClass().add("org-banner");
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(10, 18, 10, 18));
        setSpacing(0);

        // Color accent strip on the left
        Rectangle strip = new Rectangle(4, 36);
        strip.getStyleClass().add("org-banner-strip");
        strip.setArcWidth(2);
        strip.setArcHeight(2);
        try { strip.setFill(Color.web(config.getAccentColor())); }
        catch (Exception e) { strip.setFill(Color.web("#4f7cff")); }

        // Org name and type block
        lblOrgName.getStyleClass().add("org-banner-name");
        lblOrgType.getStyleClass().add("org-banner-type");
        VBox nameBlock = new VBox(2, lblOrgName, lblOrgType);
        nameBlock.setAlignment(Pos.CENTER_LEFT);

        Region div1 = divider();

        // Admin block
        Label adminHeading = new Label("IT CONTACT");
        adminHeading.getStyleClass().add("org-banner-heading");
        lblAdmin.getStyleClass().add("org-banner-value");
        VBox adminBlock = new VBox(2, adminHeading, lblAdmin);
        adminBlock.setAlignment(Pos.CENTER_LEFT);

        // Update available badge (hidden by default)
        lblUpdateBadge = new Label("Update Available");
        lblUpdateBadge.getStyleClass().add("update-badge");
        lblUpdateBadge.setVisible(false);
        lblUpdateBadge.setManaged(false);
        lblUpdateBadge.setOnMouseClicked(e -> onCheckUpdates.run());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnUpdates = new Button("Check for Updates");
        btnUpdates.getStyleClass().add("btn-secondary");
        btnUpdates.setOnAction(e -> onCheckUpdates.run());

        Button btnSettings = new Button("  Settings");
        btnSettings.getStyleClass().add("btn-secondary");
        btnSettings.setOnAction(e -> onSettings.run());

        getChildren().addAll(strip, gap(14), nameBlock, div1, adminBlock,
                spacer, lblUpdateBadge, gap(10), btnUpdates, gap(8), btnSettings);

        refresh(config);
    }

    /** Re-reads the config and updates all labels. Call after settings are saved. */
    public void refresh(OrgConfig config) {
        lblOrgName.setText(config.getOrgName());
        lblOrgType.setText(config.getOrgType());
        lblAdmin.setText(config.getAdminName().isBlank() ? "-" : config.getAdminName());
    }

    /** Shows the "Update Available" badge in the banner. */
    public void showUpdateBadge(String version) {
        lblUpdateBadge.setText("Update Available: " + version + "  \u2192");
        lblUpdateBadge.setVisible(true);
        lblUpdateBadge.setManaged(true);
    }

    private Region divider() {
        Region div = new Region();
        div.getStyleClass().add("org-banner-divider");
        div.setPrefWidth(1);
        div.setPrefHeight(30);
        HBox.setMargin(div, new Insets(0, 18, 0, 18));
        return div;
    }

    private Region gap(double width) {
        Region r = new Region();
        r.setPrefWidth(width);
        r.setMinWidth(width);
        return r;
    }
}
