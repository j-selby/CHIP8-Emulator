package net.jselby.chip8

import net.jselby.chip8.decoder.InstructionType
import net.jselby.chip8.decoder.decodeInstruction
import java.io.FileInputStream


fun toHex(value : Number, length : Int = 4) = String.format("%0" + length + "X", value)

/**
 * A simple implementation of a CHIP-8 machine in Kotlin.
 *
 * HW info taken from https://en.wikipedia.org/wiki/CHIP-8 and attached documentation. by cowgod.
 */
fun start(chip: Chip8) {
    // Post logo
    // CHIP
    /*chip.postDrawRequest(DrawRequest(5, 5, 15, 5,
            booleanArrayOf(
                    false, true,  true,  false, true, false, true, false, true,  true, true,  false, true, true,  false,
                    true,  false, false, false, true, false, true, false, false, true, false, false, true, false, true,
                    true,  false, false, false, true, true,  true, false, false, true, false, false, true, true,  false,
                    true,  false, false, false, true, false, true, false, false, true, false, false, true, false, false,
                    false, true,  true,  false, true, false, true, false, true,  true, true , false, true, false, false
            )
    ))*/

    // Load game data
    val romIn = FileInputStream("roms/PONG")
    val rom = romIn.readBytes()
    romIn.close()

    println("Loaded ROM successfully.")

    // Build CPU
    val cpu = CPU()

    var pointer = 0x200

    // Copy ROM into memory
    System.arraycopy(rom, 0, cpu.ram, pointer, rom.size)

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

    println("Fonts start at $fontsStart")

    for ((index, element) in fonts.withIndex()) {
        cpu.ram[fontsStart + index] = (element and 0xFF).toByte()
    }

    // Update the UI
    chip.sendRAM(cpu.ram)

    var sysTime = System.currentTimeMillis()

    println("Starting emulation at 0x${toHex(pointer, 3)}...")

    // Main loop
    while(chip.isVisible) {
        // Check timers
        if (System.currentTimeMillis() - sysTime > 1000 / 60) {
            // We are ready for a re-render!
            if (cpu.registers.delayTimer > 0) {
                cpu.registers.delayTimer--
            }

            if (cpu.registers.soundTimer > 0) {
                chip.beep()
                cpu.registers.soundTimer--
            }

            sysTime = System.currentTimeMillis()
        }

        // On a CHIP-8, the first 4 bits define a single hex digit corresponding to a
        //  general area of operation.
        // The remainder of the instruction is the arguments for the instruction
        //  or form part of the instruction itself. Regardless, we split it here to get
        //  the general gist of the command.
        val highInst = cpu.ram[pointer].toInt() and 0xFF
        val lowInst = cpu.ram[pointer + 1].toInt() and 0xFF
        val instVal = (highInst shl 8) + lowInst
        val inst = decodeInstruction(instVal)

        //println("${toHex(highInst, 2)} ${toHex(lowInst, 2)} = " + inst)
        // Send to UI
        chip.setInstLine(pointer)

        // Increment CPU status pointer
        pointer += 2

        // Interpret instruction
        when (inst) {
            InstructionType.LDB -> {
                // 6xkk - Set Vx = kk.
                val x =      inst.matcher.getArgument(instVal, 'x')
                val newVal = inst.matcher.getArgument(instVal, 'k')

                //println("v$x = $newVal")

                cpu.registers.vX[x] = newVal
            }
            InstructionType.LD -> {
                // Set I = nnn.
                val newVal = inst.matcher.getArgument(instVal, 'n')

                //println("I = $newVal")

                cpu.registers.i = newVal.toShort()
            }
            InstructionType.SEVB -> {
                // Skip next instruction if Vx = kk.
                val x = inst.matcher.getArgument(instVal, 'x')
                val compareVal = inst.matcher.getArgument(instVal, 'k')

                //println("v$x ?= $compareVal")

                if (cpu.registers.vX[x] == compareVal) {
                    pointer += 2
                }
            }
            InstructionType.SNEB -> {
                // Skip next instruction if Vx != kk.
                val x = inst.matcher.getArgument(instVal, 'x')
                val compareVal = inst.matcher.getArgument(instVal, 'k')

                //println("v$x ?!= $compareVal")

                if (cpu.registers.vX[x] != compareVal) {
                    pointer += 2
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
                // Set Vx = Vy.
                val x = inst.matcher.getArgument(instVal, 'x')
                val y = inst.matcher.getArgument(instVal, 'y')

                //println("v$x -= $vy, vF = borrow")

                val result = Math.abs(cpu.registers.vX[y] - cpu.registers.vX[x])
                cpu.registers.vX[0xF] = if (cpu.registers.vX[x] > cpu.registers.vX[y]) 1 else 0
                cpu.registers.vX[x] = result
            }
            InstructionType.CALL -> {
                // Call subroutine at nnn.
                val newPointer = inst.matcher.getArgument(instVal, 'n')

                //println("Calling subroutine at 0x${toHex(newPointer, 3)} from 0x${toHex(pointer, 3)}")

                cpu.stack.push(pointer)
                pointer = newPointer
            }
            InstructionType.RET -> {
                // Return from a subroutine.
                val newPointer = cpu.stack.pop()

                //println("Exiting subroutine at 0x${toHex(newPointer, 3)} to 0x${toHex(pointer, 3)}")

                pointer = newPointer
            }
            InstructionType.JP -> {
                // Jump to location nnn.
                val newPointer = inst.matcher.getArgument(instVal, 'n')

                //println("Jumping to 0x${toHex(newPointer, 3)} from 0x${toHex(pointer, 3)}")

                pointer = newPointer
            }
            InstructionType.RND -> {
                // Set Vx = random byte AND kk.
                val x = inst.matcher.getArgument(instVal, 'x')
                val andValue = inst.matcher.getArgument(instVal, 'k')
                val randomVal = cpu.random.nextInt(255)

                //println("Storing random value $randomVal AND $andValue in v$x")

                cpu.registers.vX[x] = randomVal and andValue

            }
            InstructionType.DRW -> {
                // Display n-byte sprite starting at memory location I at (Vx, Vy), set VF = collision.
                val x = cpu.registers.vX[inst.matcher.getArgument(instVal, 'x')]
                val y = cpu.registers.vX[inst.matcher.getArgument(instVal, 'y')]
                val size = inst.matcher.getArgument(instVal, 'n')

                //println("Draw of height $size at $x:$y")

                // Sprite is 8x5, with each bit corresponding to a X value (true/false, on or off XORed)
                val array = BooleanArray(size * 8)

                for ((index, byte) in (cpu.registers.i.toInt() .. cpu.registers.i + size - 1).withIndex()) {
                    for (i in 0..7) {
                        array[index * 8 + (7 - i)] = ((cpu.ram[byte].toInt() shr i) and 0x1) == 1
                    }
                }

                // Send off the request
                cpu.registers.vX[0xF] = if (chip.postDrawRequest(DrawRequest(x, y, 8, size, array))) 1 else 0

            }
            InstructionType.LDS -> {
                // Set I = location of (font) sprite for digit Vx.
                val x = cpu.registers.vX[inst.matcher.getArgument(instVal, 'x')]

                println("LDS: $x")

                cpu.registers.i = (fontsStart + x * 5).toShort()
            }
            InstructionType.ADDB -> {
                // Set Vx = Vx + kk.
                val x =      inst.matcher.getArgument(instVal, 'x')
                val newVal = inst.matcher.getArgument(instVal, 'k')

                //println("v$x += $newVal")


                cpu.registers.vX[x] += newVal
            }
            InstructionType.SKNP -> {
                // Skip next instruction if key with the value of Vx is not pressed.
                // TODO

                pointer += 2
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

                cpu.registers.i = (cpu.registers.i.toInt() + x).toShort()
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

                cpu.registers.vX[x] = cpu.registers.vX[x] and cpu.registers.vX[y]
            }
            InstructionType.ADDV -> {
                // Set Vx = Vx + Vy, set VF = carry.
                val x = inst.matcher.getArgument(instVal, 'x')
                val y = inst.matcher.getArgument(instVal, 'y')

                //println("v$x = v$x + v$y, vF = carry")

                val result = cpu.registers.vX[x] + cpu.registers.vX[y]

                cpu.registers.vX[x] = result and 0xFF
                cpu.registers.vX[0xF] = if (result > 255) 1 else 0
            }
            InstructionType.LDBC -> {
                // Store BCD representation of Vx in memory locations I, I+1, and I+2.
                val x = cpu.registers.vX[inst.matcher.getArgument(instVal, 'x')]

                cpu.ram[cpu.registers.i.toInt()] = Math.floor(x / 100.0).toByte()
                cpu.ram[cpu.registers.i.toInt() + 1] = Math.floor(x % 100 / 10.0).toByte()
                cpu.ram[cpu.registers.i.toInt() + 2] = (x % 10).toByte()
            }
            InstructionType.LDMR -> {
                // Read registers V0 through Vx from memory starting at location I.
                val x = inst.matcher.getArgument(instVal, 'x')

                //println("Copying memory from 0x${toHex(cpu.registers.i, 3)} to v0..v$x")

                for (vVal in 0..x) {
                    cpu.registers.vX[x] = cpu.ram[cpu.registers.i + vVal].toInt()
                }
            }
            else -> {
                error("Unhandled instruction: $inst")
            }
        }

        // Sleep a bit
        Thread.sleep(1)
    }
}
