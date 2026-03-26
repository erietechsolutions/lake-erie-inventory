package com.itinventory.ui;

import com.itinventory.util.ChangeLogger;
import com.itinventory.util.ChangeLogger.Action;
import com.itinventory.util.ChangeLogger.LogEntry;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Admin-only Change Log viewer.
 * Shows all system actions with filtering by user, action type, and date range.
 */
public class ChangeLogView extends Stage {

    private final ChangeLogger logger;
    private final Stage        owner;

    private final ObservableList<LogEntry>  masterList   = FXCollections.observableArrayList();
    private final FilteredList<LogEntry>    filteredList = new FilteredList<>(masterList, e -> true);

    // Filter controls
    private TextField     tfSearch;
    private ComboBox<String>  cbUser;
    private ComboBox<String>  cbAction;
    private DatePicker    dpFrom;
    private DatePicker    dpTo;
    private Label         statusLabel;

    // ── Constructor ───────────────────────────────────────────────────────────

    public ChangeLogView(Stage owner, ChangeLogger logger) {
        this.owner  = owner;
        this.logger = logger;

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Change Log");
        setResizable(true);
        setMinWidth(900);
        setMinHeight(560);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("wizard-root");
        root.setTop(buildHeader());
        root.setCenter(buildBody());
        root.setBottom(buildFooter());

        Scene scene = new Scene(root, 1060, 620);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        setScene(scene);

        loadEntries();
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private VBox buildHeader() {
        VBox header = new VBox(4);
        header.getStyleClass().add("wizard-header");
        header.setPadding(new Insets(18, 24, 14, 24));

        Label title = new Label("System Change Log");
        title.getStyleClass().add("wizard-title");
        Label sub = new Label("All system actions recorded by Lake Erie Inventory. Admin view only.");
        sub.getStyleClass().add("wizard-subtitle");
        header.getChildren().addAll(title, sub);
        return header;
    }

    // ── Body ──────────────────────────────────────────────────────────────────

    private VBox buildBody() {
        VBox body = new VBox(10);
        body.setPadding(new Insets(12, 24, 8, 24));
        VBox.setVgrow(body, Priority.ALWAYS);

        body.getChildren().addAll(buildFilterBar(), buildTable());
        return body;
    }

    private HBox buildFilterBar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 4, 0));

        tfSearch = new TextField();
        tfSearch.setPromptText("Search detail, target...");
        tfSearch.getStyleClass().add("search-field");
        tfSearch.setPrefWidth(200);
        tfSearch.textProperty().addListener((obs, o, n) -> applyFilters());

        cbUser = new ComboBox<>();
        cbUser.setPromptText("All Users");
        cbUser.getStyleClass().add("filter-combo");
        cbUser.setPrefWidth(130);
        cbUser.setOnAction(e -> applyFilters());

        cbAction = new ComboBox<>();
        cbAction.setPromptText("All Actions");
        cbAction.getStyleClass().add("filter-combo");
        cbAction.setPrefWidth(180);
        cbAction.setOnAction(e -> applyFilters());

        dpFrom = new DatePicker();
        dpFrom.setPromptText("From date");
        dpFrom.setPrefWidth(130);
        dpFrom.setOnAction(e -> applyFilters());

        dpTo = new DatePicker();
        dpTo.setPromptText("To date");
        dpTo.setPrefWidth(130);
        dpTo.setOnAction(e -> applyFilters());

        Button btnClear = new Button("Clear Filters");
        btnClear.getStyleClass().add("btn-secondary");
        btnClear.setOnAction(e -> clearFilters());

        Button btnRefresh = new Button("Refresh");
        btnRefresh.getStyleClass().add("btn-secondary");
        btnRefresh.setOnAction(e -> loadEntries());

        bar.getChildren().addAll(
            tfSearch, cbUser, cbAction, dpFrom, dpTo, btnClear, btnRefresh);
        return bar;
    }

    @SuppressWarnings("unchecked")
    private TableView<LogEntry> buildTable() {
        TableView<LogEntry> table = new TableView<>(filteredList);
        table.getStyleClass().add("asset-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No log entries found."));
        VBox.setVgrow(table, Priority.ALWAYS);

        // Timestamp
        TableColumn<LogEntry, String> colTime = new TableColumn<>("Timestamp");
        colTime.setPrefWidth(150);
        colTime.setCellFactory(tc -> new TableCell<>() {
            private static final DateTimeFormatter FMT =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                LogEntry e = getTableRow() != null ? (LogEntry) getTableRow().getItem() : null;
                setText(empty || e == null ? null : e.timestamp().format(FMT));
            }
        });

        // Username
        TableColumn<LogEntry, String> colUser = new TableColumn<>("User");
        colUser.setPrefWidth(110);
        colUser.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                LogEntry e = getTableRow() != null ? (LogEntry) getTableRow().getItem() : null;
                setText(empty || e == null ? null : e.username());
            }
        });

        // Hostname
        TableColumn<LogEntry, String> colHost = new TableColumn<>("Machine");
        colHost.setPrefWidth(110);
        colHost.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                LogEntry e = getTableRow() != null ? (LogEntry) getTableRow().getItem() : null;
                setText(empty || e == null ? null : e.hostname());
            }
        });

        // Action
        TableColumn<LogEntry, String> colAction = new TableColumn<>("Action");
        colAction.setPrefWidth(170);
        colAction.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                LogEntry e = getTableRow() != null ? (LogEntry) getTableRow().getItem() : null;
                if (empty || e == null) { setText(null); setGraphic(null); return; }
                Label lbl = new Label(e.action().name().replace('_', ' '));
                lbl.getStyleClass().addAll("badge", actionBadgeStyle(e.action()));
                setGraphic(lbl); setText(null);
            }
        });

        // Target
        TableColumn<LogEntry, String> colTarget = new TableColumn<>("Target");
        colTarget.setPrefWidth(120);
        colTarget.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                LogEntry e = getTableRow() != null ? (LogEntry) getTableRow().getItem() : null;
                setText(empty || e == null || e.target().isBlank() ? "-" : e.target());
            }
        });

        // Detail
        TableColumn<LogEntry, String> colDetail = new TableColumn<>("Detail");
        colDetail.setPrefWidth(180);
        colDetail.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                LogEntry e = getTableRow() != null ? (LogEntry) getTableRow().getItem() : null;
                setText(empty || e == null || e.detail().isBlank() ? "-" : e.detail());
            }
        });

        // Old / New value
        TableColumn<LogEntry, String> colOld = new TableColumn<>("Old Value");
        colOld.setPrefWidth(100);
        colOld.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                LogEntry e = getTableRow() != null ? (LogEntry) getTableRow().getItem() : null;
                setText(empty || e == null || e.oldValue().isBlank() ? "-" : e.oldValue());
            }
        });

        TableColumn<LogEntry, String> colNew = new TableColumn<>("New Value");
        colNew.setPrefWidth(100);
        colNew.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                LogEntry e = getTableRow() != null ? (LogEntry) getTableRow().getItem() : null;
                setText(empty || e == null || e.newValue().isBlank() ? "-" : e.newValue());
            }
        });

        table.getColumns().addAll(colTime, colUser, colHost,
                colAction, colTarget, colDetail, colOld, colNew);
        return table;
    }

    // ── Footer ────────────────────────────────────────────────────────────────

    private VBox buildFooter() {
        statusLabel = new Label();
        statusLabel.getStyleClass().add("status-label");

        Button btnExport = new Button("Export to CSV");
        btnExport.getStyleClass().add("btn-secondary");
        btnExport.setOnAction(e -> exportLog());

        Button btnClose = new Button("Close");
        btnClose.getStyleClass().add("btn-secondary");
        btnClose.setOnAction(e -> close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(10, statusLabel, spacer, btnExport, btnClose);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 24, 8, 24));

        Label copyright = new Label(App.COPYRIGHT);
        copyright.getStyleClass().add("copyright-label");
        copyright.setMaxWidth(Double.MAX_VALUE);
        copyright.setAlignment(Pos.CENTER);
        copyright.setPadding(new Insets(0, 24, 8, 24));

        VBox footer = new VBox(0, bar, copyright);
        footer.getStyleClass().add("wizard-footer");
        return footer;
    }

    // ── Logic ─────────────────────────────────────────────────────────────────

    private void loadEntries() {
        try {
            List<LogEntry> entries = logger.loadAll();
            masterList.setAll(entries);

            // Populate user filter
            List<String> users = entries.stream()
                    .map(LogEntry::username).distinct().sorted().collect(
                    java.util.stream.Collectors.toList());
            String prevUser = cbUser.getValue();
            cbUser.getItems().clear();
            cbUser.getItems().add("All Users");
            cbUser.getItems().addAll(users);
            cbUser.setValue(prevUser != null ? prevUser : "All Users");

            // Populate action filter
            List<String> actions = entries.stream()
                    .map(e -> e.action().name()).distinct().sorted().collect(
                    java.util.stream.Collectors.toList());
            String prevAction = cbAction.getValue();
            cbAction.getItems().clear();
            cbAction.getItems().add("All Actions");
            cbAction.getItems().addAll(actions);
            cbAction.setValue(prevAction != null ? prevAction : "All Actions");

            applyFilters();
        } catch (IOException e) {
            statusLabel.setText("Could not load log: " + e.getMessage());
        }
    }

    private void applyFilters() {
        String search    = tfSearch.getText() == null ? "" : tfSearch.getText().toLowerCase();
        String userSel   = cbUser.getValue();
        String actionSel = cbAction.getValue();
        LocalDate from   = dpFrom.getValue();
        LocalDate to     = dpTo.getValue();

        filteredList.setPredicate(e -> {
            boolean matchSearch = search.isBlank()
                    || e.detail().toLowerCase().contains(search)
                    || e.target().toLowerCase().contains(search)
                    || e.username().toLowerCase().contains(search)
                    || e.oldValue().toLowerCase().contains(search)
                    || e.newValue().toLowerCase().contains(search);

            boolean matchUser   = userSel == null   || userSel.equals("All Users")
                                  || e.username().equalsIgnoreCase(userSel);
            boolean matchAction = actionSel == null || actionSel.equals("All Actions")
                                  || e.action().name().equals(actionSel);
            boolean matchFrom   = from == null
                                  || !e.timestamp().toLocalDate().isBefore(from);
            boolean matchTo     = to   == null
                                  || !e.timestamp().toLocalDate().isAfter(to);

            return matchSearch && matchUser && matchAction && matchFrom && matchTo;
        });

        statusLabel.setText(filteredList.size() + " of " +
                masterList.size() + " entries shown");
    }

    private void clearFilters() {
        tfSearch.clear();
        cbUser.setValue("All Users");
        cbAction.setValue("All Actions");
        dpFrom.setValue(null);
        dpTo.setValue(null);
        applyFilters();
    }

    private void exportLog() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Change Log");
        fc.setInitialFileName("change_log_export.csv");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File file = fc.showSaveDialog(this);
        if (file != null) {
            try {
                logger.exportToCsv(
                        java.util.Collections.unmodifiableList(filteredList),
                        file.getAbsolutePath());
                statusLabel.setText("Exported " + filteredList.size() +
                        " entries to " + file.getName());
            } catch (IOException e) {
                Alert err = new Alert(Alert.AlertType.ERROR,
                        "Export failed: " + e.getMessage(), ButtonType.OK);
                err.initOwner(this);
                err.showAndWait();
            }
        }
    }

    // ── Badge styling ─────────────────────────────────────────────────────────

    private String actionBadgeStyle(Action action) {
        String name = action.name();
        if (name.contains("DELETED") || name.contains("RESET"))  return "badge-missing";
        if (name.contains("CREATED") || name.contains("ADDED"))  return "badge-active";
        if (name.contains("FAILED")  || name.contains("STALE"))  return "badge-retired";
        if (name.contains("DISABLED"))                            return "badge-inactive";
        if (name.contains("LOGIN"))                               return "badge-cat";
        return "badge-repair";
    }
}
