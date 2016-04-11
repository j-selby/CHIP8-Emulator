package net.jselby.chip8.frontend

import com.sun.javafx.tk.Toolkit
import javafx.animation.AnimationTimer
import javafx.application.Application
import javafx.application.Platform
import javafx.event.EventType
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.FlowPane
import javafx.scene.paint.Color
import javafx.scene.text.Text
import javafx.stage.FileChooser
import javafx.stage.Modality
import javafx.stage.Stage
import net.jselby.chip8.interpreter.DrawRequest
import net.jselby.chip8.interpreter.Interpreter
import net.jselby.chip8.interpreter.decoder.decodeInstruction
import net.jselby.chip8.interpreter.toHex
import net.jselby.chip8.ui.FrontendProvider
import java.io.File
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread

/**
 * A JavaFX frontend for the Chip-8 interpreter/emulator.
 */
class JavaFXFrontend : Application(), FrontendProvider {

    val screenScale = 16.0//12.0

    // Game screen
    private val xResolution = 64.0 //* screenScale
    private val yResolution = 32.0 //* screenScale

    private val pixels = BooleanArray(64 * 32)
    private val uiThreadPixels = BooleanArray(64 * 32)

    val interpreter = Interpreter(this)
    private var interpreterThread : Thread? = null

    // JavaFX
    private lateinit var canvas: Canvas
    private lateinit var canvasRenderer: GraphicsContext
    private lateinit var disPane: TextArea
    var isVisible = true
        private set

    // Keys
    val keyMap = HashMap<KeyCode, Int>()
    val keysPressed = HashMap<Int, Boolean>()
    var future : CompletableFuture<Int>? = null

    // Debugging
    var curDebuggingLine = 0
    var oldDebuggingLine = 0

    // Options
    var renderSpeed = 10
    var animationCaller = AnimationCaller(this)

    // Menu items dependent on ROM
    private lateinit var keepRAMROM: MenuItem
    private lateinit var reloadRAM: MenuItem

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
        stage.isResizable = false // AnimationTimer & draw calls + menubar seem to create lag when this is on. Sorry!

        // Build menubar
        val fileMenu    = Menu("File")
        val optionsMenu = Menu("Options")
        val helpMenu    = Menu("Debug")

        val menuBar = MenuBar()
        menuBar.menus.addAll(fileMenu, optionsMenu, helpMenu)

        // Build renderer
        canvas = Canvas(xResolution * screenScale, yResolution * screenScale)

        canvasRenderer = canvas.graphicsContext2D

        // Build screen
        val root = BorderPane()
        root.top = menuBar
        root.center = canvas

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

        // Build options menus
        // Set render speed...
        val rSPane = BorderPane()

        val rSTop = FlowPane()
        rSTop.padding = Insets(10.0, 10.0, 15.0, 10.0)
        val rSMiddle = FlowPane()
        rSMiddle.padding = Insets(0.0, 0.0, 0.0, 10.0)
        val rSBottom = FlowPane()
        rSBottom.padding = Insets(15.0, 10.0, 10.0, 10.0)

        val title = Text("New render speed? (in ms, 0 disables delay)")
        val slider = Slider(0.0, 10.0, renderSpeed.toDouble())
        slider.isShowTickLabels = true
        slider.majorTickUnit = 2.0
        slider.blockIncrement = 2.0
        slider.isSnapToTicks = true
        val setButton = Button("OK")

        rSTop.children.add(title)
        rSMiddle.children.add(slider)
        rSBottom.children.add(setButton)

        rSPane.top = rSTop
        rSPane.center = rSMiddle
        rSPane.bottom = rSBottom

        val rSStage = Stage()
        rSStage.scene = Scene(rSPane, 360.0, 120.0)
        rSStage.initModality(Modality.APPLICATION_MODAL)
        rSStage.isResizable = false
        rSStage.title = "Set render speed..."

        setButton.setOnAction {
            renderSpeed = slider.value.toInt()
            rSStage.close()
        }

        // Build buttons
        val disMenuItem = MenuItem("Disassembler")
        disMenuItem.setOnAction {
            disStage.show()
        }
        helpMenu.items.add(disMenuItem)

        keepRAMROM = MenuItem("Reset CPU (keep RAM+ROM)")
        keepRAMROM.setOnAction {
            startInterpreter(false, false)
        }
        keepRAMROM.isDisable = true
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
            fileChooser.initialDirectory = File(".")
            val file = fileChooser.showOpenDialog(stage)

