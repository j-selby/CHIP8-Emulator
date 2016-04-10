package net.jselby.chip8

import org.testng.Assert
import org.testng.annotations.Test

/**
 * Test cases for the Interpreter.
 */
class InterpreterTest {
    @Test
    fun testASSERT_LD() {
        val interpreter = buildByteInterpreter(
                0xA0, 0x01, // Write 1 (SUCCESS) to I
                0xF9, 0x99  // Exit with I
        )

        assertInterpreterAssert(interpreter, 1)
    }

    @Test
    fun testJP() {
        // Jump to location nnn.
        val interpreter = buildByteInterpreter(
                0x12, 0x06, // Jump to 0x206

                0xA0, 0x02, // 0x202, Write 2 (FAILURE) to I
                0xF9, 0x99, // 0x204, Exit with I

                0xA0, 0x01, // 0x206, Write 1 (SUCCESS) to I
                0xF9, 0x99  // 0x208, Exit with I
        )
        assertInterpreterAssert(interpreter, 1)
    }

    @Test
    fun testCALL() {
        // Call subroutine at nnn.
        // RET is in its own function
        val interpreter = buildByteInterpreter(
                0x22, 0x06, // Call 0x206

                0xA0, 0x02, // 0x202, Write 2 (FAILURE) to I
                0xF9, 0x99, // 0x204, Exit with I

                0xA0, 0x01, // 0x206, Write 1 (SUCCESS) to I
                0xF9, 0x99  // 0x208, Exit with I
        )
        assertInterpreterAssert(interpreter, 1)
    }


    @Test
    fun testRET() {
        // Return from a subroutine.
        val interpreter = buildByteInterpreter(
                0x22, 0x06, // Call 0x206

                0xA0, 0x01, // 0x202, Write 1 (SUCCESS) to I
                0xF9, 0x99, // 0x204, Exit with I

                0x00, 0xEE, // 0x206, Return from a subroutine.

                0xA0, 0x02, // 0x208, Write 2 (FAILURE) to I
                0xF9, 0x99  // 0x20A, Exit with I
        )
        assertInterpreterAssert(interpreter, 1)
    }

    @Test
    fun testLDB() {
        // Set Vx = kk.
        val interpreter = buildByteInterpreter(
                0x60, 0x00, // Write 0 to v0
                0x61, 0x01, // Write 1 to v0
                0x62, 0x02, // Write 2 to v0
                0x6F, 0x01, // Write 1 to vF

                0xA0, 0x01, // Write 1 (SUCCESS) to I
                0xF9, 0x99  // Exit with I
        )

        assertInterpreterAssert(interpreter, 1)

        Assert.assertEquals(interpreter.cpu.registers.vX[0], 0)
        Assert.assertEquals(interpreter.cpu.registers.vX[1], 1)
        Assert.assertEquals(interpreter.cpu.registers.vX[2], 2)
        Assert.assertEquals(interpreter.cpu.registers.vX[15], 1)
    }

    @Test
    fun testSEVB_WithSkip() {
        // Skip next instruction if Vx == kk.
        // Test match
        val interpreter = buildByteInterpreter(
                0xA0, 0x01, // Write 1 (SUCCESS) to I

                0x62, 0x05, // Write 5 to v2
                0x32, 0x05, // Skip next inst. if v2 equals 5

                0xA0, 0x02, // Write 2 (FAILURE) to I

                0xF9, 0x99  // Exit with I
        )
        assertInterpreterAssert(interpreter, 1)
    }


    @Test
    fun testSEVB_WithoutSkip() {
        // Skip next instruction if Vx == kk.
        // Test match
        val interpreter = buildByteInterpreter(
                0xA0, 0x02, // Write 2 (FAILURE) to I

                0x62, 0x0A, // Write 10 to v2
                0x32, 0x05, // Skip next inst. if v2 equals 5

                0xA0, 0x01, // Write 1 (SUCCESS) to I

                0xF9, 0x99  // Exit with I
        )
        assertInterpreterAssert(interpreter, 1)
    }

