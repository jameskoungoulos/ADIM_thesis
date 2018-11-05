package pubg.radar

import pubg.radar.deserializer.replaySpeed
import pubg.radar.deserializer.pcapFile
import pubg.radar.sniffer.Sniffer
import pubg.radar.ui.GLMap

const val gridWidth = 813000f
const val mapWidth = 819200f
const val mapWidthCropped = 8192

var gameStarted = false
var isErangel = true
var mapSelector = "Erangel"

interface GameListener {
  fun onGameStart() {}
  fun onGameOver()
}

private val gameListeners = ArrayList<GameListener>()

fun register(gameListener: GameListener) {
  gameListeners.add(gameListener)
}

fun deregister(gameListener: GameListener) {
  gameListeners.remove(gameListener)
}

fun gameStart() {
  println("New Game on")
  
  gameStarted = true
  gameListeners.forEach { it.onGameStart() }
}

fun gameOver() {
  gameStarted = false
  gameListeners.forEach { it.onGameOver() }
}

lateinit var Args: Array<String>
fun main(args: Array<String>) {
  var filename = ""
  if (args.size < 1) {
    filename = "MIRAMAR_TPP_GROUP_WIN"
  } else {filename = args.get(0)}
  Sniffer.sniffLocationOffline(filename)
  val ui = GLMap()
  ui.show(filename)
}