            if (file != null && file.exists() && file.canRead()) {
                stopInterpreter()
                interpreter.rom = file.toString()
                startInterpreter()
            }
        }
        loadROM.isDisable = true
        fileMenu.items.add(loadROM)

        reloadRAM = MenuItem("Reload ROM")
        reloadRAM.isDisable = true
        reloadRAM.setOnAction {
            startInterpreter(true, true)
        }
        fileMenu.items.add(reloadRAM)

        fileMenu.items.add(SeparatorMenuItem())

        val exitButton = MenuItem("Exit")
        exitButton.setOnAction {
            isVisible = false
            Platform.exit()
        }
        fileMenu.items.add(exitButton)

        val setRenderSpeed = MenuItem("Set Render Speed...")
        setRenderSpeed.setOnAction {
            rSStage.showAndWait()
        }
        optionsMenu.items.add(setRenderSpeed)

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
        postDrawRequest(DrawRequest(5, 5, 23, 5,
                booleanArrayOf(
                        false, true, true, false, true, false, true, false, true, true, true, false, true, true, false, false, false, false, false, false, false, true, false,
                        true, false, false, false, true, false, true, false, false, true, false, false, true, false, true, false, false, false, false, false, true, false, true,
                        true, false, false, false, true, true, true, false, false, true, false, false, true, true, false, false, true, true, true, false, false, true, false,
                        true, false, false, false, true, false, true, false, false, true, false, false, true, false, false, false, false, false, false, false, true, false, true,
                        false, true, true, false, true, false, true, false, true, true, true, false, true, false, false, false, false, false, false, false, false, true, false
                )
        ))

        stage.show()

        // Fix weird border in JavaFX
        stage.width -= 10
        stage.height -= 10

        // Render once
        render()

        loadROM.isDisable = false

        println("Ready to begin emulation!")
    }

    fun startInterpreter(reset : Boolean = true, loadRom : Boolean = true) {
        stopInterpreter()

        isVisible = true

        interpreterThread = thread {
            println("Interpreter started.")
            interpreter.start(reset, loadRom)
        }

        interpreterThread!!.name = "CHIP-8 Emulation Thread"

        // Update menu
        reloadRAM.isDisable = false
        keepRAMROM.isDisable = false
        animationCaller.start()
    }

    fun stopInterpreter() {
        if (interpreterThread != null) {
            isVisible = false // Signal other thread of termination
            future?.complete(0)


            interpreterThread!!.join()
            interpreterThread = null

            println("Interpreter stopped.")

            // Update menu
            reloadRAM.isDisable = true
            keepRAMROM.isDisable = true
            animationCaller.stop()
        }
    }

    fun render() {
        // Render!
        System.arraycopy(pixels, 0, uiThreadPixels, 0, uiThreadPixels.size)

        Toolkit.getToolkit().checkFxUserThread()

        val xScale = canvas.width / xResolution
        val yScale = canvas.height / yResolution
        canvasRenderer.scale(xScale, yScale)
        for (y in 0 .. yResolution.toInt() - 1) {
            for (x in 0 .. xResolution.toInt() - 1) {
                val localIndex = y * 64 + x
                canvasRenderer.fill = if (uiThreadPixels[localIndex]) Color.WHITE else Color.BLACK
                canvasRenderer.fillRect(x.toDouble(), y.toDouble(), 1.0, 1.0)
            }
        }
        canvasRenderer.scale(1 / xScale, 1 / yScale)


        // Update CPU timers
        val myInterpreter = interpreter
        if (interpreterThread != null) {
            if (myInterpreter.cpu.registers.delayTimer > 0) {
                myInterpreter.cpu.registers.delayTimer--
            }

            if (myInterpreter.cpu.registers.soundTimer > 0) {
                beep()
                myInterpreter.cpu.registers.soundTimer--
            }
        }

        val curDebuggingLine = curDebuggingLine
        if (curDebuggingLine != oldDebuggingLine) {
            val start = disPane.text.indexOf("0x${toHex(curDebuggingLine, 4)}")
            if (start < 0 || start > disPane.text.length) {
                //println("WARNING: Invalid instruction for disassembler: $line")
                return
            }

            val text = disPane.text.substring(start..disPane.text.indexOf('\n', start)).split(" ")[0]

            disPane.selectRange(start, start + text.length)

            disPane.scrollLeft = 0.0

            oldDebuggingLine = curDebuggingLine
        }

    }

    override fun postDrawRequest(request: DrawRequest) : Boolean {
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

                    if (pixels.size <= globalIndex || globalIndex < 0) {
                        println("Painting $renderX $renderY with $x and $y base from $localIndex : $globalIndex")
                        continue;
                    }
                    // XOR the current image
                    pixels[globalIndex] = !pixels[globalIndex]

                    //println("Painting $renderX $renderY with $x and $y base from $localIndex : $globalIndex")

                    if (!response && !pixels[globalIndex]) {
                        response = true
                    }
                }
            }
        }

        Thread.sleep(renderSpeed.toLong())

        return response
    }

    override fun clearScreen() {
        pixels.fill(false)
        Platform.runLater {
            canvasRenderer.fill = Color.BLACK
            canvasRenderer.fillRect(0.0, 0.0, xResolution, yResolution)
            render()
        }
    }

    override fun beforeStartCallback() {
        val ram = interpreter.cpu.ram

        Platform.runLater {
            var contents = ""

            for (i in 0 .. ram.size - 1 step 2) {
                var line = ""

                val highInst = ram[i].toInt() and 0xFF
                val lowInst = ram[i + 1].toInt() and 0xFF
                val instVal = (highInst shl 8) + lowInst
                val inst = decodeInstruction(instVal)

                line += "0x${toHex(i)}  ${inst?.name ?: "${toHex(highInst, 2)} ${toHex(lowInst, 2)}"}"
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

                line += " ".repeat(30 - line.length)

                if (inst != null) {
                    line += "; ${inst.description}\n"
                } else {
                    line += "\n"
                }

                contents += line
            }

            disPane.text = contents
            disPane.requestLayout()
        }
    }

    override fun setDebuggingLine(line : Int) {
        curDebuggingLine = line
    }

    override fun beep() {
        Platform.runLater {
            //Toolkit.getDefaultToolkit().beep();
        }
    }

    override fun hasTimerUpdater() = true

    override fun doContinue() = isVisible

    override fun isKeyPressed(key: Int) = keysPressed[key]!!

    override fun postKeyPressedFuture(future: CompletableFuture<Int>) {
        this.future = future
    }
}

class AnimationCaller(val chip8: JavaFXFrontend) : AnimationTimer() {
    override fun handle(now: Long) {
        chip8.render()
    }
}