package net.jselby.chip8

import javafx.animation.AnimationTimer
import javafx.application.Application
import javafx.application.Platform
import javafx.event.EventType
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.control.*
import javafx.scene.image.WritableImage
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.FileChooser
import javafx.stage.Modality
import javafx.stage.Stage
import net.jselby.chip8.decoder.decodeInstruction
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread

/**
 * A Chip8 emulator, written using Kotlin/JavaFX.
 */
class Chip8 : Application() {
    val screenScale = 8.0

    // Game screen
    private val xResolution = 64.0 * screenScale
    private val yResolution = 32.0 * screenScale

    private val pixels = BooleanArray(64 * 32)
    private var img = WritableImage(xResolution.toInt(), yResolution.toInt())

    val interpreter = Interpreter(this)
    private var interpreterThread : Thread? = null

    // JavaFX
    private lateinit var canvasRenderer: GraphicsContext
    private lateinit var disPane: TextArea
    var isVisible = true
        private set

    // Keys
    val keyMap = HashMap<KeyCode, Int>()
    val keysPressed = HashMap<Int, Boolean>()
    var future : CompletableFuture<Int>? = null

    // Debugging
    var sendDebuggingLine = false
    var debuggingLine = 0

    override fun start(stage: Stage) {
        println("CHIP-8 Emulator")

        // Init keymap
        keyMap.put(KeyCode.DIGIT1, 0x1); // 1
        keyMap.put(KeyCode.DIGIT2, 0x2); // 2
        keyMap.put(KeyCode.DIGIT3, 0x3); // 3
        keyMap.put(KeyCode.DIGIT4, 0xC); // C
        keyMap.put(KeyCode.Q,      0x4); // 4
        keyMap.put(KeyCode.W,      0x5); // 5
        keyMap.put(KeyCode.E,      0x6); // 6
        keyMap.put(KeyCode.R,      0xD); // D
        keyMap.put(KeyCode.A,      0x7); // 7
        keyMap.put(KeyCode.S,      0x8); // 8
        keyMap.put(KeyCode.D,      0x9); // 9
        keyMap.put(KeyCode.F,      0xE); // E
        keyMap.put(KeyCode.Z,      0xA); // A
        keyMap.put(KeyCode.X,      0x0); // 0
        keyMap.put(KeyCode.C,      0xB); // B
        keyMap.put(KeyCode.V,      0xF); // F

        // Set default key states
        for (value in keyMap.values) {
            keysPressed.put(value, false)
        }


        stage.title = "CHIPy"
        stage.isResizable = false

        // Build menubar
        val fileMenu    = Menu("File")
        val optionsMenu = Menu("Options")
        val helpMenu    = Menu("Debug")

        val menuBar = MenuBar()
        menuBar.menus.addAll(fileMenu, optionsMenu, helpMenu)

        // Build renderer
        val canvas = Canvas(xResolution, yResolution)
        canvasRenderer = canvas.graphicsContext2D
        canvasRenderer.fill = Color.BLACK
        canvasRenderer.fillRect(0.0, 0.0, xResolution, yResolution)

        // Build screen
        val root = VBox()
        root.children.addAll(menuBar, canvas)

        //stage.width = xResolution
        //println(menuBar.height)
        //stage.height = yResolution + menuBar.height

        stage.scene = Scene(root)

        // Build disassembler view
        disPane = TextArea()
        disPane.style = "-fx-font-family: monospace; -fx-highlight-fill: white; -fx-highlight-text-fill: red;"
        disPane.isEditable = false

        val disStage = Stage()
        disStage.scene = Scene(disPane, 480.0, 360.0)
        disStage.initModality(Modality.WINDOW_MODAL)
        disStage.title = "Disassembler"
        disStage.minWidth = 480.0
        disStage.minHeight = 360.0

        // Build buttons
        val disMenuItem = MenuItem("Disassembler")
        disMenuItem.setOnAction {
            disStage.show()
        }
        helpMenu.items.add(disMenuItem)

        val keepRAMROM = MenuItem("Reset CPU (keep RAM+ROM)")
        keepRAMROM.setOnAction {
            startInterpreter(false, false)
        }
        helpMenu.items.add(keepRAMROM)

        val resetScreen = MenuItem("Reset Screen")
        resetScreen.setOnAction {
            clearScreen()
        }
        helpMenu.items.add(resetScreen)

        val loadROM = MenuItem("Load ROM...")
        loadROM.setOnAction {
            val fileChooser = FileChooser()
            fileChooser.title = "Select CHIP-8 ROM"
            val file = fileChooser.showOpenDialog(stage)

            if (file != null && file.exists() && file.canRead()) {
                stopInterpreter()
                interpreter.rom = file.toString()
                startInterpreter()
            }
        }
        fileMenu.items.add(loadROM)

        val reloadRAM = MenuItem("Reload ROM")
        reloadRAM.setOnAction {
            startInterpreter(true, true)
        }
        fileMenu.items.add(reloadRAM)

        // Add callbacks
        stage.setOnHidden {
            isVisible = false
        }

        stage.addEventHandler(EventType.ROOT, {
            if (it is KeyEvent) {
                if (keyMap.containsKey(it.code)) {
                    keysPressed.put(keyMap[it.code]!!,
                            it.eventType == KeyEvent.KEY_PRESSED)

                    val test = future
                    if (test != null && it.eventType == KeyEvent.KEY_PRESSED) {
                        // Unblock other thread
                        future = null
                        test.complete(keyMap[it.code]!!)
                    }
                }
            }
        })

        // Build dummy logo
        postDrawRequest(DrawRequest(5, 5, 15, 5,
                booleanArrayOf(
                        false, true,  true,  false, true, false, true, false, true,  true, true,  false, true, true,  false,
                        true,  false, false, false, true, false, true, false, false, true, false, false, true, false, true,
                        true,  false, false, false, true, true,  true, false, false, true, false, false, true, true,  false,
                        true,  false, false, false, true, false, true, false, false, true, false, false, true, false, false,
                        false, true,  true,  false, true, false, true, false, true,  true, true , false, true, false, false
                )
        ))

        stage.show()

        // Start renderer thread
        AnimationCaller(this).start()

        println("Ready to begin emulation!")

        interpreter.rom = "roms/PONG2"
        startInterpreter()

    }

