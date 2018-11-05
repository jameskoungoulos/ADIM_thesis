package pubg.radar.struct.cmd

import pubg.radar.bugln
import pubg.radar.deserializer.ROLE_MAX
import pubg.radar.deserializer.channel.ActorChannel
import pubg.radar.struct.*
import pubg.radar.struct.Archetype.*
import pubg.radar.struct.cmd.CMD.propertyVector100
import pubg.radar.struct.cmd.CMD.repMovement

object APawnCMD {
  fun process(actor: Actor, bunch: Bunch, waitingHandle: Int): Boolean {
    with(bunch) {
      when (waitingHandle) {
        1 -> if (readBit()) {//bHidden
          ActorChannel.visualActors.remove(actor.netGUID)
          bugln { ",bHidden id$actor" }
        }
        2 -> if (!readBit()) {// bReplicateMovement
          ActorChannel.visualActors.remove(actor.netGUID)
          bugln { ",!bReplicateMovement id$actor " }
        }
        3 -> if (readBit()) {//bTearOff
          ActorChannel.visualActors.remove(actor.netGUID)
          bugln { ",bTearOff id$actor" }
        }
        4 -> {
          val role = readInt(ROLE_MAX)
          val b = role
        }
        5 -> {
          val (netGUID, obj) = readObject()
          actor.owner = if (netGUID.isValid()) netGUID else null
          bugln { " owner: [$netGUID] $obj ---------> beOwned:$actor" }
        }
        6 -> {
          repMovement(actor)
          with(actor) {
            when (Type) {
              DroopedItemGroup -> ActorChannel.droppedItemLocation[netGUID]?.first!!.set(location)
              AirDrop -> ActorChannel.airDropLocation[netGUID] = location
              Other -> {
              }
              else -> ActorChannel.visualActors[netGUID] = this
            }
          }
        }
        7 -> {
          val (a, obj) = readObject()
          val attachTo = if (a.isValid()) {
            ActorChannel.actors[a]?.beAttached = true
            a
          } else null
          if (actor.attachTo != null)
            ActorChannel.actors[actor.attachTo!!]?.beAttached = false
          actor.attachTo = attachTo
          bugln { ",attachTo [$actor---------> $a ${NetGUIDCache.guidCache.getObjectFromNetGUID(a)} ${ActorChannel.actors[a]}" }
        }
        8 -> {
          val locationOffset = propertyVector100()
          if (actor.Type == DroopedItemGroup) {
            bugln { "${actor.location} locationOffset $locationOffset" }
          }
          bugln { ",attachLocation $actor ----------> $locationOffset" }
        }
        else -> return false
      }
      return true
    }
  }
}