package net.jselby.chip8.interpreter.decoder

/**
 * A instruction pattern, intended to be able
 * to match instructions based upon a String
 * representation of it, and provide relevant
 * values.
 */
class InstructionMatcher(private val pattern : String) {
    fun matches(instr : Int): Int {
        var matches = 0

        // Check constants within the pattern
        for ((index, character) in pattern.withIndex()) {
            if (character.isLetterOrDigit()) {
                // Check this.
                if (character.isLetter() &&
                        !(65..70).contains(character.toInt())) {
                    continue
                }

                val value = if (character.isLetter()) Integer.decode("0x$character") else Integer.decode("$character")

                // Select this index in the instruction
                val instrValue = instr shr (4 * (3 - index)) and 0xF

                if (value != instrValue) {
                    return 0
                }

                matches++
            }
        }

        return matches
    }



    fun getArgument(instr: Int, selector: Char): Int {
        // Find range in which this selector resides
        var argument = 0

        for ((index, character) in pattern.withIndex()) {
            if (selector == character) {
                argument = (argument shl 4) + (instr shr (4 * (3 - index)) and 0xF)
            }
        }

        return argument
    }
}