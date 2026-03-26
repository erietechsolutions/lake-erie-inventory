# Lake Erie Inventory | IT Asset Inventory Management System
### JavaFX GUI Edition

A graphical Java desktop application for managing your IT asset inventory.
All data is stored in plain CSV files.

---

## Quick Start (Windows)

### Step 1 — Install JavaFX SDK (one time only)

1. Go to **https://gluonhq.com/products/javafx/**
2. Choose: **JavaFX 21 LTS** → **SDK** → **Windows** → **x64** → Download
3. Extract the zip somewhere easy, e.g. `C:\javafx-sdk-21`
   - You should see `C:\javafx-sdk-21\lib\` containing `.jar` files

### Step 2 — Configure the launcher

1. Open `run.bat` in Notepad
2. Find this line near the top:
   ```
   SET JAVAFX_PATH=C:\javafx-sdk-21
   ```
3. Change the path to match where you extracted the SDK, then save

### Step 3 — Run

Double-click `run.bat` — it compiles and launches the app automatically.

---

## Features

- Color-coded asset table with sortable columns
- Live search + category/status filter dropdowns
- Add / Edit / Delete assets via polished dialog
- Sidebar quick-filters (Active, In Repair, Expired Warranty, etc.)
- Export current filtered view to CSV
- Auto-backup before every save

---

## Troubleshooting

| Problem | Fix |
|---|---|
| "JavaFX SDK not found" | Edit `JAVAFX_PATH` in `run.bat` to match your extraction folder |
| "javac is not recognized" | Install the JDK (not just JRE) from https://adoptium.net |
| Blank white window | Re-run `run.bat` — the CSS copy step should fix it |
| Module/LinkageError | Make sure you downloaded the **SDK** zip from Gluon, not jmods |

---

## Requirements

- Java JDK 17+ — https://adoptium.net
- JavaFX SDK 17+ — https://gluonhq.com/products/javafx/ (21.0.10 LTS Works Best)
=======
# opensource-it-inventory
This is an open source IT inventory system
