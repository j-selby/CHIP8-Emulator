package net.jselby.chip8.interpreter.decoder

/**
 * Decodes a raw CHIP8 instruction into a object representation.
 *
 * b1 = first byte in instruction
 * b2 = second byte in instruction
 */
fun decodeInstruction(instr : Int): InstructionType? {
    if (instr == 0) {
        return null
}

    var instruction : InstructionType? = null
    var maxMatch = 0

    for (testInstr in InstructionType.values()) {
        var result = testInstr.matcher.matches(instr)

        if (result > maxMatch) {
            instruction = testInstr
            maxMatch = result
        }
    }

    return instruction
}
