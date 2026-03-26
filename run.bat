@echo off
SET JAVAFX_PATH=C:\javafx-sdk-21

IF NOT EXIST "%JAVAFX_PATH%\lib" (
    echo ERROR: JavaFX not found at %JAVAFX_PATH%
    echo Edit JAVAFX_PATH in this file to point to your JavaFX SDK folder.
    pause
    exit /b 1
)

IF NOT EXIST out mkdir out

echo Compiling Lake Erie Inventory...
javac --module-path "%JAVAFX_PATH%\lib" --add-modules javafx.controls,javafx.fxml -encoding UTF-8 -d out src\main\java\com\itinventory\model\Asset.java src\main\java\com\itinventory\util\IdGenerator.java src\main\java\com\itinventory\util\CsvManager.java src\main\java\com\itinventory\util\OrgConfig.java src\main\java\com\itinventory\util\UpdateChecker.java src\main\java\com\itinventory\util\AutoUpdater.java src\main\java\com\itinventory\service\InventoryService.java src\main\java\com\itinventory\ui\App.java src\main\java\com\itinventory\ui\AssetTableView.java src\main\java\com\itinventory\ui\AssetDialog.java src\main\java\com\itinventory\ui\OrgBanner.java src\main\java\com\itinventory\ui\SetupWizard.java src\main\java\com\itinventory\ui\SettingsView.java src\main\java\com\itinventory\ui\UpdateDialog.java src\main\java\com\itinventory\ui\MainView.java src\main\java\com\itinventory\Main.java

IF ERRORLEVEL 1 (
    echo.
    echo Compilation failed. See errors above.
    pause
    exit /b 1
)

echo Launching Lake Erie Inventory...
java --module-path "%JAVAFX_PATH%\lib" --add-modules javafx.controls,javafx.fxml -cp "out;src\main\resources" com.itinventory.Main
pause
