package pubg.radar.struct.cmd

import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import pubg.radar.struct.*
import pubg.radar.struct.Archetype.*
import wumo.pubg.struct.cmd.TeamCMD
import java.util.*

typealias cmdProcessor = (Actor, Bunch, Int) -> Boolean

enum class REPCMD {
  DynamicArray,  //0 Dynamic array
  Return,  //1 Return from array, or end of stream
  Property,  //2 Generic property
  
  PropertyBool,//3
  PropertyFloat,//4
  PropertyInt,//5
  PropertyByte,//6
  PropertyName,//7
  PropertyObject,//8
  PropertyUInt32,//9
  PropertyVector,//10
  PropertyRotator,//11
  PropertyPlane,//12
  PropertyVector100,//13
  PropertyNetId,//14
  RepMovement,//15
  PropertyVectorNormal,//16
  PropertyVector10,//17
  PropertyVectorQ,//18
  PropertyString,//19
  PropertyUInt64,//20
}

object CMD {
  fun Bunch.propertyBool() = readBit()
  fun Bunch.propertyFloat() = readFloat()
  fun Bunch.propertyInt() = readInt32()
  fun Bunch.propertyByte() = readByte()
  fun Bunch.propertyName() = readName()
  fun Bunch.propertyObject() = readObject()
  fun Bunch.propertyUInt32() = readUInt32()
  fun Bunch.propertyVector() = Vector3(readFloat(), readFloat(), readFloat())
  fun Bunch.propertyRotator() = Vector3(readFloat(), readFloat(), readFloat())
  fun Bunch.propertyPlane() = Quaternion(propertyVector(), readFloat())
  fun Bunch.propertyVector100() = readVector(100, 30)
  fun Bunch.propertyNetId() = if (readInt32() > 0) readString() else ""
  fun Bunch.repMovement(actor: Actor) {
    val bSimulatedPhysicSleep = readBit()
    val bRepPhysics = readBit()
    actor.location = if (actor.isAPawn)
      readVector(100, 30)
    else
      readVector(1, 24)
    
    actor.rotation = if (actor.isACharacter)
      readRotationShort()
    else
      readRotation()
    
    actor.velocity = readVector(1, 24)
    if (bRepPhysics)
      readVector(1, 24)
  }
  
  fun Bunch.propertyVectorNormal() = readFixedVector(1, 16)
  fun Bunch.propertyVector10() = readVector(10, 24)
  fun Bunch.propertyVectorQ() = readVector(1, 20)
  fun Bunch.propertyString() = readString()
  fun Bunch.propertyUInt64() = readInt64()
  
  val processors: Map<Archetype, cmdProcessor>
  
  init {
    processors = EnumMap<Archetype, cmdProcessor>(
        mapOf(
            GameState to GameStateCMD::process,
            Other to APawnCMD::process,
            DroopedItemGroup to APawnCMD::process,
            Grenade to APawnCMD::process,
            TwoSeatBoat to APawnCMD::process,
            SixSeatBoat to APawnCMD::process,
            TwoSeatCar to APawnCMD::process,
            ThreeSeatCar to APawnCMD::process,
            FourSeatCar to APawnCMD::process,
            SixSeatCar to APawnCMD::process,
            Plane to APawnCMD::process,
            Player to ActorCMD::process,
            Parachute to APawnCMD::process,
            AirDrop to APawnCMD::process,
            PlayerState to PlayerStateCMD::process,
            Team to TeamCMD::process
        ))
  }
}