    fun startInterpreter(reset : Boolean = true, loadRom : Boolean = true) {
        stopInterpreter()

        isVisible = true

        interpreterThread = thread {
            println("Interpreter started.")
            interpreter.start(reset, loadRom)
        }

        interpreterThread!!.name = "CHIP-8 Emulation Thread"
    }

    fun stopInterpreter() {
        if (interpreterThread != null) {
            isVisible = false // Signal other thread of termination
            future?.complete(0)


            interpreterThread!!.join()
            interpreterThread = null

            println("Interpreter stopped.")
        }
    }

    fun render() {
        // Render!
        canvasRenderer.drawImage(img, 0.0, 0.0)
        //setInstLine(line)
    }

    fun postDrawRequest(request: DrawRequest) : Boolean {
        var response = false

        // Write to main image
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

                    if (pixels.size <= globalIndex) {
                        println("Painting $renderX $renderY with $x and $y base from $localIndex : $globalIndex")
                    }
                    // XOR the current image
                    pixels[globalIndex] = !pixels[globalIndex]

                    //println("Painting $renderX $renderY with $x and $y base from $localIndex : $globalIndex")

                    val color = if (pixels[globalIndex]) Color.WHITE else Color.BLACK

                    for (drawX in (renderX * screenScale).toInt() .. ((renderX + 1) * screenScale).toInt()) {
                        for (drawY in (renderY * screenScale).toInt() .. ((renderY + 1) * screenScale).toInt()) {
                            if (drawX < xResolution && drawY < yResolution) {
                                img.pixelWriter.setColor(drawX, drawY, color)
                            }
                        }
                    }

                    if (!response && !pixels[globalIndex]) {
                        response = true
                    }
                }
            }
        }

        return response
    }

    fun clearScreen() {
        img = WritableImage(xResolution.toInt(), yResolution.toInt())
        pixels.fill(false)
        Platform.runLater {
            canvasRenderer.fill = Color.BLACK
            canvasRenderer.fillRect(0.0, 0.0, xResolution, yResolution)
            render()
        }
    }

    fun sendRAM(ram : ByteArray) {
        Platform.runLater {
            var contents = ""

            for (i in 0 .. ram.size - 1 step 2) {
                var line = ""

                val highInst = ram[i].toInt() and 0xFF
                val lowInst = ram[i + 1].toInt() and 0xFF
                val instVal = (highInst shl 8) + lowInst
                val inst = decodeInstruction(instVal)

                line += "0x${toHex(i)}  ${inst?.name}"
                line += " ".repeat(13 - line.length)

                // Dissect parameters
                if (inst != null) {
                    var lastChar = '0'
                    for (charI in 0..inst.pattern.length - 1) {
                        if (inst.pattern[charI] == lastChar) {
                            continue
                        }

                        lastChar = inst.pattern[charI]

                        if (lastChar.isLetter() && !(65..70).contains(lastChar.toInt())) {
                            // This is an argument!
                            line += "$lastChar=${inst.matcher.getArgument(instVal, lastChar)} "
                        }
                    }
                }

                line += " ".repeat(30 - line.length) + "; ${inst?.description}\n"

                contents += line
            }

            disPane.text = contents
            disPane.requestLayout()
        }
    }

    fun setInstLine(line : Int) {
        debuggingLine = line

        if (!sendDebuggingLine) {
            sendDebuggingLine = true
            Platform.runLater {
                val start = disPane.text.indexOf("0x${toHex(debuggingLine, 4)}")
                if (start < 0 || start > disPane.text.length) {
                    println("WARNING: Invalid instruction for disassembler: $line")
                    return@runLater
                }

                val text = disPane.text.substring(start..disPane.text.indexOf('\n', start)).split(" ")[0]

                disPane.selectRange(start, start + text.length)

                disPane.scrollLeft = 0.0

                sendDebuggingLine = false
            }
        }
    }

    fun beep() {
        Platform.runLater {
            //Toolkit.getDefaultToolkit().beep();
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Application.launch(Chip8::class.java, *args)
        }
    }
}

class AnimationCaller(val chip8: Chip8) : AnimationTimer() {
    override fun handle(now: Long) {
        chip8.render()
    }
}