package net.jselby.chip8.decoder

/**
 * Patterns for CHIP-8 instructions.
 */
enum class InstructionType(val pattern : String, val description : String) {
    SYS ("0nnn", "Jump to RCA 1802 code at nnn. Unsupported."),
    CLS ("00E0", "Clear the display."),
    RET ("00EE", "Return from a subroutine."),
    JP  ("1nnn", "Jump to location nnn."),
    CALL("2nnn", "Call subroutine at nnn."),
    SEVB("3xkk", "Skip next instruction if Vx = kk."),
    SNEB("4xkk", "Skip next instruction if Vx != kk."),
    SEVV("5xy0", "Skip next instruction if Vx = Vy."),
    LDB ("6xkk", "Set Vx = kk."),
    ADDB("7xkk", "Set Vx = Vx + kk."),
    LDV ("8xy0", "Set Vx = Vy."),
    OR  ("8xy1", "Set Vx = Vx OR Vy."),
    AND ("8xy2", "Set Vx = Vx AND Vy."),
    XOR ("8xy3", "Set Vx = Vx XOR Vy."),
    ADDV("8xy4", "Set Vx = Vx + Vy, set VF = carry."),
    SUB ("8xy5", "Set Vx = Vx - Vy, set VF = NOT borrow."),
    SHR ("8xy6", "Set Vx = Vx SHR 1."),
    SUBN("8xy7", "Set Vx = Vy - Vx, set VF = NOT borrow."),
    SHL ("8xyE", "Set Vx = Vx SHL 1."),
    SNEV("9xy0", "Skip next instruction if Vx != Vy."),
    LD  ("Annn", "Set I = nnn."),
    JPV ("Bnnn", "Jump to location nnn + V0."),
    RND ("Cxkk", "Set Vx = random byte AND kk."),
    DRW ("Dxyn", "Display n-byte sprite starting at memory location I at (Vx, Vy), set VF = collision."),
    SKP ("Ex9E", "Skip next instruction if key with the value of Vx is pressed."),
    SKNP("ExA1", "Skip next instruction if key with the value of Vx is not pressed."),
    LDVD("Fx07", "Set Vx = delay timer value."),
    LDKP("Fx0A", "Wait for a key press, store the value of the key in Vx."),
    LDDV("Fx15", "Set delay timer = Vx."),
    LDDS("Fx18", "Set sound timer = Vx."),
    ADDI("Fx1E", "Set I = I + Vx."),
    LDS ("Fx29", "Set I = location of sprite for digit Vx."),
    LDBC("Fx33", "Store BCD representation of Vx in memory locations I, I+1, and I+2."),
    LDMW("Fx55", "Store registers V0 through Vx in memory starting at location I."),
    LDMR("Fx65", "Read registers V0 through Vx from memory starting at location I."),

    // Unofficial commands
    ASSERT("F999", "Returns from the program prematurely, with a status code in I.");

    val matcher : InstructionMatcher

    init {
        assert(pattern.length == 4)
        matcher = InstructionMatcher(pattern)
    }

    override fun toString() = "$name(\"$pattern\")"
}