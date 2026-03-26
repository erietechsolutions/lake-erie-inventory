package com.itinventory;

import com.itinventory.ui.App;

/**
 * Entry point – delegates to the JavaFX Application class.
 * Keeping main() outside App avoids module/classpath issues
 * when running without the JavaFX launcher.
 */
public class Main {
    public static void main(String[] args) {
        App.main(args);
    }
}
