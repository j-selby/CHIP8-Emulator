package net.jselby.chip8

import net.jselby.chip8.interpreter.DrawRequest
import net.jselby.chip8.ui.FrontendProvider
import java.util.concurrent.CompletableFuture

/**
 * A simple interpreter frontend, useful for tests.
 */
class NullFrontend : FrontendProvider {
    val pixels = BooleanArray(64 * 32)

    override fun hasTimerUpdater() = false

    override fun doContinue() = true

    override fun clearScreen() {
        pixels.fill(false)
    }

    override fun beep() {}

    override fun setDebuggingLine(line: Int) {}

    override fun postDrawRequest(request: DrawRequest): Boolean {
        var response = false

        for (y in 0..request.height - 1) {
            var renderY = request.baseY + y
            while (renderY >= 32) {
                renderY -= 32
            }

            for (x in 0..request.width - 1) {
                val localIndex = y * request.width + x

                if (request.pixels[localIndex]) {
                    var renderX = request.baseX + x
                    while (renderX >= 64) {
                        renderX -= 64
                    }

                    val globalIndex = (renderY * 64 + renderX).toInt()

                    if (pixels.size <= globalIndex || globalIndex < 0) {
                        error("Writing to invalid pixel: $globalIndex from $renderX : $renderY")
                    }
                    // XOR the current image
                    pixels[globalIndex] = !pixels[globalIndex]

                    if (!response && !pixels[globalIndex]) {
                        response = true
                    }
                }
            }
        }

        return response
    }

    override fun isKeyPressed(key: Int) = false

    override fun postKeyPressedFuture(future: CompletableFuture<Int>) {
        future.complete(0)
    }

}