    @Test
    fun testSNEB_WithSkip() {
        // Skip next instruction if Vx != kk.
        val interpreter = buildByteInterpreter(
                0xA0, 0x01, // Write 1 (SUCCESS) to I

                0x62, 0x0A, // Write 10 to v2
                0x42, 0x05, // Skip next inst. if v2 does not equal 5

                0xA0, 0x02, // Write 2 (FAILURE) to I

                0xF9, 0x99  // Exit with I
        )
        assertInterpreterAssert(interpreter, 1)
    }

    @Test
    fun testSNEB_WithoutSkip() {
        // Skip next instruction if Vx != kk.
        val interpreter = buildByteInterpreter(
                0xA0, 0x02, // Write 2 (FAILURE) to I

                0x62, 0x05, // Write 5 to v2
                0x42, 0x05, // Skip next inst. if v2 does not equal 5

                0xA0, 0x01, // Write 1 (SUCCESS) to I

                0xF9, 0x99  // Exit with I
        )
        assertInterpreterAssert(interpreter, 1)
    }

    @Test
    fun testSEVV_WithSkip() {
        // Skip next instruction if Vx = Vy.
        val interpreter = buildByteInterpreter(
                0xA0, 0x01, // Write 1 (SUCCESS) to I

                0x62, 0x05, // Write 5 to v2
                0x65, 0x05, // Write 5 to v5
                0x52, 0x50, // Skip next inst. if v2 equals v5

                0xA0, 0x02, // Write 2 (FAILURE) to I

                0xF9, 0x99  // Exit with I
        )
        assertInterpreterAssert(interpreter, 1)
    }

    @Test
    fun testSEVV_WithoutSkip() {
        // Skip next instruction if Vx = Vy.
        val interpreter = buildByteInterpreter(
                0xA0, 0x02, // Write 2 (FAILURE) to I

                0x62, 0x05, // Write 5 to v2
                0x65, 0x0A, // Write 10 to v5
                0x52, 0x50, // Skip next inst. if v2 does not equal 5

                0xA0, 0x01, // Write 1 (SUCCESS) to I

                0xF9, 0x99  // Exit with I
        )
        assertInterpreterAssert(interpreter, 1)
    }

    @Test
    fun testADDB() {
        // Set Vx = Vx + kk.
        val interpreter = buildByteInterpreter(
                0x62, 0x05, // Write 5 to v2
                0x72, 0x02, // Add 2 to v2

                0xA0, 0x01, // Write 1 (SUCCESS) to I
                0xF9, 0x99  // Exit with I
        )
        assertInterpreterAssert(interpreter, 1)
        Assert.assertEquals(interpreter.cpu.registers.vX[2], 5 + 2)
    }


    @Test
    fun testADDB_Overflow() {
        // Set Vx = Vx + kk.
        val interpreter = buildByteInterpreter(
                0x62, 0x05, // Write 5 to v2
                0x72, 0xFF, // Add 255 to v2

                0xA0, 0x01, // Write 1 (SUCCESS) to I
                0xF9, 0x99  // Exit with I
        )
        assertInterpreterAssert(interpreter, 1)
        Assert.assertEquals(interpreter.cpu.registers.vX[2], (5 + 255) and 0xFF)
    }

    /**
     * Tests an interpreter to see if it returns a specified value
     */
    fun assertInterpreterAssert(interpreter: Interpreter, i : Int) {
        interpreter.start(useByteRom = true)
        Assert.assertTrue(interpreter.exitAsserted)
        Assert.assertEquals(interpreter.cpu.registers.i, i)
    }

    fun buildByteInterpreter(vararg rom : Int): Interpreter {
        // Convert int[] to byte[]
        val array = ByteArray(rom.size)
        rom.forEachIndexed { index, value -> array[index] = value.toByte() }

        // Build interpreter
        val interpreter = Interpreter(null)
        interpreter.byteRom = array

        return interpreter
    }
}