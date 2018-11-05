package pubg.radar.struct.cmd

import com.badlogic.gdx.math.Vector2
import pubg.radar.*
import pubg.radar.struct.*
import pubg.radar.struct.cmd.CMD.propertyBool
import pubg.radar.struct.cmd.CMD.propertyByte
import pubg.radar.struct.cmd.CMD.propertyFloat
import pubg.radar.struct.cmd.CMD.propertyInt
import pubg.radar.struct.cmd.CMD.propertyName
import pubg.radar.struct.cmd.CMD.propertyObject
import pubg.radar.struct.cmd.CMD.propertyString
import pubg.radar.struct.cmd.CMD.propertyVector

object GameStateCMD: GameListener {
  init {
    register(this)
  }
  
  override fun onGameOver() {
    SafetyZonePosition.setZero()
    SafetyZoneRadius = 0f
    SafetyZoneBeginPosition.setZero()
    SafetyZoneBeginRadius = 0f
    PoisonGasWarningPosition.setZero()
    PoisonGasWarningRadius = 0f
    RedZonePosition.setZero()
    RedZoneRadius = 0f
    TotalWarningDuration = 0f
    ElapsedWarningDuration = 0f
    TotalReleaseDuration = 0f
    ElapsedReleaseDuration = 0f
    NumJoinPlayers = 0
    NumAlivePlayers = 0
    NumAliveTeams = 0
    RemainingTime = 0
    MatchElapsedMinutes = 0
    NumTeams = 0
  }
  
  var TotalWarningDuration = 0f
  var ElapsedWarningDuration = 0f
  var RemainingTime = 0
  var MatchElapsedMinutes = 0
  val SafetyZonePosition = Vector2()
  var SafetyZoneRadius = 0f
  val SafetyZoneBeginPosition = Vector2()
  var SafetyZoneBeginRadius = 0f
  val PoisonGasWarningPosition = Vector2()
  var PoisonGasWarningRadius = 0f
  val RedZonePosition = Vector2()
  var RedZoneRadius = 0f
  var TotalReleaseDuration = 0f
  var ElapsedReleaseDuration = 0f
  var NumJoinPlayers = 0
  var NumAlivePlayers = 0
  var NumAliveTeams = 0
  var NumTeams = 0
  var isTeamMatch = false
  var MatchStartType = ""
  var isWarMode = false

  fun process(actor: Actor, bunch: Bunch, repObj: NetGuidCacheObject?, waitingHandle: Int, data: HashMap<String, Any?>): Boolean {
    with(bunch) {
      when (waitingHandle) {
        16 -> {
          val GameModeClass = propertyObject()
          val b = GameModeClass
        }
        17 -> {
          val SpectatorClass = propertyObject()
          val b = SpectatorClass
        }
        18 -> {
          val bReplicatedHasBegunPlay = propertyBool()
          val b = bReplicatedHasBegunPlay
        }
        19 -> {
          val ReplicatedWorldTimeSeconds = propertyFloat()
          val b = ReplicatedWorldTimeSeconds
        }
        20 -> {
          val MatchState = propertyName()
          val b = MatchState
        }
        21 -> {
          val ElapsedTime = propertyInt()
          val b = ElapsedTime
          //println("21 $b")

        }
        22 -> {
          val MatchId = propertyString()
          val b = MatchId
        }
        23 -> {
          val MatchShortGuid = propertyString()
          val b = MatchShortGuid
        }
        24 -> propertyBool()//bIsCustomGame
        25 -> propertyBool() //bIsWinnerZombieTeam
        26 -> {
          val NumTeams = propertyInt()
          val b = NumTeams
        }
        27 -> { // At Spawn Spot
          RemainingTime = propertyInt()
          //println("27 $RemainingTime")
        }
        28 -> {
          MatchElapsedMinutes = propertyInt()
        }
        29 -> {
          val bTimerPaused = propertyBool()
          val b = bTimerPaused
        }
        30 -> {
          val bShowLastCircleMark = propertyBool()
        }
        31 -> {
          val bCanShowLastCircleMark = propertyBool()
        }
        32 -> {
          NumJoinPlayers = propertyInt()
        }
        33 -> {
          NumAlivePlayers = propertyInt()
        }
        34 -> {
          val NumAliveZombiePlayers = propertyInt()
          val b = NumAliveZombiePlayers
        }
        35 -> {
          NumAliveTeams = propertyInt()
        }
        36 -> {
          val NumStartPlayers = propertyInt()
          val b = NumStartPlayers
        }
        37 -> {
          val NumStartTeams = propertyInt()
          val b = NumStartTeams
        }
        38 -> {
          val pos = propertyVector()
          SafetyZonePosition.set(pos.x, pos.y)
        }
        39 -> {
          SafetyZoneRadius = propertyFloat()
        }
        40 -> {
          val pos = propertyVector()
          PoisonGasWarningPosition.set(pos.x, pos.y)
        }
        41 -> {
          PoisonGasWarningRadius = propertyFloat()
        }
        42 -> {
          val pos = propertyVector()
          RedZonePosition.set(pos.x, pos.y)
          
          val b = RedZonePosition
        }
        43 -> {
          RedZoneRadius = propertyFloat()
          val b = RedZoneRadius
        }
        44 -> {
          TotalReleaseDuration = propertyFloat()
          val b = TotalReleaseDuration
          //println("44 $b")
        }
        45 -> {
          ElapsedReleaseDuration = propertyFloat()
          val b = ElapsedReleaseDuration
          //println("45 $b")
        }
        46 -> {
          TotalWarningDuration = propertyFloat()
          //println("46 $b")
        }
        47 -> {
          ElapsedWarningDuration = propertyFloat()
          //println("47 $ElapsedWarningDuration")
        }
        48 -> {
          val bIsGasRelease = propertyBool()
        }
        49 -> {
          val bIsTeamMatch = propertyBool()
          isTeamMatch = bIsTeamMatch
        }
        50 -> {
          val bIsZombieMode = propertyBool()
        }
        51 -> {
          val pos = propertyVector()
          SafetyZoneBeginPosition.set(pos.x, pos.y)
        }
        52 -> {
          SafetyZoneBeginRadius = propertyFloat()
        }
        53 -> {
          val result = propertyByte()
          MatchStartType = result.toString()
        }
        54 -> {
          val bShowAircraftRoute = propertyBool()
          //println("GameStateCMD 59: $bShowAircraftRoute")
        }
        55 -> {
          val bIsWarMode = propertyBool()
          isWarMode = bIsWarMode
          //println("GameStateCMD 60: $bIsWarMode")
        }
        else -> return false
      }
      return true
    }
  }
}