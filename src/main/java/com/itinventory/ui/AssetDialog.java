package com.itinventory.ui;

import com.itinventory.model.Asset;
import com.itinventory.model.Asset.Category;
import com.itinventory.model.Asset.Status;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import com.itinventory.service.InventoryService;


/**
 * Modal dialog for adding or editing an asset.
 * Pass {@code existing = null} for an add, or an Asset instance for an edit.
 */
public class AssetDialog extends Dialog<Asset> {

    private final Label tagWarning = new Label();

    private final boolean isEdit;

    // Form fields
    private final TextField       tfName         = field();
    private final ComboBox<Category> cbCategory  = new ComboBox<>();
    private final TextField       tfManufacturer = field();
    private final TextField       tfModel        = field();
    private final TextField       tfSerial       = field();
    private final TextField tfAssetTag = field("e.g. 0000");
    private final ComboBox<Status> cbStatus      = new ComboBox<>();
    private final TextField       tfAssignedTo   = field();
    private final TextField       tfLocation     = field();
    private final TextField       tfPurchaseDate = field("YYYY-MM-DD");
    private final TextField       tfPrice        = field("0.00");
    private final TextField       tfWarranty     = field("YYYY-MM-DD");
    private final TextArea        taNotes        = new TextArea();

    public AssetDialog(Stage owner, Asset existing, InventoryService service) {
        this.isEdit = (existing != null);
        String existingId = existing != null ? existing.getAssetId() : "";

        setTitle(isEdit ? "Edit Asset – " + existing.getAssetId() : "Add New Asset");
        initOwner(owner);
        getDialogPane().getStylesheets().add(
                getClass().getResource("/styles.css").toExternalForm());
        getDialogPane().getStyleClass().add("dialog-pane");

        // Enum combos
        cbCategory.getItems().addAll(Category.values());
        cbCategory.setMaxWidth(Double.MAX_VALUE);
        cbStatus.getItems().addAll(Status.values());
        cbStatus.setMaxWidth(Double.MAX_VALUE);

        // Notes area
        taNotes.setPrefRowCount(3);
        taNotes.setWrapText(true);

        // Populate for edit
        if (isEdit) populate(existing);
        else        cbStatus.setValue(Status.ACTIVE);

        // Layout
        getDialogPane().setContent(buildForm());
        getDialogPane().setPrefWidth(540);

        // Buttons
        ButtonType saveBtn = new ButtonType(isEdit ? "Save Changes" : "Add Asset", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        // Style the OK button
        Button okButton = (Button) getDialogPane().lookupButton(saveBtn);
        okButton.getStyleClass().add("btn-primary");

        // Result converter
        setResultConverter(btnType -> {
            if (btnType == saveBtn) return buildAsset(isEdit ? existing : null);
            return null;
        
});

        tfAssetTag.textProperty().addListener((obs, oldVal, newVal) -> {
    String tag = newVal.trim();
    if (tag.isBlank()) { tagWarning.setVisible(false); return; }
    boolean taken = service.findByTag(tag)
            .filter(a -> !a.getAssetId().equals(existingId))
            .isPresent();
    tagWarning.setText("⚠  Tag '" + tag.toUpperCase() + "' is already in use");
    tagWarning.setVisible(taken);
});



        
    }

    // ── Form layout ───────────────────────────────────────────────────────────

    private GridPane buildForm() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("dialog-grid");
        grid.setHgap(14);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 24, 10, 24));

        ColumnConstraints labelCol = new ColumnConstraints(110);
        ColumnConstraints fieldCol = new ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);
        ColumnConstraints labelCol2 = new ColumnConstraints(110);
        ColumnConstraints fieldCol2 = new ColumnConstraints();
        fieldCol2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelCol, fieldCol, labelCol2, fieldCol2);

        int row = 0;

        // Row 0 – name (spans 3 cols)
        grid.add(label("Name *"),    0, row);
        GridPane.setColumnSpan(tfName, 3);
        grid.add(tfName,             1, row++);

        // Row 1 – asset tag | warning label (right side, same row)
        grid.add(label("Asset Tag"),  0, row);
        grid.add(tfAssetTag,          1, row);

        tagWarning.getStyleClass().add("tag-warning");
        tagWarning.setVisible(false);
        tagWarning.setWrapText(true);
        GridPane.setColumnSpan(tagWarning, 2);
        grid.add(tagWarning, 2, row++);

        // Row 2 – serial number (own row now that warning shares row 1)
        grid.add(label("Serial No."), 0, row);
        GridPane.setColumnSpan(tfSerial, 3);
        grid.add(tfSerial,            1, row++);

        
        

        // Row 2 – category | manufacturer
        grid.add(label("Category *"),    0, row);
        grid.add(cbCategory,             1, row);
        grid.add(label("Manufacturer"),  2, row);
        grid.add(tfManufacturer,         3, row++);

        // Row 3 – model | serial
        grid.add(label("Model"),     0, row);
        grid.add(tfModel,            1, row);

        // Row 4 – status | assignee
        grid.add(label("Status *"),  0, row);
        grid.add(cbStatus,           1, row);
        grid.add(label("Assigned To"), 2, row);
        grid.add(tfAssignedTo,       3, row++);

        // Row 5 – location | price
        grid.add(label("Location"),  0, row);
        grid.add(tfLocation,         1, row);
        grid.add(label("Price ($)"), 2, row);
        grid.add(tfPrice,            3, row++);

        // Row 6 – purchase date | warranty
        grid.add(label("Purchase Date"), 0, row);
        grid.add(tfPurchaseDate,         1, row);
        grid.add(label("Warranty Exp."), 2, row);
        grid.add(tfWarranty,             3, row++);

        // Row 7 – notes (full width)
        grid.add(label("Notes"),     0, row);
        GridPane.setColumnSpan(taNotes, 3);
        grid.add(taNotes,            1, row++);

        // Make text fields fill width
        for (var node : grid.getChildren()) {
            if (node instanceof TextField tf) {
                tf.setMaxWidth(Double.MAX_VALUE);
                GridPane.setHgrow(tf, Priority.ALWAYS);
            }
        }

        return grid;
    }

    // ── Populate / build ──────────────────────────────────────────────────────

    private void populate(Asset a) {
        tfName.setText(a.getName());
        cbCategory.setValue(a.getCategory());
        tfManufacturer.setText(a.getManufacturer());
        tfModel.setText(a.getModel());
        tfSerial.setText(a.getSerialNumber());
        tfAssetTag.setText(a.getAssetTag() != null ? a.getAssetTag() : "");
        cbStatus.setValue(a.getStatus());
        tfAssignedTo.setText(a.getAssignedTo());
        tfLocation.setText(a.getLocation());
        tfPurchaseDate.setText(a.getPurchaseDate()   != null ? a.getPurchaseDate().toString()   : "");
        tfPrice.setText(a.getPurchasePrice() > 0     ? String.valueOf(a.getPurchasePrice())      : "");
        tfWarranty.setText(a.getWarrantyExpiry()     != null ? a.getWarrantyExpiry().toString()  : "");
        taNotes.setText(a.getNotes());
    }

    private Asset buildAsset(Asset base) {
        Asset a = base != null ? base : new Asset();
        a.setName(tfName.getText().trim());
        a.setCategory(cbCategory.getValue());
        a.setManufacturer(tfManufacturer.getText().trim());
        a.setModel(tfModel.getText().trim());
        a.setSerialNumber(tfSerial.getText().trim());
        a.setAssetTag(tfAssetTag.getText().trim().toUpperCase());
        a.setStatus(cbStatus.getValue());
        a.setAssignedTo(tfAssignedTo.getText().trim());
        a.setLocation(tfLocation.getText().trim());
        a.setPurchaseDate(parseDate(tfPurchaseDate.getText()));
        a.setPurchasePrice(parseDouble(tfPrice.getText()));
        a.setWarrantyExpiry(parseDate(tfWarranty.getText()));
        a.setNotes(taNotes.getText().trim());
        return a;
    }

    // ── Factories / helpers ───────────────────────────────────────────────────

    private Label label(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("form-label");
        l.setAlignment(Pos.CENTER_RIGHT);
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    private TextField field() {
        TextField tf = new TextField();
        tf.getStyleClass().add("form-field");
        return tf;
    }

    private TextField field(String prompt) {
        TextField tf = field();
        tf.setPromptText(prompt);
        return tf;
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s.trim()); }
        catch (DateTimeParseException e) { return null; }
    }

    private double parseDouble(String s) {
        if (s == null || s.isBlank()) return 0.0;
        try { return Double.parseDouble(s.trim()); }
        catch (NumberFormatException e) { return 0.0; }
    }
}
