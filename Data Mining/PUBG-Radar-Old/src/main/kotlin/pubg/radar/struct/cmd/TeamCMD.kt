package wumo.pubg.struct.cmd

import pubg.radar.*
import pubg.radar.struct.*
import pubg.radar.struct.cmd.ActorCMD.actorWithPlayerState
import pubg.radar.struct.cmd.CMD.propertyString
import pubg.radar.struct.cmd.CMD.propertyVector100
import pubg.radar.struct.cmd.PlayerStateCMD.playerNames
import java.util.concurrent.ConcurrentHashMap

object TeamCMD: GameListener {
  val team = ConcurrentHashMap<String, String>()
  
  init {
    register(this)
  }
  
  override fun onGameOver() {
    team.clear()
  }
  
  fun process(actor: Actor, bunch: Bunch, waitingHandle: Int): Boolean {
    with(bunch) {
      //      println("${actor.netGUID} $waitingHandle")
      when (waitingHandle) {
        5 -> {
          val (netGUID, obj) = readObject()
          actor.owner = if (netGUID.isValid()) netGUID else null
          bugln { " owner: [$netGUID] $obj ---------> beOwned:$actor" }
        }
        16 -> {
          val playerLocation = propertyVector100()
        }
        17 -> {
          val playerRotation = readRotationShort()
        }
        18 -> {
          val playerName = propertyString()
          team[playerName] = playerName
					playerNames[actor.netGUID] = playerName
          val playerStateGUID = actorWithPlayerState[actor.netGUID]
          if (playerStateGUID != null) 
            playerNames[playerStateGUID] = playerName
        }
        else -> return false
      }
      return true
    }
  }
}