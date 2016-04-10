package net.jselby.chip8

import net.jselby.chip8.decoder.InstructionType
import net.jselby.chip8.decoder.decodeInstruction
import java.io.FileInputStream
import java.util.concurrent.CompletableFuture


fun toHex(value : Number, length : Int = 4) = String.format("%0" + length + "X", value)

/**
 * A simple implementation of a CHIP-8 machine in Kotlin.
 *
 * HW info taken from https://en.wikipedia.org/wiki/CHIP-8 and attached documentation. by cowgod.
 */
class Interpreter(private val chip: Chip8?) {
    var cpu = CPU()
    var rom = ""
    var byteRom : ByteArray? = null
    var exitAsserted = false

    fun start(reset : Boolean = true, loadRom : Boolean = true,
              useByteRom : Boolean = false) {
        exitAsserted = false

        // Build fonts!
        val fonts = intArrayOf(
                0xF0, 0x90, 0x90, 0x90, 0xF0, // 0
                0x20, 0x60, 0x20, 0x20, 0x70, // 1
                0xF0, 0x10, 0xF0, 0x80, 0xF0, // 2
                0xF0, 0x10, 0xF0, 0x10, 0xF0, // 3
                0x90, 0x90, 0xF0, 0x10, 0x10, // 4
                0xF0, 0x80, 0xF0, 0x10, 0xF0, // 5
                0xF0, 0x80, 0xF0, 0x90, 0xF0, // 6
                0xF0, 0x10, 0x20, 0x40, 0x40, // 7
                0xF0, 0x90, 0xF0, 0x90, 0xF0, // 8
                0xF0, 0x90, 0xF0, 0x10, 0xF0, // 9
                0xF0, 0x90, 0xF0, 0x90, 0x90, // A
                0xE0, 0x90, 0xE0, 0x90, 0xE0, // B
                0xF0, 0x80, 0x80, 0x80, 0xF0, // C
                0xE0, 0x90, 0x90, 0x90, 0xE0, // D
                0xF0, 0x80, 0xF0, 0x80, 0xF0, // E
                0xF0, 0x80, 0xF0, 0x80, 0x80  // F
        )

        val fontsStart = 0x1FF - fonts.size

        // Build CPU
        if (reset) {
            cpu = CPU()

            for ((index, element) in fonts.withIndex()) {
                cpu.ram[fontsStart + index] = (element and 0xFF).toByte()
            }

            // Clear screen
            chip?.clearScreen()
        } else {
            cpu.registers.pc = 0x200
        }

        if (loadRom) {
            // Load game data
            val rom : ByteArray
            if (useByteRom) {
                rom = byteRom!!
            } else {
                val romIn = FileInputStream(this.rom)
                rom = romIn.readBytes()
                romIn.close()
                println("Loaded ROM successfully.")

                println("Starting emulation at 0x${toHex(cpu.registers.pc, 3)}...")
            }

            // Copy ROM into memory
            System.arraycopy(rom, 0, cpu.ram, cpu.registers.pc, rom.size)
        }

        // Update the UI
        chip?.sendRAM(cpu.ram)

        var sysTime = System.currentTimeMillis()

        // Main loop
        interpreterLoop@
        while (chip?.isVisible ?: true) {
            // Check timers
            if (System.currentTimeMillis() - sysTime > 1000 / 60) {
                // We are ready for a re-render!
                if (cpu.registers.delayTimer > 0) {
                    cpu.registers.delayTimer--
                }

                if (cpu.registers.soundTimer > 0) {
                    chip?.beep()
                    cpu.registers.soundTimer--
                }

                sysTime = System.currentTimeMillis()
            }

            // On a CHIP-8, the first 4 bits define a single hex digit corresponding to a
            //  general area of operation.
            // The remainder of the instruction is the arguments for the instruction
            //  or form part of the instruction itself. Regardless, we split it here to get
            //  the general gist of the command.
            val highInst = cpu.ram[cpu.registers.pc].toInt() and 0xFF
            val lowInst = cpu.ram[cpu.registers.pc + 1].toInt() and 0xFF
            val instVal = (highInst shl 8) + lowInst
            val inst = decodeInstruction(instVal)

            //println("${toHex(highInst, 2)} ${toHex(lowInst, 2)} = $inst @ 0x${toHex(cpu.registers.pc, 4)}")

            // Send to UI
            chip?.setInstLine(cpu.registers.pc)

            // Increment CPU status cpu.registers.pc
            cpu.registers.pc += 2

            // Interpret instruction
            when (inst) {
                InstructionType.LDB -> {
                    // 6xkk - Set Vx = kk.
                    val x = inst.matcher.getArgument(instVal, 'x')
                    val newVal = inst.matcher.getArgument(instVal, 'k')

                    //println("v$x = $newVal")

                    cpu.registers.vX[x] = newVal and 0x00FF
                }
                InstructionType.CLS -> {
                    // Clear the screen
                    chip?.clearScreen()
                }
                InstructionType.LD -> {
                    // Set I = nnn.
                    val newVal = inst.matcher.getArgument(instVal, 'n')

                    //println("I = $newVal")

                    cpu.registers.i = newVal and 0x0FFF
                }
                InstructionType.SEVB -> {
                    // Skip next instruction if Vx = kk.
                    val x = inst.matcher.getArgument(instVal, 'x')
                    val compareVal = inst.matcher.getArgument(instVal, 'k') and 0x00FF

                    //println("v$x ?= $compareVal")

                    if (cpu.registers.vX[x] == compareVal) {
                        cpu.registers.pc += 2
                    }
                }
                InstructionType.SEVV -> {
                    // Skip next instruction if Vx = Vy.
                    val x = inst.matcher.getArgument(instVal, 'x')
                    val y = inst.matcher.getArgument(instVal, 'y')

                    if (cpu.registers.vX[x] == cpu.registers.vX[y]) {
                        cpu.registers.pc += 2
                    }
                }
                InstructionType.SNEV -> {
                    // Skip next instruction if Vx != Vy.
                    val x = inst.matcher.getArgument(instVal, 'x')
                    val y = inst.matcher.getArgument(instVal, 'y')

                    if (cpu.registers.vX[x] != cpu.registers.vX[y]) {
                        cpu.registers.pc += 2
                    }
                }
                InstructionType.SHL -> {
                    // Set Vx = Vx SHL 1.
                    val x = inst.matcher.getArgument(instVal, 'x')
                    cpu.registers.vX[0xF] = cpu.registers.vX[x] and 0x80
                    cpu.registers.vX[x] = cpu.registers.vX[x] shl 1

                    //println("v$x ?!= $compareVal")

                }
                InstructionType.SHR -> {
                    // Set Vx = Vx SHR 1.
                    val x = inst.matcher.getArgument(instVal, 'x')
                    cpu.registers.vX[0xF] = cpu.registers.vX[x] and 0x01
                    cpu.registers.vX[x] = cpu.registers.vX[x] shr 1

                    //println("v$x ?!= $compareVal")

                }
                InstructionType.SNEB -> {
                    // Skip next instruction if Vx != kk.
                    val x = inst.matcher.getArgument(instVal, 'x')
                    val compareVal = inst.matcher.getArgument(instVal, 'k') and 0x00FF

                    //println("v$x ?!= $compareVal")

                    if (cpu.registers.vX[x] != compareVal) {
                        cpu.registers.pc += 2
                    }
                }
                InstructionType.LDV -> {
                    // Set Vx = Vy.
                    val x = inst.matcher.getArgument(instVal, 'x')
                    val y = inst.matcher.getArgument(instVal, 'y')

                    //println("v$x = $vy")

                    cpu.registers.vX[x] = cpu.registers.vX[y]
                }
                InstructionType.SUB -> {
                    // Set Vx = Vx - Vy, set VF = NOT borrow.
                    val x = inst.matcher.getArgument(instVal, 'x')
                    val y = inst.matcher.getArgument(instVal, 'y')

                    //println("v$x -= $vy, vF = borrow")

                    cpu.registers.vX[0xF] = if (cpu.registers.vX[x] > cpu.registers.vX[y]) 1 else 0
                    cpu.registers.vX[x] = cpu.registers.vX[x] - cpu.registers.vX[y]
                }

                InstructionType.SUBN -> {
                    // Set Vx = Vy - Vx, set VF = NOT borrow.
                    val x = inst.matcher.getArgument(instVal, 'x')
                    val y = inst.matcher.getArgument(instVal, 'y')

                    //println("v$y -= v$y, vF = borrow")

                    cpu.registers.vX[0xF] = if (cpu.registers.vX[y] <= cpu.registers.vX[x]) 1 else 0
                    cpu.registers.vX[x] = (cpu.registers.vX[y] - cpu.registers.vX[x]) and 0x00FF
                }
                InstructionType.CALL -> {
                    // Call subroutine at nnn.
                    val newPointer = inst.matcher.getArgument(instVal, 'n')

                    //println("Calling subroutine at 0x${toHex(newPointer, 3)} from 0x${toHex(cpu.registers.pc, 3)}")

                    cpu.stack.push(cpu.registers.pc)
                    cpu.registers.pc = newPointer
                }
                InstructionType.RET -> {
                    // Return from a subroutine.
                    val newPointer = cpu.stack.pop()

                    //println("Exiting subroutine at 0x${toHex(newPointer, 3)} to 0x${toHex(cpu.registers.pc, 3)}")

                    cpu.registers.pc = newPointer
                }
                InstructionType.JP -> {
                    // Jump to location nnn.
                    val newPointer = inst.matcher.getArgument(instVal, 'n')

                    if (newPointer == cpu.registers.pc - 2) {
                        println("Infinite loop detected (JP). Killing interpreter.")
                        break@interpreterLoop
                    }

                    //println("Jumping to 0x${toHex(newPointer, 3)} from 0x${toHex(cpu.registers.pc, 3)}")

                    cpu.registers.pc = newPointer
                }
                InstructionType.JPV -> {
                    // Jump to location nnn + v.
                    val newPointer = inst.matcher.getArgument(instVal, 'n') + cpu.registers.vX[0]

                    if (newPointer == cpu.registers.pc - 2) {
                        println("Infinite loop detected (JPV). Killing interpreter.")
                        break@interpreterLoop
                    }

                    //println("Jumping to 0x${toHex(newPointer, 3)} from 0x${toHex(cpu.registers.pc, 3)}")

                    cpu.registers.pc = newPointer
                }
                InstructionType.RND -> {
                    // Set Vx = random byte AND kk.
                    val x = inst.matcher.getArgument(instVal, 'x')
                    val andValue = inst.matcher.getArgument(instVal, 'k') and 0x00FF
                    val randomVal = cpu.random.nextInt(255)

                    //println("Storing random value $randomVal AND $andValue in v$x")

                    cpu.registers.vX[x] = randomVal and andValue

                }
                InstructionType.DRW -> {
                    // Display n-byte sprite starting at memory location I at (Vx, Vy), set VF = collision.
                    val x = cpu.registers.vX[inst.matcher.getArgument(instVal, 'x')]
                    val y = cpu.registers.vX[inst.matcher.getArgument(instVal, 'y')]
                    val size = inst.matcher.getArgument(instVal, 'n') and 0x000F

                    //println("Draw of height $size at $x:$y")

                    // Sprite is 8x5, with each bit corresponding to a X value (true/false, on or off XORed)
                    val array = BooleanArray(size * 8)

                    for ((index, byte) in (cpu.registers.i.toInt()..cpu.registers.i + size - 1).withIndex()) {
                        for (i in 0..7) {
                            array[index * 8 + (7 - i)] = ((cpu.ram[byte].toInt() shr i) and 0x1) == 1
                        }
                    }

                    // Send off the request
                    if (chip != null) {
                        cpu.registers.vX[0xF] = if (chip.postDrawRequest(DrawRequest(x, y, 8, size, array))) 1 else 0
                    }

                    // Sleep a bit
                    if (chip != null && chip.renderSpeed > 0) {
                        Thread.sleep(chip.renderSpeed.toLong())
                    }
                }
                InstructionType.LDS -> {
                    // Set I = location of (font) sprite for digit Vx.
                    val x = cpu.registers.vX[inst.matcher.getArgument(instVal, 'x')]

                    //println("LDS: $x")

                    cpu.registers.i = fontsStart + x * 5
                }
                InstructionType.ADDB -> {
                    // Set Vx = Vx + kk.
                    val x = inst.matcher.getArgument(instVal, 'x')
                    val newVal = inst.matcher.getArgument(instVal, 'k')

                    //println("v$x += $newVal")

                    cpu.registers.vX[x] = (cpu.registers.vX[x] + newVal) and 0x00FF
                }

                InstructionType.SKP -> {
                    // Skip next instruction if key with the value of Vx is pressed.
                    val x = inst.matcher.getArgument(instVal, 'x')

                    if (chip != null && chip.keysPressed[cpu.registers.vX[x]]!!) {
                        cpu.registers.pc += 2
                    }
                }
                InstructionType.SKNP -> {
                    // Skip next instruction if key with the value of Vx is not pressed.
                    val x = inst.matcher.getArgument(instVal, 'x')

                    if (chip == null || !chip.keysPressed[cpu.registers.vX[x]]!!) {
                        cpu.registers.pc += 2
                    }
                }
                InstructionType.LDKP -> {
                    // Wait for a key press, store the value of the key in Vx.
                    // Wait for UI thread to come back
                    val x = inst.matcher.getArgument(instVal, 'x')

                    println("Interpreter stalling on main thread...")

                    val future = CompletableFuture<Int>()
                    chip?.future = future
                    cpu.registers.vX[x] = future.get()

                    println("Interpreter is back!")
                }
                InstructionType.LDDV -> {
                    // Set delay timer = Vx.
                    val x = inst.matcher.getArgument(instVal, 'x')

                    //println("delayTimer = v$x")

                    cpu.registers.delayTimer = cpu.registers.vX[x]
                }
                InstructionType.LDDS -> {
                    // Set sound timer = Vx.
                    val x = inst.matcher.getArgument(instVal, 'x')

                    //println("soundTimer = v$x")

                    cpu.registers.soundTimer = cpu.registers.vX[x]
                }
                InstructionType.ADDI -> {
                    // Set I = I + Vx.
                    val x = cpu.registers.vX[inst.matcher.getArgument(instVal, 'x')]

                    cpu.registers.i = cpu.registers.i.toInt() + x
                }
                InstructionType.LDVD -> {
                    // Set Vx = delay timer value.
                    val x = inst.matcher.getArgument(instVal, 'x')

                    //println("v$x = delayTimer")

                    cpu.registers.vX[x] = cpu.registers.delayTimer
                }
                InstructionType.AND -> {
                    // Set Vx = Vx AND Vy.
                    val x = inst.matcher.getArgument(instVal, 'x')
                    val y = inst.matcher.getArgument(instVal, 'y')

                    //println("v$x = v$x AND v$y")

                    cpu.registers.vX[x] = (cpu.registers.vX[x] and cpu.registers.vX[y])
                }
                InstructionType.XOR -> {
                    // Set Vx = Vx XOR Vy.
                    val x = inst.matcher.getArgument(instVal, 'x')
                    val y = inst.matcher.getArgument(instVal, 'y')

                    //println("v$x = v$x XOR v$y")

                    cpu.registers.vX[x] = cpu.registers.vX[x] xor cpu.registers.vX[y]
                }
                InstructionType.ADDV -> {
                    // Set Vx = Vx + Vy, set VF = carry.
                    val x = inst.matcher.getArgument(instVal, 'x')
                    val y = inst.matcher.getArgument(instVal, 'y')

                    //println("v$x = v$x + v$y, vF = carry")

                    val result = cpu.registers.vX[x] + cpu.registers.vX[y]

                    cpu.registers.vX[x] = result and 0x00FF
                    cpu.registers.vX[0xF] = if (result > 0xFF) 1 else 0
                }
                InstructionType.LDBC -> {
                    // Store BCD representation of Vx in memory locations I, I+1, and I+2.
                    val x = cpu.registers.vX[inst.matcher.getArgument(instVal, 'x')]

                    cpu.ram[cpu.registers.i.toInt()] = Math.floor(x / 100.0).toByte()
                    cpu.ram[cpu.registers.i.toInt() + 1] = Math.floor(x % 100 / 10.0).toByte()
                    cpu.ram[cpu.registers.i.toInt() + 2] = (x % 10).toByte()
                }
                InstructionType.LDMW -> {
                    // Store registers V0 through Vx in memory starting at location I.
                    val x = inst.matcher.getArgument(instVal, 'x')

                    //println("Copying memory from 0x${toHex(cpu.registers.i, 3)} to v0..v$x")

                    for (vVal in 0..x) {
                        cpu.ram[cpu.registers.i + vVal] = cpu.registers.vX[x].toByte()
                    }
                    cpu.registers.i += x
                }
                InstructionType.LDMR -> {
                    // Read registers V0 through Vx from memory starting at location I.
                    val x = inst.matcher.getArgument(instVal, 'x')

                    //println("Copying memory from 0x${toHex(cpu.registers.i, 3)} to v0..v$x")

                    //println("Read to $x")
                    for (vVal in 0..x) {
                        cpu.registers.vX[vVal] = cpu.ram[cpu.registers.i + vVal].toInt()
                    }
                    cpu.registers.i += x
                }
                InstructionType.ASSERT -> {
                    exitAsserted = true
                    break@interpreterLoop
                }
                else -> {
                    error("Unhandled instruction: $inst with ${toHex(highInst, 2)}" +
                            " ${toHex(lowInst, 2)} at PC ${toHex(cpu.registers.pc - 2, 4)}")
                }
            }
        }
    }
}