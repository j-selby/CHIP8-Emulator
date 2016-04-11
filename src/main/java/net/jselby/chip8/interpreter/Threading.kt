package net.jselby.chip8.interpreter

/**
 * Various objects sent between threads
 */
data class DrawRequest(val baseX : Int, val baseY : Int,
                       val width : Int, val height : Int,
                       val pixels : BooleanArray)