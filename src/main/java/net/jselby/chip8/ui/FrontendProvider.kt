package net.jselby.chip8.ui

import net.jselby.chip8.interpreter.DrawRequest
import java.util.concurrent.CompletableFuture

/**
 * An abstraction for the frontend process.
 */
interface FrontendProvider {
    /**
     * If the frontend will automatically decrement
     * the CPU's timers based upon frame updating.
     *
     * @see [Interpreter's updateTimers()][net.jselby.chip8.interpreter.Interpreter.updateTimers()]
     *
     * @return If the frontend will call updateTimers() automatically
     */
    fun hasTimerUpdater() : Boolean

    /**
     * If the interpreter should continue. Use this to terminate
     * the interpreter early.
     *
     * @return If the interpreter should continue.
     */
    fun doContinue() : Boolean

    /**
     * Called when the interpreter is just about to start.
     * Use this to update your UI state, if required.
     */
    fun beforeStartCallback() {}

    /**
     * Clears the screen, resetting pixels to their original position.
     */
    fun clearScreen()

    /**
     * Beep at the user.
     */
    fun beep()

    /**
     * Sets the line of the RAM that we are currently in.
     *
     * @param line The new line
     */
    fun setDebuggingLine(line : Int)

    /**
     * Sends a payload of pixels to the frontend to be updated on the screen.
     *
     * @return If a pixel was changed to false as a result of this request
     */
    fun postDrawRequest(request: DrawRequest): Boolean

    /**
     * Returns if a key is currently pressed. The mapping of these is
     * up to the frontend.
     *
     * @param key The hexadecimal key that we are checking.
     *
     * @return If this key is pressed.
     */
    fun isKeyPressed(key : Int) : Boolean

    /**
     * Posts a future to the frontend to be posted when a key is pressed.
     *
     * @param future The future to post.
     */
    fun postKeyPressedFuture(future : CompletableFuture<Int>)
}