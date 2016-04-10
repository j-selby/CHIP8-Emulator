package net.jselby.chip8

import javafx.animation.AnimationTimer
import javafx.application.Application
import javafx.application.Platform
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.control.*
import javafx.scene.image.WritableImage
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.Modality
import javafx.stage.Stage
import net.jselby.chip8.decoder.decodeInstruction
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread

/**
 * A Chip8 emulator, written using Kotlin/JavaFX.
 */
class Chip8 : Application() {
    val screenScale = 8.0

    private val xResolution = 64.0 * screenScale
    private val yResolution = 32.0 * screenScale

    private val pixels = BooleanArray(64 * 32)

    //private val renderRequests = ConcurrentLinkedQueue<DrawRequest>()
    private var renderRequestSent = false

    private lateinit var canvasRenderer: GraphicsContext

    private lateinit var disPane: TextArea

    var isVisible = true
        private set

    val keyMap = HashMap<Char, Int>()

    private val img = WritableImage(xResolution.toInt(), yResolution.toInt())

    override fun start(stage: Stage) {
        println("CHIP-8 Emulator")

        // Init keymap


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
        disPane.style = "-fx-font-family: monospace"

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

        // Add callbacks
        stage.setOnHidden {
            isVisible = false
        }

        stage.show()

        // Start renderer thread
        AnimationCaller(this).start()

        println("Ready to begin emulation!")

        thread {
            start(this)
        }.name = "CHIP-8 Emulation Thread"
    }

    fun render() {
        // Render!
        canvasRenderer.drawImage(img, 0.0, 0.0)
    }

    fun postDrawRequest(request: DrawRequest) : Boolean {
        var response = false

        // Write to main image
        for (y in 0..request.height - 1) {
            val renderY = request.baseY + y
            for (x in 0..request.width - 1) {
                val localIndex = y * request.width + x

                if (request.pixels[localIndex]) {
                    val renderX = request.baseX + x

                    val globalIndex = (renderY * 64 + renderX).toInt()

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
        Platform.runLater {
            disPane.scrollTop = line * 8.0
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