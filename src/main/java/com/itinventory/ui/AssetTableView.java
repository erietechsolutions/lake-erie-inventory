package com.itinventory.ui;

import com.itinventory.model.Asset;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Wraps a TableView<Asset> with all column definitions and row styling.
 * Category and Status columns use string values to support custom entries.
 */
public class AssetTableView {

    private final TableView<Asset> table;
    private Runnable onDoubleClick;

    @SuppressWarnings("unchecked")
    public AssetTableView(ObservableList<Asset> items) {
        table = new TableView<>(items);
        table.getStyleClass().add("asset-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No assets found."));

        // Asset ID
        TableColumn<Asset, String> colId = col("Asset ID", "assetId", 100);

        // Asset Tag
        TableColumn<Asset, String> colTag = new TableColumn<>("Asset Tag");
        colTag.setCellValueFactory(new PropertyValueFactory<>("assetTag"));
        colTag.setPrefWidth(120);
        colTag.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String tag, boolean empty) {
                super.updateItem(tag, empty);
                if (empty || tag == null || tag.isBlank()) {
                    setText(null); setGraphic(null); return;
                }
                Label lbl = new Label(tag.toUpperCase());
                lbl.getStyleClass().addAll("badge", "badge-tag");
                setGraphic(lbl); setText(null);
            }
        });

        // Name
        TableColumn<Asset, String> colName = col("Name", "name", 180);

        // Category — string-based, supports custom values
        TableColumn<Asset, String> colCat = new TableColumn<>("Category");
        colCat.setPrefWidth(130);
        colCat.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                Asset a = getTableRow() != null ? (Asset) getTableRow().getItem() : null;
                if (empty || a == null) { setText(null); setGraphic(null); return; }
                String catStr = a.getCategoryStr();
                if (catStr == null || catStr.isBlank()) { setText("-"); setGraphic(null); return; }
                Label lbl = new Label(catStr.replace('_', ' '));
                lbl.getStyleClass().addAll("badge", "badge-cat");
                setGraphic(lbl); setText(null);
            }
        });

        // Status — string-based, supports custom values
        TableColumn<Asset, String> colStatus = new TableColumn<>("Status");
        colStatus.setPrefWidth(120);
        colStatus.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                Asset a = getTableRow() != null ? (Asset) getTableRow().getItem() : null;
                if (empty || a == null) { setText(null); setGraphic(null); return; }
                String statStr = a.getStatusStr();
                if (statStr == null || statStr.isBlank()) { setText("-"); setGraphic(null); return; }
                Label lbl = new Label(statStr.replace('_', ' '));
                lbl.getStyleClass().addAll("badge", statusBadgeStyle(statStr));
                setGraphic(lbl); setText(null);
            }
        });

        TableColumn<Asset, String> colAssignee = col("Assigned To", "assignedTo", 130);
        TableColumn<Asset, String> colLocation  = col("Location",    "location",   110);
        TableColumn<Asset, String> colSerial    = col("Serial No.",  "serialNumber", 130);
        TableColumn<Asset, String> colMfr       = col("Manufacturer","manufacturer", 110);

        // Purchase Price
        TableColumn<Asset, Double> colPrice = new TableColumn<>("Purchase Price");
        colPrice.setCellValueFactory(new PropertyValueFactory<>("purchasePrice"));
        colPrice.setPrefWidth(120);
        colPrice.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                if (empty || price == null) { setText(null); return; }
                setText(price == 0.0 ? "-" : String.format("$%,.2f", price));
                setAlignment(Pos.CENTER_RIGHT);
            }
        });

        // Warranty Expiry
        TableColumn<Asset, LocalDate> colWarranty = new TableColumn<>("Warranty Expiry");
        colWarranty.setCellValueFactory(new PropertyValueFactory<>("warrantyExpiry"));
        colWarranty.setPrefWidth(120);
        colWarranty.setCellFactory(tc -> new TableCell<>() {
            private static final DateTimeFormatter FMT =
                    DateTimeFormatter.ofPattern("yyyy-MM-dd");
            @Override protected void updateItem(LocalDate d, boolean empty) {
                super.updateItem(d, empty);
                getStyleClass().removeAll("expired", "expiring-soon");
                if (empty || d == null) { setText("-"); return; }
                setText(d.format(FMT));
                Asset asset = getTableRow() != null
                        ? (Asset) getTableRow().getItem() : null;
                if (asset != null) {
                    if (asset.isWarrantyExpired())
                        getStyleClass().add("expired");
                    else if (asset.isWarrantyExpiringSoon(30))
                        getStyleClass().add("expiring-soon");
                }
            }
        });

        table.getColumns().addAll(colId, colTag, colName, colCat, colStatus,
                colAssignee, colLocation, colSerial, colMfr, colPrice, colWarranty);

        // Row double-click
        table.setRowFactory(tv -> {
            TableRow<Asset> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty() && onDoubleClick != null)
                    onDoubleClick.run();
            });
            return row;
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private TableColumn<Asset, String> col(String title, String property, double width) {
        TableColumn<Asset, String> c = new TableColumn<>(title);
        c.setCellValueFactory(new PropertyValueFactory<>(property));
        c.setPrefWidth(width);
        c.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                setText(empty || val == null || val.isBlank() ? "-" : val);
            }
        });
        return c;
    }

    /**
     * Maps a status string to a CSS badge class.
     * Built-in statuses get specific colors; custom statuses get a neutral color.
     */
    private String statusBadgeStyle(String status) {
        if (status == null) return "badge-inactive";
        return switch (status.toUpperCase()) {
            case "ACTIVE"    -> "badge-active";
            case "INACTIVE"  -> "badge-inactive";
            case "IN_REPAIR" -> "badge-repair";
            case "RETIRED"   -> "badge-retired";
            case "MISSING"   -> "badge-missing";
            default          -> "badge-cat"; // neutral blue for custom statuses
        };
    }

    public TableView<Asset> getTableView() { return table; }
    public Asset getSelected() { return table.getSelectionModel().getSelectedItem(); }
    public void setOnDoubleClick(Runnable r) { this.onDoubleClick = r; }
}
