package pubg.radar.ui

import com.badlogic.gdx.*
import com.badlogic.gdx.Input.Buttons.RIGHT
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.backends.lwjgl3.*
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.Color.*
import com.badlogic.gdx.graphics.g2d.*
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.*
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.*
import com.badlogic.gdx.math.*
import com.badlogic.gdx.math.Vector3
import pubg.radar.*
import pubg.radar.deserializer.channel.ActorChannel.Companion.actors
import pubg.radar.deserializer.channel.ActorChannel.Companion.airDropLocation
import pubg.radar.deserializer.channel.ActorChannel.Companion.corpseLocation
import pubg.radar.deserializer.channel.ActorChannel.Companion.droppedItemLocation
import pubg.radar.deserializer.channel.ActorChannel.Companion.visualActors
import pubg.radar.http.PlayerProfile.Companion.completedPlayerInfo
import pubg.radar.http.PlayerProfile.Companion.pendingPlayerInfo
import pubg.radar.http.PlayerProfile.Companion.query
import pubg.radar.sniffer.Sniffer.Companion.localAddr
import pubg.radar.sniffer.Sniffer.Companion.preDirection
import pubg.radar.sniffer.Sniffer.Companion.preSelfCoords
import pubg.radar.sniffer.Sniffer.Companion.selfCoords
import pubg.radar.sniffer.Sniffer.Companion.coordChange
import pubg.radar.sniffer.Sniffer.Companion.sniffOption
import pubg.radar.struct.*
import pubg.radar.struct.Archetype.*
import pubg.radar.struct.Archetype.Plane
import pubg.radar.struct.cmd.ActorCMD.actorWithPlayerState
import pubg.radar.struct.cmd.ActorCMD.playerStateToActor
import pubg.radar.struct.cmd.GameStateCMD.ElapsedWarningDuration
import pubg.radar.struct.cmd.GameStateCMD.MatchElapsedMinutes
import pubg.radar.struct.cmd.GameStateCMD.NumAlivePlayers
import pubg.radar.struct.cmd.GameStateCMD.NumAliveTeams
import pubg.radar.struct.cmd.GameStateCMD.PoisonGasWarningPosition
import pubg.radar.struct.cmd.GameStateCMD.PoisonGasWarningRadius
import pubg.radar.struct.cmd.GameStateCMD.RedZonePosition
import pubg.radar.struct.cmd.GameStateCMD.RedZoneRadius
import pubg.radar.struct.cmd.GameStateCMD.SafetyZonePosition
import pubg.radar.struct.cmd.GameStateCMD.SafetyZoneRadius
import pubg.radar.struct.cmd.GameStateCMD.TotalWarningDuration
import pubg.radar.struct.cmd.PlayerStateCMD.playerPings
import pubg.radar.struct.cmd.PlayerStateCMD.attacks
import pubg.radar.struct.cmd.PlayerStateCMD.playerArmor
import pubg.radar.struct.cmd.PlayerStateCMD.playerHead
import pubg.radar.struct.cmd.PlayerStateCMD.playerBack
import pubg.radar.struct.cmd.PlayerStateCMD.playerNames
import pubg.radar.struct.cmd.PlayerStateCMD.playerNumKills
import pubg.radar.struct.cmd.PlayerStateCMD.selfID
import pubg.radar.struct.cmd.PlayerStateCMD.teamNumbers
import pubg.radar.util.tuple4
import wumo.pubg.struct.cmd.TeamCMD.team
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.*
import java.io.File
import kotlin.system.exitProcess

typealias renderInfo = tuple4<Actor, Float, Float, Float>

fun Float.d(n: Int) = String.format("%.${n}f", this)
class GLMap: InputAdapter(), ApplicationListener, GameListener {
  companion object {
    operator fun Vector3.component1(): Float = x
    operator fun Vector3.component2(): Float = y
    operator fun Vector3.component3(): Float = z
    operator fun Vector2.component1(): Float = x
    operator fun Vector2.component2(): Float = y
    
    val spawnErangel = Vector2(795548.3f, 17385.875f)
    val spawnDesert = Vector2(78282f, 731746f)
  }
  
  init {
    register(this)
  }
  
  override fun onGameStart() {
    preSelfCoords.set(if (isErangel) spawnErangel else spawnDesert)
    selfCoords.set(preSelfCoords)
    preDirection.setZero()    
  }
  
  override fun onGameOver() {
    camera.zoom = 1 / 4f
    
    aimStartTime.clear()
    attackLineStartTime.clear()
    pinLocation.setZero()

    // sort the stats so that the timestamp is in ascending order
    val sortedStatsA = statsA.toSortedMap()
    val sortedStatsB = statsB.toSortedMap()
    writeDataFile(sortedStatsA, "A")
    writeDataFile(sortedStatsB, "B")
    exitProcess(0)
  }
  
