package net.jselby.chip8

import javafx.application.Application
import net.jselby.chip8.frontend.JavaFXFrontend

/**
 * The main entry point for the Emulator/Interpreter, using the JavaFX frontend.
 */
class Chip8Emulator {
    private constructor()

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Application.launch(JavaFXFrontend::class.java, *args)
        }
    }
}