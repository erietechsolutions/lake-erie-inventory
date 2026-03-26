package com.itinventory.ui;

import com.itinventory.model.Asset;
import com.itinventory.model.Asset.Category;
import com.itinventory.model.Asset.Status;
import com.itinventory.model.UserAccount;
import com.itinventory.service.InventoryService;
import com.itinventory.util.OrgConfig;
import com.itinventory.util.UpdateChecker;
import com.itinventory.util.UserManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Root view: org banner + sidebar + toolbar + asset table + status bar.
 */
public class MainView {

    private final Stage             stage;
    private final InventoryService  service;
    private final OrgConfig         config;
    private final UserManager       userManager;
    private final boolean           readOnly;
    private final BorderPane        root;
    private OrgBanner               orgBanner;
    private final ExecutorService   executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "update-check-bg");
        t.setDaemon(true);
        return t;
    });

    // Table data
    private final ObservableList<Asset> masterList   = FXCollections.observableArrayList();
    private final FilteredList<Asset>   filteredList = new FilteredList<>(masterList, a -> true);

    // UI refs
    private AssetTableView tableView;
    private Label statusLabel;
    private TextField searchField;
    private ComboBox<String> categoryFilter;
    private ComboBox<String> statusFilter;

    // ── Constructor ───────────────────────────────────────────────────────────

    public MainView(Stage stage, InventoryService service, OrgConfig config,
                    UserManager userManager, boolean readOnly) {
        this.stage       = stage;
        this.service     = service;
        this.config      = config;
        this.userManager = userManager;
        this.readOnly    = readOnly;
        this.root        = new BorderPane();
        root.getStyleClass().add("main-root");

        orgBanner = new OrgBanner(config, this::openSettings, this::openUpdateDialog);
        VBox topRegion = new VBox(orgBanner, buildToolbar());
        root.setTop(topRegion);
        root.setLeft(buildSidebar());
        root.setCenter(buildCenter());
        root.setBottom(buildStatusBar());

        updateTitle();
        refreshTable();
        runSilentUpdateCheck();
    }

    public BorderPane getRoot() { return root; }

    private void updateTitle() {
        String name = config.getOrgName().isBlank() ? "Lake Erie Inventory" : config.getOrgName();
        UserAccount user = userManager.getCurrentUser();
        String userInfo  = user != null ? " [" + user.getUsername() + "]" : "";
        String roLabel   = (readOnly || (user != null && user.isViewOnly())) ? " (Read Only)" : "";
        stage.setTitle(name + " - Lake Erie Inventory" + userInfo + roLabel);
    }

    private boolean canEdit() {
        if (readOnly) return false;
        UserAccount u = userManager.getCurrentUser();
        return u != null && u.canEdit();
    }

    private boolean isAdmin() {
        UserAccount u = userManager.getCurrentUser();
        return u != null && u.isAdmin();
    }

    private void openSettings() {
        if (!isAdmin()) {
            Alert a = new Alert(Alert.AlertType.INFORMATION,
                "Settings are only accessible to Admin users.", ButtonType.OK);
            a.setTitle("Access Restricted");
            a.initOwner(stage);
            a.showAndWait();
            return;
        }
        SettingsView sv = new SettingsView(stage, config, service, userManager, () -> {
            orgBanner.refresh(config);
            updateTitle();
        }, () -> {
            refreshTable();
            updateStatus("Database reset - all inventory data has been cleared.");
        });
        sv.showAndWait();
    }

    private void openUpdateDialog() {
        UpdateDialog dialog = new UpdateDialog(stage);
        dialog.showAndWait();
    }

    /**
     * Runs a silent update check in the background when the app starts.
     * If an update is found, shows the badge in the org banner without
     * interrupting the user.
     */
    private void runSilentUpdateCheck() {
        executor.submit(() -> {
            UpdateChecker.UpdateResult result = UpdateChecker.check();
            if (result.isUpdateAvailable()) {
                Platform.runLater(() ->
                    orgBanner.showUpdateBadge(result.latestVersion()));
            }
        });
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────

    private VBox buildSidebar() {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(200);

        // App title / logo area
        VBox logoBox = new VBox(4);
        logoBox.getStyleClass().add("sidebar-logo");
        Label appTitle = new Label("Lake Erie");
        appTitle.getStyleClass().add("sidebar-title");
        Label appSub = new Label("Inventory");
        appSub.getStyleClass().add("sidebar-subtitle");
        logoBox.getChildren().addAll(appTitle, appSub);

        // Logged-in user badge
        UserAccount user = userManager.getCurrentUser();
        if (user != null) {
            Label userLabel = new Label(user.getUsername());
            userLabel.getStyleClass().add("sidebar-user-name");
            Label roleLabel = new Label(user.getRole().displayName() +
                    (readOnly ? " - Read Only" : ""));
            roleLabel.getStyleClass().add("sidebar-user-role");
            VBox userBox = new VBox(2, userLabel, roleLabel);
            userBox.getStyleClass().add("sidebar-user-box");
            userBox.setPadding(new Insets(8, 14, 8, 14));
            logoBox.getChildren().add(userBox);
        }

        // Nav buttons
        Button btnAll         = sidebarButton("📋  All Assets",      () -> clearFilters());
        Button btnActive      = sidebarButton("✅  Active",           () -> applyStatusFilter("ACTIVE"));
        Button btnInRepair    = sidebarButton("🔧  In Repair",        () -> applyStatusFilter("IN_REPAIR"));
        Button btnInactive    = sidebarButton("💤  Inactive",         () -> applyStatusFilter("INACTIVE"));
        Button btnRetired     = sidebarButton("🗄  Retired",          () -> applyStatusFilter("RETIRED"));
        Button btnMissing     = sidebarButton("❓  Missing",          () -> applyStatusFilter("MISSING"));

        Separator sep1 = new Separator();
        sep1.getStyleClass().add("sidebar-sep");

        Button btnWarranty    = sidebarButton("⚠️  Expired Warranty", () -> showExpiredWarranties());
        Button btnExpiringSoon = sidebarButton("⏰  Expiring Soon",   () -> showExpiringSoon());

        Separator sep2 = new Separator();
        sep2.getStyleClass().add("sidebar-sep");

        Button btnExport = sidebarButton("📤  Export CSV", () -> exportCsv());
        Button btnReload = sidebarButton("🔄  Reload",     () -> reload());

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        sidebar.getChildren().addAll(
                logoBox, btnAll,
                sep1, btnActive, btnInRepair, btnInactive, btnRetired, btnMissing,
                sep2, btnWarranty, btnExpiringSoon,
                spacer, btnExport, btnReload
        );

        // Admin-only: User Management shortcut
        if (isAdmin()) {
            Button btnUsers = sidebarButton("👥  User Management",
                    () -> new UserManagementView(stage, userManager).showAndWait());
            sidebar.getChildren().add(btnUsers);
        }

        return sidebar;
    }

    private Button sidebarButton(String text, Runnable action) {
        Button btn = new Button(text);
        btn.getStyleClass().add("sidebar-btn");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setOnAction(e -> action.run());
        return btn;
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private HBox buildToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.getStyleClass().add("toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);

        searchField = new TextField();
        searchField.setPromptText("🔍  Search by name, serial, assignee…");
        searchField.getStyleClass().add("search-field");
        searchField.setPrefWidth(280);
        searchField.textProperty().addListener((obs, o, n) -> applyFilters());

        categoryFilter = new ComboBox<>();
        categoryFilter.getStyleClass().add("filter-combo");
        categoryFilter.setPromptText("Category");
        categoryFilter.setPrefWidth(140);
        List<String> cats = new ArrayList<>();
        cats.add("All Categories");
        Arrays.stream(Category.values()).map(Enum::name).forEach(cats::add);
        categoryFilter.setItems(FXCollections.observableArrayList(cats));
        categoryFilter.getSelectionModel().selectFirst();
        categoryFilter.setOnAction(e -> applyFilters());

        statusFilter = new ComboBox<>();
        statusFilter.getStyleClass().add("filter-combo");
        statusFilter.setPromptText("Status");
        statusFilter.setPrefWidth(130);
        List<String> statuses = new ArrayList<>();
        statuses.add("All Statuses");
        Arrays.stream(Status.values()).map(Enum::name).forEach(statuses::add);
        statusFilter.setItems(FXCollections.observableArrayList(statuses));
        statusFilter.getSelectionModel().selectFirst();
        statusFilter.setOnAction(e -> applyFilters());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnAdd = new Button("＋  Add Asset");
        btnAdd.getStyleClass().addAll("btn-primary");
        btnAdd.setOnAction(e -> openAddDialog());
        btnAdd.setDisable(!canEdit());

        Button btnEdit = new Button("✏  Edit");
        btnEdit.getStyleClass().add("btn-secondary");
        btnEdit.setOnAction(e -> openEditDialog());
        btnEdit.setDisable(!canEdit());

        Button btnDelete = new Button("🗑  Delete");
        btnDelete.getStyleClass().add("btn-danger");
        btnDelete.setOnAction(e -> deleteSelected());
        btnDelete.setDisable(!canEdit());

        // Show read-only badge in toolbar if restricted
        if (!canEdit()) {
            Label roLabel = new Label("👁  View Only");
            roLabel.getStyleClass().add("readonly-badge");
            toolbar.getChildren().addAll(searchField, categoryFilter, statusFilter,
                    spacer, roLabel, btnAdd, btnEdit, btnDelete);
        } else {
            toolbar.getChildren().addAll(searchField, categoryFilter, statusFilter,
                    spacer, btnAdd, btnEdit, btnDelete);
        }
        return toolbar;
    }

    // ── Center (table) ────────────────────────────────────────────────────────

    private StackPane buildCenter() {
        tableView = new AssetTableView(filteredList);
        if (canEdit()) {
            tableView.setOnDoubleClick(this::openEditDialog);
        }
        StackPane center = new StackPane(tableView.getTableView());
        center.getStyleClass().add("table-container");
        return center;
    }

    // ── Status bar ────────────────────────────────────────────────────────────

    private HBox buildStatusBar() {
        HBox bar = new HBox(20);
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label();
        statusLabel.getStyleClass().add("status-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label copyright = new Label(App.COPYRIGHT);
        copyright.getStyleClass().add("copyright-label");

        bar.getChildren().addAll(statusLabel, spacer, copyright);
        return bar;
    }

    // ── Filter logic ──────────────────────────────────────────────────────────

    private void applyFilters() {
        String search  = searchField.getText() == null ? "" : searchField.getText().toLowerCase();
        String catSel  = categoryFilter.getValue();
        String statSel = statusFilter.getValue();

        filteredList.setPredicate(a -> {
            boolean matchSearch = search.isBlank()
                    || contains(a.getName(), search)
                    || contains(a.getSerialNumber(), search)
                    || contains(a.getAssignedTo(), search)
                    || contains(a.getAssetId(), search)
                    || contains(a.getLocation(), search)
                    || contains(a.getAssetTag(), search);

            boolean matchCat = catSel == null || catSel.equals("All Categories")
                    || (a.getCategory() != null && a.getCategory().name().equals(catSel));

            boolean matchStat = statSel == null || statSel.equals("All Statuses")
                    || (a.getStatus() != null && a.getStatus().name().equals(statSel));

            return matchSearch && matchCat && matchStat;
        });

        updateStatus();
    }

    private void clearFilters() {
        searchField.clear();
        categoryFilter.getSelectionModel().selectFirst();
        statusFilter.getSelectionModel().selectFirst();
        applyFilters();
    }

    private void applyStatusFilter(String status) {
        statusFilter.setValue(status);
        applyFilters();
    }

    private void showExpiredWarranties() {
        List<Asset> expired = service.getExpiredWarranties();
        masterList.setAll(service.getAllAssets());
        filteredList.setPredicate(a -> expired.stream()
                .anyMatch(e -> e.getAssetId().equals(a.getAssetId())));
        updateStatus("Showing " + expired.size() + " assets with expired warranties");    }

    private void showExpiringSoon() {
        List<Asset> soon = service.getWarrantiesExpiringSoon(30);
        masterList.setAll(service.getAllAssets());
        filteredList.setPredicate(a -> soon.stream()
                .anyMatch(e -> e.getAssetId().equals(a.getAssetId())));
        updateStatus("Showing " + soon.size() + " assets with warranties expiring within 30 days");
    }

    // ── CRUD actions ──────────────────────────────────────────────────────────

    private void openAddDialog() {
        AssetDialog dialog = new AssetDialog(stage, null, service);
        dialog.showAndWait().ifPresent(asset -> {
            try {
                String id = service.addAsset(asset);
                refreshTable();
                updateStatus("Added asset " + id);
            } catch (IOException e) {
                showError("Failed to save asset", e.getMessage());
            }
        });
    }

    private void openEditDialog() {
        Asset selected = tableView.getSelected();
        if (selected == null) {
            showInfo("No selection", "Please select an asset to edit.");
            return;
        }
        AssetDialog dialog = new AssetDialog(stage, selected, service);
        dialog.showAndWait().ifPresent(updated -> {
            try {
                service.updateAsset(updated);
                refreshTable();
                updateStatus("Updated asset " + updated.getAssetId());
            } catch (IOException e) {
                showError("Failed to update asset", e.getMessage());
            }
        });
    }

    private void deleteSelected() {
        Asset selected = tableView.getSelected();
        if (selected == null) {
            showInfo("No selection", "Please select an asset to delete.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Asset");
        confirm.setHeaderText("Delete " + selected.getAssetId() + " – " + selected.getName() + "?");
        confirm.setContentText("This action cannot be undone.");
        confirm.initOwner(stage);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    service.deleteAsset(selected.getAssetId());
                    refreshTable();
                    updateStatus("Deleted asset " + selected.getAssetId());
                } catch (IOException e) {
                    showError("Failed to delete asset", e.getMessage());
                }
            }
        });
    }

    private void exportCsv() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Inventory to CSV");
        fc.setInitialFileName("inventory_export.csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File file = fc.showSaveDialog(stage);
        if (file != null) {
            try {
                service.exportFiltered(new ArrayList<>(filteredList), file.getAbsolutePath());
                updateStatus("Exported " + filteredList.size() + " asset(s) to " + file.getName());
            } catch (IOException e) {
                showError("Export failed", e.getMessage());
            }
        }
    }

    private void reload() {
        try {
            service.reload();
            refreshTable();
            updateStatus("Reloaded from disk — " + service.getTotalCount() + " asset(s)");
        } catch (IOException e) {
            showError("Reload failed", e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public void refreshTable() {
        masterList.setAll(service.getAllAssets());
        applyFilters();
        updateStatus();
    }

    private void updateStatus() {
        long total    = masterList.size();
        long showing  = filteredList.size();
        double value  = filteredList.stream().mapToDouble(Asset::getPurchasePrice).sum();
        statusLabel.setText(String.format(
                "%d asset(s) shown of %d total  |  Total value: $%,.2f", showing, total, value));
    }

    private void updateStatus(String msg) {
        statusLabel.setText(msg);
    }

    private boolean contains(String field, String query) {
        return field != null && field.toLowerCase().contains(query);
    }

    private void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setTitle(title);
        a.initOwner(stage);
        a.showAndWait();
    }

    private void showInfo(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setTitle(title);
        a.initOwner(stage);
        a.showAndWait();
    }
}
