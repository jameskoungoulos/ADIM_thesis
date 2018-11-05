package pubg.radar

import pubg.radar.sniffer.Sniffer.Companion.sniffLocationOffline
import pubg.radar.ui.GLMap

fun main(args: Array<String>) {
  val ui = GLMap()
  ui.show(args.get(0))
}