  fun show(filename: String) {
    this.filename = filename
    val config = Lwjgl3ApplicationConfiguration()
    config.setTitle("${sniffOption.name}")
    config.useOpenGL3(false, 2, 1)
    // config.useOpenGL3(true, 3, 3)
    //config.setWindowedMode(600, 600)
    config.setResizable(true)
    config.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode())
    // config.setBackBufferConfig(8, 8, 8, 8, 32, 0, 8)
    config.setBackBufferConfig(8, 8, 8, 8, 32, 0, 4)
    config.setIdleFPS(60)
    Lwjgl3Application(this, config)
  }
  
  lateinit var spriteBatch: SpriteBatch
  lateinit var shapeRenderer: ShapeRenderer
  lateinit var mapErangel: Texture
  lateinit var mapMiramar: Texture
  lateinit var map: Texture
  lateinit var largeFont: BitmapFont
  lateinit var littleFont: BitmapFont
  lateinit var nameFont: BitmapFont
  lateinit var fontCamera: OrthographicCamera
  lateinit var camera: OrthographicCamera
  lateinit var alarmSound: Sound
  
  val layout = GlyphLayout()
  var windowWidth = initialWindowWidth
  var windowHeight = initialWindowWidth
  
  val aimStartTime = HashMap<NetworkGUID, Long>()
  val attackLineStartTime = LinkedList<Triple<NetworkGUID, NetworkGUID, Long>>()
  val pinLocation = Vector2()

  // data file name (same as pcap name)
  var filename = ""
  // Create Dictionaries
  val statsA = HashMap<Long, MutableList<Float>>()
  val statsB = HashMap<Long, MutableList<Float>>()

  fun Vector2.windowToMap() =
      Vector2(selfCoords.x + (x - windowWidth / 2.0f) * camera.zoom * windowToMapUnit,
              selfCoords.y + (y - windowHeight / 2.0f) * camera.zoom * windowToMapUnit)
  
  fun Vector2.mapToWindow() =
      Vector2((x - selfCoords.x) / (camera.zoom * windowToMapUnit) + windowWidth / 2.0f,
              (y - selfCoords.y) / (camera.zoom * windowToMapUnit) + windowHeight / 2.0f)
  
  override fun scrolled(amount: Int): Boolean {
    camera.zoom *= 1.1f.pow(amount)
    return true
  }
  
  override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
    if (button == RIGHT) {
      pinLocation.set(pinLocation.set(screenX.toFloat(), screenY.toFloat()).windowToMap())
      return true
    }
    return false
  }

  override fun create() {
    spriteBatch = SpriteBatch()
    shapeRenderer = ShapeRenderer()
    Gdx.input.inputProcessor = this;
    camera = OrthographicCamera(windowWidth, windowHeight)
    with(camera) {
      setToOrtho(true, windowWidth * windowToMapUnit, windowHeight * windowToMapUnit)
      zoom = 1 / 4f
      update()
      position.set(mapWidth / 2, mapWidth / 2, 0f)
      update()
    }
    
    fontCamera = OrthographicCamera(initialWindowWidth, initialWindowWidth)
    alarmSound = Gdx.audio.newSound(Gdx.files.internal("Alarm.wav"))
    mapErangel = Texture(Gdx.files.internal("Erangel.bmp"))
    mapMiramar = Texture(Gdx.files.internal("Miramar.bmp"))
    map = mapErangel
    
    val generator = FreeTypeFontGenerator(Gdx.files.internal("GOTHICB.TTF"))
    val param = FreeTypeFontParameter()
    param.size = 50
    param.characters = DEFAULT_CHARS
    param.color = RED
    largeFont = generator.generateFont(param)
    param.size = 20
    param.color = WHITE
    littleFont = generator.generateFont(param)
    param.color = BLACK
    param.size = 15
    nameFont = generator.generateFont(param)
    generator.dispose()
  }
  
  val dirUnitVector = Vector2(1f, 0f)

  // Value tables
  val attackTable: HashMap<String, Float> = hashMapOf("Gunfire" to 5f, "Personal Gunfire" to 20f, "Grenade" to 15f)

  // Previous frame values
  var prevTime: Long = 0
  var prevX: Float = 0f
  var prevY: Float = 0f
  var selfX: Float = 0f
  var selfY: Float = 0f
  var distX:Float = 0f
  var distY:Float = 0f
  var playerSpeed = 0f
  var prevDirection = Vector2(0f,0f)
  var prevCombatScore = 0f
  var prevDirectWeightedCombatScore = 0f
  var prevIndirectWeightedCombatScore = 0f
  var prevCombatTS: Long = 0
  val teamGUID = mutableMapOf<NetworkGUID, Boolean>()

  // Constants
  val MAX_ERANGEL = 795738.251975f // sqrt((795548.3 - 0)^2 + (17385.857 - 0)^2)
  val MAX_MIRAMAR = 735921.381698f // sqrt((78282 - 0)^2 + (731746 - 0)^2)
  val COMBAT_FRESHNESS = 3000 // in milliseconds
  val COMBAT_RANGE = 20000
  val GRENADE_RANGE = 1000
  val CORPSE_RANGE = 8000
  val ENEMY_RANGE = 15000
  val ITEM_RANGE = 10000
  val VEHICLE_RANGE = 13000
  val AIRDROP_RANGE = 35000

  // Proximity stats
  var enemyProximity = 0f
  var vehicleProximity = 0f
  var itemProximity = 0f
  var corpseProximity = 0f
  var safeZoneProximity = 0f
  var redZoneProximity = 0f
  var airDropProximity = 0f
  // Weighted by normalised distance
  // Object distance ranks
  // var enemyDistances = mutableListOf<Float>()
  // var vehicleDistances = mutableListOf<Float>()
  // var itemDistances = mutableListOf<Float>()
  // var corpseDistances = mutableListOf<Float>()
  // var airDropDistances = mutableListOf<Float>()
  // var directCombatDistances = mutableListOf<Float>()
  // var indirectCombatDistances = mutableListOf<Float>()

  var enemyWeightedProximity = 0f
  var vehicleWeightedProximity = 0f
  var itemWeightedProximity = 0f
  var corpseWeightedProximity = 0f
  var airDropWeightedProximity = 0f

  // Score stats
  var directCombatWeightedScore = 0f
  var indirectCombatWeightedScore = 0f
  var combatScore = 0f
  var gearScore = 0f

  override fun render() {
    Gdx.gl.glClearColor(0.417f, 0.417f, 0.417f, 0f)
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
    if (gameStarted)
      map = if (isErangel) mapErangel else mapMiramar
    else
      return
    val currentTime = System.currentTimeMillis()

    selfX = selfCoords.x
    selfY = selfCoords.y

    val selfDir = Vector2(selfX, selfY).sub(preSelfCoords)
    if (selfDir.len() < 1e-8)
      selfDir.set(preDirection)
    
    //move camera
    camera.position.set(selfX, selfY, 0f)
    camera.update()
    
    //draw map
    paint(camera.combined) {
      draw(map, 0f, 0f, mapWidth, mapWidth,
           0, 0, mapWidthCropped, mapWidthCropped,
           false, true)
    }
    
    shapeRenderer.projectionMatrix = camera.combined
    Gdx.gl.glEnable(GL20.GL_BLEND)
    
    drawGrid()
    drawCircles()
    
    val typeLocation = EnumMap<Archetype, MutableList<renderInfo>>(Archetype::class.java)

    for ((_, actor) in visualActors)
      typeLocation.compute(actor.Type) { _, v ->
        val list = v ?: ArrayList()
        val (centerX, centerY) = actor.location
        val direction = actor.rotation.y
        if (actor.Type == Player && !isTeamMate(actor) && teamGUID[actor.netGUID] == null) {
          enemyProximity += proximity(centerX, centerY, ENEMY_RANGE)
          enemyWeightedProximity += 1/euclidean_distance(centerX,centerY)
        } else {
          teamGUID[actor.netGUID] = true
        }
        list.add(tuple4(actor, centerX, centerY, direction))
        list
      }

    paint(fontCamera.combined) {
      largeFont.draw(spriteBatch, "$currentTime\n" +
								  "$NumAlivePlayers/$NumAliveTeams\n" +
                                  "${MatchElapsedMinutes}min\n" +
                                  "${ElapsedWarningDuration.toInt()}/${TotalWarningDuration.toInt()}\n", 10f, windowHeight - 10f)
      val time = (pinLocation.cpy().sub(selfX, selfY).len() / runSpeed).toInt()
      val (x, y) = pinLocation.mapToWindow()
      littleFont.draw(spriteBatch, "$time", x, windowHeight - y)
      safeZoneHint()
      drawPlayerInfos(typeLocation[Player])
    }
    
    val zoom = camera.zoom
    
    Gdx.gl.glEnable(GL20.GL_BLEND)
    draw(Filled) {
      color = redZoneColor
      circle(RedZonePosition, RedZoneRadius, 100)
      
      color = visionColor
      circle(selfX, selfY, visionRadius, 100)
      
      color = pinColor
      circle(pinLocation, pinRadius * zoom, 10)
      //draw self
      drawPlayer(LIME, tuple4(null, selfX, selfY, selfDir.angle()))
      drawItem()
      drawAirDrop(zoom)
      drawCorpse()
      drawAPawn(typeLocation, selfX, selfY, zoom, currentTime)
    }
    
    drawAttackLine(currentTime)

    var directionDelta = selfDir.sub(prevDirection)
    prevDirection = selfDir
    
    preSelfCoords.set(selfX, selfY)
    preDirection = selfDir
    
    Gdx.gl.glDisable(GL20.GL_BLEND)

    if (coordChange) {
      distX = abs(selfX-prevX)
      distY = abs(selfY-prevY)
    }

    playerSpeed = (kotlin.math.sqrt(distX.pow(2) + distY.pow(2))) / (currentTime - prevTime)

    if (playerHead[selfID] != null && playerHead[selfID] != "_") {
      gearScore += playerHead[selfID]!!.toFloat() * 10f
    } 
    if (playerArmor[selfID] != null && playerArmor[selfID] != "_") {
      gearScore += playerArmor[selfID]!!.toFloat() * 10f
    }
    if (playerBack[selfID] != null && playerBack[selfID] != "_") {
      gearScore += playerBack[selfID]!!.toFloat() * 10f
    }
	// // calculate our weighted scores given a list of distances for each feature
 //    directCombatWeightedScore = weightedProximity(directCombatDistances)
 //    indirectCombatWeightedScore = weightedProximity(indirectCombatDistances)
 //    enemyWeightedProximity = weightedProximity(enemyDistances)
 //    vehicleWeightedProximity = weightedProximity(vehicleDistances)
 //    corpseWeightedProximity = weightedProximity(corpseDistances)
 //    itemWeightedProximity = weightedProximity(itemDistances)
 //    airDropWeightedProximity = weightedProximity(airDropDistances)

    // check if we are still in a combat situation
    directCombatWeightedScore = updateWeightedDirectCombat(directCombatWeightedScore, currentTime)
    indirectCombatWeightedScore = updateWeightedIndirectCombat(indirectCombatWeightedScore, currentTime)
    combatScore = updateCombat(combatScore, currentTime)

    // var nonNullablePing: Float = 0.0f
    // if (playerPings[selfStateID] != null) {
    //   nonNullablePing = playerPings[selfStateID]!!
    // }

    val mutableListA = mutableListOf<Float>(selfX, selfY, playerSpeed.toFloat(), directionDelta.len(), NumAlivePlayers.toFloat(), gearScore, directCombatWeightedScore, indirectCombatWeightedScore, enemyWeightedProximity, corpseWeightedProximity, vehicleWeightedProximity, itemWeightedProximity, airDropWeightedProximity, safeZoneProximity, redZoneProximity)
    statsA.put(currentTime, mutableListA)

    val mutableListB = mutableListOf<Float>(selfX, selfY, playerSpeed.toFloat(), directionDelta.len(), gearScore, combatScore, enemyProximity, vehicleProximity, corpseProximity, itemProximity, airDropProximity, safeZoneProximity, redZoneProximity, NumAlivePlayers.toFloat())
    statsB.put(currentTime, mutableListB)

    // reset everything
    prevTime = currentTime
    prevX = selfX
    prevY = selfY
    gearScore = 0f

    // directCombatDistances.clear()
    // indirectCombatDistances.clear()
    // enemyDistances.clear()
    // vehicleDistances.clear()
    // corpseDistances.clear()
    // itemDistances.clear()
    // airDropDistances.clear()

    directCombatWeightedScore = 0f
    indirectCombatWeightedScore = 0f
    enemyWeightedProximity = 0f
    vehicleWeightedProximity = 0f
    corpseWeightedProximity = 0f
    itemWeightedProximity = 0f
    airDropWeightedProximity = 0f

    combatScore = 0f
    enemyProximity = 0f
    vehicleProximity = 0f
    corpseProximity = 0f
    itemProximity = 0f
    safeZoneProximity = 0f
    redZoneProximity = 0f
    airDropProximity = 0f

    // reset so only currently replicated visual actor data is being displayed
    // visualActors.clear()

    // println(mutableListA)
    // println(mutableListB)
  }

  fun updateWeightedDirectCombat(cScore: Float, currentTime: Long) : Float {
    if (cScore != 0f) {
      prevCombatTS = currentTime
      prevDirectWeightedCombatScore = cScore
      return cScore
    }
    else if (currentTime - prevCombatTS <= COMBAT_FRESHNESS) return prevDirectWeightedCombatScore
    else return cScore
  }

  fun updateWeightedIndirectCombat(cScore: Float, currentTime: Long) : Float {
    if (cScore != 0f) {
      prevCombatTS = currentTime
      prevIndirectWeightedCombatScore = cScore
      return cScore
    }
    else if (currentTime - prevCombatTS <= COMBAT_FRESHNESS) return prevIndirectWeightedCombatScore
    else return cScore
  }

  fun updateCombat(cScore: Float, currentTime: Long) : Float {
    if (cScore != 0f) {
      prevCombatTS = currentTime
      prevCombatScore = cScore
      return cScore
    }
    else if (currentTime - prevCombatTS <= COMBAT_FRESHNESS) return prevCombatScore
    else return cScore
  }

  fun writeDataFile(stats: SortedMap<Long, MutableList<Float>>, set: String) {
    File(filename+"_"+set+".txt").bufferedWriter().use { out ->
      stats.forEach {
          out.write("${it.key}")
          for (entry in it.value) {
            out.write(",${entry}")
          }
          out.newLine()
      }
    }
  }

  fun euclidean_distance(x: Float, y: Float) : Float {
    val distX = selfX-x 
    val distY = selfY-y
    return  kotlin.math.sqrt(distX.pow(2) + distY.pow(2))
  }

  // return 1 if player is withing distance, 0 otherwise
  fun proximity(x: Float, y: Float, range: Int) : Float {
    if (euclidean_distance(x,y) <= range)
     return 1f
    else return 0f
  }

  // calculate normalised proximity scaled by distance
  // fun weightedProximity(distances: MutableList<Float>) : Float {
  //   var score = 0f
  //   if (distances.size == 0) return 0f
  //   val max = distances.max()!! + 1f // add one so that if there is only one value it isn't reduced to 0
  //   val min = 0f
  //   val it = distances.listIterator()
  //   while (it.hasNext()) {
  //     val d = it.next()!!
  //     score += (1 - ((d - min) / (max - min))) // inverted to give closer objects higher scores, and further objects much lower scores (especially good when objects are clustered)
  //   }
  //   return score
  // }
  
  private fun drawAttackLine(currentTime: Long) {
    while (attacks.isNotEmpty()) {
      val (A, B) = attacks.poll()
      attackLineStartTime.add(Triple(A, B, currentTime))
    }
    if (attackLineStartTime.isEmpty()) return
    draw(Line) {
      val iter = attackLineStartTime.iterator()
      while (iter.hasNext()) {
        val (A, B, st) = iter.next()
        if (currentTime - st > attackLineDuration) {
          iter.remove()
          continue
        }
        if (A == selfID || B == selfID) {
          val enemyID = if (A == selfID) B else A
          val actorEnemyID = playerStateToActor[enemyID]
          if (actorEnemyID == null) {
            // there's an attack but we don't know the originator (typically is self damage or something along those lines)
            // combatScore += attackTable.get("Personal Gunfire")!! * 2
            iter.remove()
            continue
          }
          val actorEnemy = actors[actorEnemyID]
          if (actorEnemy == null || currentTime - st > attackMeLineDuration) {
            iter.remove()
            continue
          }
          color = attackLineColor
          val (xA, yA) = selfCoords
          val (xB, yB) = actorEnemy.location
          // if this is an attack involving the player and a known enemy 
          // scale according to proximity
          directCombatWeightedScore += 1 / euclidean_distance(xB, yB)
          if (proximity(xB,yB, COMBAT_RANGE) > 0) combatScore += attackTable.get("Personal Gunfire")!! * 2 
          else combatScore += attackTable.get("Personal Gunfire")!!
          line(xA, yA, xB, yB)
        } else {
          val actorAID = playerStateToActor[A]
          val actorBID = playerStateToActor[B]
          // attack between other players, if we have data on one then check proximity and scale score accordingly
          // if we know both, get average of each scaled proximity
          // otherwise add the proximity for the known enemy
          if (actorAID != null && actors[actorAID] != null) {
            combatScore += attackTable.get("Gunfire")!! * proximity(actors[actorAID]!!.location!!.x, actors[actorAID]!!.location!!.y, COMBAT_RANGE)
          } else if (actorBID != null && actors[actorBID] != null) {
            combatScore += attackTable.get("Gunfire")!! * proximity(actors[actorBID]!!.location!!.x, actors[actorBID]!!.location!!.y, COMBAT_RANGE)
          }
          if ((actorAID != null && actors[actorAID] != null) && (actorBID != null && actors[actorBID] != null)) {
            indirectCombatWeightedScore += 1 / ((euclidean_distance(actors[actorAID]!!.location!!.x, actors[actorAID]!!.location!!.y) + euclidean_distance(actors[actorBID]!!.location!!.x, actors[actorBID]!!.location!!.y)) / 2)
          } else if (actorAID != null && actors[actorAID] != null) {
            indirectCombatWeightedScore += 1 / euclidean_distance(actors[actorAID]!!.location!!.x, actors[actorAID]!!.location!!.y)
          } else if (actorBID != null && actors[actorBID] != null) {
            indirectCombatWeightedScore += 1 / euclidean_distance(actors[actorBID]!!.location!!.x, actors[actorBID]!!.location!!.y)
          }
          if (actorAID == null || actorBID == null) {
            iter.remove()
            continue
          }
          val actorA = actors[actorAID]
          val actorB = actors[actorBID]
          if (actorA == null || actorB == null || currentTime - st > attackLineDuration) {
            iter.remove()
            continue
          }
          color = attackLineColor
          val (xA, yA) = actorA.location
          val (xB, yB) = actorB.location
          line(xA, yA, xB, yB)
        }
      }
    }
  }
  
  private fun drawCircles() {
    Gdx.gl.glLineWidth(2f)
    draw(Line) {

      // // Proximity circles
      // color = RED
      // circle(Vector2(selfCoords.x,selfCoords.y), ENEMY_RANGE.toFloat(), 100)
      // color = GREEN 
      // circle(Vector2(selfCoords.x,selfCoords.y), ITEM_RANGE.toFloat(), 100)
      // color = YELLOW 
      // circle(Vector2(selfCoords.x,selfCoords.y), VEHICLE_RANGE.toFloat(), 100)
      // color = PURPLE 
      // circle(Vector2(selfCoords.x,selfCoords.y), CORPSE_RANGE.toFloat(), 100)
      // color = ORANGE 
      // circle(Vector2(selfCoords.x,selfCoords.y), AIRDROP_RANGE.toFloat(), 100)
      // color = PINK 
      // circle(Vector2(selfCoords.x,selfCoords.y), COMBAT_RANGE.toFloat(), 100)
      // color = BROWN
      // circle(Vector2(selfCoords.x,selfCoords.y), GRENADE_RANGE.toFloat(), 100)

      //vision circle
      
      color = safeZoneColor
      circle(PoisonGasWarningPosition, PoisonGasWarningRadius, 100)
      
      color = BLUE
      circle(SafetyZonePosition, SafetyZoneRadius, 100)
      
      if (PoisonGasWarningPosition.len() > 0) {
        color = safeDirectionColor
        line(selfCoords, PoisonGasWarningPosition)
      }
    }
    Gdx.gl.glLineWidth(1f)
  }
  
  private fun drawGrid() {
    draw(Filled) {
      color = BLACK
      //thin grid
      for (i in 0..7)
        for (j in 0..9) {
          rectLine(0f, i * unit + j * unit2, gridWidth, i * unit + j * unit2, 100f)
          rectLine(i * unit + j * unit2, 0f, i * unit + j * unit2, gridWidth, 100f)
        }
      color = GRAY
      //thick grid
      for (i in 0..7) {
        rectLine(0f, i * unit, gridWidth, i * unit, 500f)
        rectLine(i * unit, 0f, i * unit, gridWidth, 500f)
      }
    }
  }
  
  private fun ShapeRenderer.drawAPawn(typeLocation: EnumMap<Archetype, MutableList<renderInfo>>,
                                      selfX: Float, selfY: Float,
                                      zoom: Float,
                                      currentTime: Long) {
    for ((type, actorInfos) in typeLocation) {
      when (type) {
        TwoSeatBoat -> actorInfos?.forEach {
          val x = it?._2
          val y = it?._3
          vehicleProximity += proximity(x, y, VEHICLE_RANGE)
          vehicleWeightedProximity += 1 / euclidean_distance(x, y)
          drawVehicle(boatColor, it, vehicle2Width, vehicle6Width)
        }
        SixSeatBoat -> actorInfos?.forEach {
          val x = it?._2
          val y = it?._3
          vehicleProximity += proximity(x, y, VEHICLE_RANGE)
          vehicleWeightedProximity += 1 / euclidean_distance(x, y)
          drawVehicle(boatColor, it, vehicle4Width, vehicle6Width)
        }
        TwoSeatCar -> actorInfos?.forEach {
          val x = it?._2
          val y = it?._3
          vehicleProximity += proximity(x, y, VEHICLE_RANGE)
          vehicleWeightedProximity += 1 / euclidean_distance(x, y)
          drawVehicle(carColor, it, vehicle2Width, vehicle6Width)
        }
        FourSeatCar -> actorInfos?.forEach {
          val x = it?._2
          val y = it?._3
          vehicleProximity += proximity(x, y, VEHICLE_RANGE)
          vehicleWeightedProximity += 1 / euclidean_distance(x, y)
          drawVehicle(carColor, it, vehicle4Width, vehicle6Width)
        }
        SixSeatCar -> actorInfos?.forEach {
          val x = it?._2
          val y = it?._3
          vehicleProximity += proximity(x, y, VEHICLE_RANGE)
          vehicleWeightedProximity += 1 / euclidean_distance(x, y)
          drawVehicle(carColor, it, vehicle2Width, vehicle6Width)
        }
        Plane -> actorInfos?.forEach {
          drawPlayer(planeColor, it)
        }
        Player -> actorInfos?.forEach {
          drawPlayer(playerColor, it)
          aimAtMe(it, selfX, selfY, currentTime, zoom)
        }
        Parachute -> actorInfos?.forEach {
          drawPlayer(parachuteColor, it)
        }
        Grenade -> actorInfos?.forEach {
          val x = it?._2
          val y = it?._3
          directCombatWeightedScore += 1 / euclidean_distance(x, y)
          combatScore += attackTable.get("Grenade")!!
          drawPlayer(WHITE, it, false)
        }
        else -> {
          //            actorInfos?.forEach {
          //            bugln { "${it._1!!.archetype.pathName} ${it._1.location}" }
          //            drawPlayer(BLACK, it)
          //            }
        }
      }
    }
  }
  
  private fun ShapeRenderer.drawCorpse() {
    corpseLocation.values.forEach {
      val (x, y) = it
      corpseProximity += proximity(x, y, CORPSE_RANGE)
      corpseWeightedProximity += 1 / euclidean_distance(x, y)
      val backgroundRadius = (corpseRadius + 50f)
      val radius = corpseRadius
      color = BLACK
      rect(x - backgroundRadius, y - backgroundRadius, backgroundRadius * 2, backgroundRadius * 2)
      color = corpseColor
      rect(x - radius, y - radius, radius * 2, radius * 2)
    }
  }
  
  private fun ShapeRenderer.drawAirDrop(zoom: Float) {
    airDropLocation.values.forEach {
      val (x, y) = it
      airDropProximity += proximity(x, y, AIRDROP_RANGE)
      airDropWeightedProximity += 1 / euclidean_distance(x, y)
      val backgroundRadius = (airDropRadius + 2000) * zoom
      val airDropRadius = airDropRadius * zoom
      color = BLACK
      rect(x - backgroundRadius, y - backgroundRadius, backgroundRadius * 2, backgroundRadius * 2)
      color = BLUE
      rect(x, y - airDropRadius, airDropRadius, airDropRadius * 2)
      color = RED
      rect(x - airDropRadius, y - airDropRadius, airDropRadius, airDropRadius * 2)
    }
  }
  
  private fun ShapeRenderer.drawItem() {
    droppedItemLocation.values
      .forEach {
          val (x, y) = it.first
          val items = it.second
          itemProximity += proximity(x, y, ITEM_RANGE)
          itemWeightedProximity += 1 / euclidean_distance(x, y)
          val finalColor = it.third
          
          if (finalColor.a == 0f)
            finalColor.set(
                when {
                  "98k" in items || "m416" in items || "Choke" in items || "scar" in items -> rareWeaponColor
                  "armor3" in items || "helmet3" in items -> rareArmorColor
                  "4x" in items || "8x" in items -> rareScopeColor
                  "Extended" in items || "Compensator" in items -> rareAttachColor
                  "heal" in items || "drink" in items -> healItemColor
                  else -> normalItemColor
                })
          
          val rare = when (finalColor) {
            rareWeaponColor, rareArmorColor, rareScopeColor, rareAttachColor -> true
            else -> false
          }
          val backgroundRadius = (itemRadius + 50f)
          val radius = itemRadius
          if (rare) {
            color = BLACK
            rect(x - backgroundRadius, y - backgroundRadius, backgroundRadius * 2, backgroundRadius * 2)
            color = finalColor
            rect(x - radius, y - radius, radius * 2, radius * 2)
          } else {
            color = BLACK
            circle(x, y, backgroundRadius, 10)
            color = finalColor
            circle(x, y, radius, 10)
          }
        }
  }
  
  fun drawPlayerInfos(players: MutableList<renderInfo>?) {
    players?.forEach {
      val (actor, x, y, _) = it
      actor!!
      val playerStateGUID = actorWithPlayerState[actor.netGUID] ?: return@forEach
      val name = playerNames[playerStateGUID] ?: return@forEach
      val teamNumber = teamNumbers[playerStateGUID] ?: 0
      val numKills = playerNumKills[playerStateGUID] ?: 0
      val (sx, sy) = Vector2(x, y).mapToWindow()
      query(name)
      if (completedPlayerInfo.containsKey(name)) {
        val info = completedPlayerInfo[name]!!
        val desc = "$name($numKills)\n${info.win}/${info.totalPlayed}\n${info.roundMostKill}-${info.killDeathRatio.d(2)}/${info.headshotKillRatio.d(2)}\n$teamNumber"
        nameFont.draw(spriteBatch, desc, sx + 2, windowHeight - sy - 2)
      } else
        nameFont.draw(spriteBatch, "$name($numKills)\n$teamNumber", sx + 2, windowHeight - sy - 2)
    }
    val profileText = "${completedPlayerInfo.size}/${completedPlayerInfo.size + pendingPlayerInfo.size}"
    layout.setText(largeFont, profileText)
    largeFont.draw(spriteBatch, profileText, windowWidth - layout.width, windowHeight - 10f)
  }
  
  var lastPlayTime = System.currentTimeMillis()
  fun safeZoneHint() {
    if (PoisonGasWarningPosition.len() > 0) {
      val dir = PoisonGasWarningPosition.cpy().sub(selfCoords)
      val road = dir.len() - PoisonGasWarningRadius
      if (road > 0) safeZoneProximity = road
      else safeZoneProximity = 0f // if inside the zone, set to zero
      val redDir = RedZonePosition.cpy().sub(selfCoords)
      if (redDir.len() - RedZoneRadius > 0) redZoneProximity = redDir.len() - RedZoneRadius 
      else redZoneProximity = 0f // if inside the zone, set to zero
      if (road > 0) {
        val runningTime = (road / runSpeed).toInt()
        val (x, y) = dir.nor().scl(road).add(selfCoords).mapToWindow()
        littleFont.draw(spriteBatch, "$runningTime", x, windowHeight - y)
        val remainingTime = (TotalWarningDuration - ElapsedWarningDuration).toInt()
        if (remainingTime == 60 && runningTime > remainingTime) {
          val currentTime = System.currentTimeMillis()
          if (currentTime - lastPlayTime > 10000) {
            lastPlayTime = currentTime
            alarmSound.play()
          }
        }
      }
    }
  }
  
  inline fun draw(type: ShapeType, draw: ShapeRenderer.() -> Unit) {
    shapeRenderer.apply {
      begin(type)
      draw()
      end()
    }
  }
  
  inline fun paint(matrix: Matrix4, paint: SpriteBatch.() -> Unit) {
    spriteBatch.apply {
      projectionMatrix = matrix
      begin()
      paint()
      end()
    }
  }
  
  fun ShapeRenderer.circle(loc: Vector2, radius: Float, segments: Int) {
    circle(loc.x, loc.y, radius, segments)
  }
  
  fun ShapeRenderer.aimAtMe(it: renderInfo, selfX: Float, selfY: Float, currentTime: Long, zoom: Float) {
    //draw aim line
    val (actor, x, y, dir) = it
    if (isTeamMate(actor)) return
    val actorID = actor!!.netGUID
    val dirVec = dirUnitVector.cpy().rotate(dir)
    val focus = Vector2(selfX - x, selfY - y)
    val distance = focus.len()
    var aim = false
    if (distance < aimLineRange && distance > aimCircleRadius) {
      val aimAngle = focus.angle(dirVec)
      if (aimAngle.absoluteValue < asin(aimCircleRadius / distance) * MathUtils.radiansToDegrees) {//aim
        aim = true
        aimStartTime.compute(actorID) { _, startTime ->
          if (startTime == null) currentTime
          else {
            if (currentTime - startTime > aimTimeThreshold) {
              color = aimLineColor
              rectLine(x, y, selfX, selfY, aimLineWidth * zoom)
            }
            startTime
          }
        }
      }
    }
    if (!aim)
      aimStartTime.remove(actorID)
  }

  fun ShapeRenderer.drawPlayer(pColor: Color?, actorInfo: renderInfo, drawSight: Boolean = true) {
    val zoom = camera.zoom
    val backgroundRadius = (playerRadius + 2000f) * zoom
    val playerRadius = playerRadius * zoom
    val directionRadius = directionRadius * zoom
    
    color = BLACK
    val (actor, x, y, dir) = actorInfo
    circle(x, y, backgroundRadius, 10)
    
    color = if (isTeamMate(actor))
      teamColor
    else
      pColor
    
    circle(x, y, playerRadius, 10)
    
    if (drawSight) {
      color = sightColor
      arc(x, y, directionRadius, dir - fov / 2, fov, 10)
    }
  }
  
  private fun isTeamMate(actor: Actor?): Boolean {
    if (actor != null) {
      val playerStateGUID = actorWithPlayerState[actor.netGUID]
      if (playerStateGUID != null) {
        val name = playerNames[playerStateGUID] ?: return false
        if (name in team)
          return true
      }
    }
    return false
  }
  
  fun ShapeRenderer.drawVehicle(_color: Color, actorInfo: renderInfo,
                                width: Float, height: Float) {
    
    val (actor, x, y, dir) = actorInfo
    val v_x = actor!!.velocity.x
    val v_y = actor.velocity.y
    
    val dirVector = dirUnitVector.cpy().rotate(dir).scl(height / 2)
    color = BLACK
    val backVector = dirVector.cpy().nor().scl(height / 2 + 200f)
    rectLine(x - backVector.x, y - backVector.y,
             x + backVector.x, y + backVector.y, width + 400f)
    color = _color
    rectLine(x - dirVector.x, y - dirVector.y,
             x + dirVector.x, y + dirVector.y, width)
    
    if (actor.beAttached || v_x * v_x + v_y * v_y > 40) {
      color = playerColor
      circle(x, y, playerRadius * camera.zoom, 10)
    }
  }
  
  override fun resize(width: Int, height: Int) {
    windowWidth = width.toFloat()
    windowHeight = height.toFloat()
    camera.setToOrtho(true, windowWidth * windowToMapUnit, windowHeight * windowToMapUnit)
    fontCamera.setToOrtho(false, windowWidth, windowHeight)
  }
  
  override fun pause() {
  }
  
  override fun resume() {
  }
  
  override fun dispose() {
    deregister(this)
    alarmSound.dispose()
    nameFont.dispose()
    largeFont.dispose()
    littleFont.dispose()
    mapErangel.dispose()
    mapMiramar.dispose()
    spriteBatch.dispose()
    shapeRenderer.dispose()
  }
  
}