package net.jselby.chip8

import java.util.*

data class CPU(val registers: Registers = Registers(),
               val stack : Stack<Int> = Stack(),
               val ramSize : Int = 4096,
               val random : Random = Random()) {
    val ram : ByteArray = ByteArray(ramSize)
}

data class Registers(val vX : Array<Int> = Array(16, {0}),
                     var i : Int = 0,
                     var pc : Int = 0x200,
                     var delayTimer : Int = 0,
                     var soundTimer : Int = 0)