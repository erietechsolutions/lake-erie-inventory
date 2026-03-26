@echo off
SET JAVAFX_PATH=C:\javafx-sdk-21
SET ERROR_LOG=compile_error.txt

IF NOT EXIST "%JAVAFX_PATH%\lib" (
    powershell -Command "Add-Type -AssemblyName PresentationFramework; [System.Windows.MessageBox]::Show('JavaFX SDK not found at %JAVAFX_PATH%\n\nEdit JAVAFX_PATH in run.bat to point to your JavaFX SDK folder.', 'Lake Erie Inventory - Setup Error', 'OK', 'Error')"
    exit /b 1
)

IF NOT EXIST out mkdir out

javac --module-path "%JAVAFX_PATH%\lib" --add-modules javafx.controls,javafx.fxml -encoding UTF-8 -d out src\main\java\com\itinventory\model\Asset.java src\main\java\com\itinventory\model\UserAccount.java src\main\java\com\itinventory\util\IdGenerator.java src\main\java\com\itinventory\util\CsvManager.java src\main\java\com\itinventory\util\OrgConfig.java src\main\java\com\itinventory\util\UpdateChecker.java src\main\java\com\itinventory\util\AutoUpdater.java src\main\java\com\itinventory\util\UserManager.java src\main\java\com\itinventory\util\CustomFieldsManager.java src\main\java\com\itinventory\util\ChangeLogger.java src\main\java\com\itinventory\service\InventoryService.java src\main\java\com\itinventory\ui\App.java src\main\java\com\itinventory\ui\AssetTableView.java src\main\java\com\itinventory\ui\AssetDialog.java src\main\java\com\itinventory\ui\OrgBanner.java src\main\java\com\itinventory\ui\SetupWizard.java src\main\java\com\itinventory\ui\LoginView.java src\main\java\com\itinventory\ui\UserManagementView.java src\main\java\com\itinventory\ui\CustomFieldsView.java src\main\java\com\itinventory\ui\ChangeLogView.java src\main\java\com\itinventory\ui\SettingsView.java src\main\java\com\itinventory\ui\UpdateDialog.java src\main\java\com\itinventory\ui\MainView.java src\main\java\com\itinventory\Main.java 2> %ERROR_LOG%

IF ERRORLEVEL 1 (
    SET /p ERROR_TEXT=<%ERROR_LOG%
    powershell -Command "$err = Get-Content '%ERROR_LOG%' -Raw; Add-Type -AssemblyName PresentationFramework; [System.Windows.MessageBox]::Show($err, 'Lake Erie Inventory - Compilation Failed', 'OK', 'Error')"
    exit /b 1
)

DEL /Q %ERROR_LOG% 2>nul

start "" javaw --module-path "%JAVAFX_PATH%\lib" --add-modules javafx.controls,javafx.fxml -cp "out;src\main\resources" com.itinventory.Main
