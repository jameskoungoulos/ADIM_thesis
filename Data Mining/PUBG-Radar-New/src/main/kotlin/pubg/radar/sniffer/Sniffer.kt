package pubg.radar.sniffer

import com.badlogic.gdx.math.Vector2
import org.pcap4j.core.*
import org.pcap4j.core.BpfProgram.BpfCompileMode.OPTIMIZE
import org.pcap4j.core.PcapNetworkInterface.PromiscuousMode.PROMISCUOUS
import org.pcap4j.packet.*
import pubg.radar.*
import pubg.radar.deserializer.proc_raw_packet
import pubg.radar.deserializer.replaySpeed
import pubg.radar.deserializer.pcapFile
import pubg.radar.sniffer.SniffOption.*
import pubg.radar.util.notify
import java.io.*
import java.io.File.separator
import java.net.Inet4Address
import java.nio.charset.Charset
import java.util.*
import javax.swing.*
import javax.swing.JOptionPane.*
import kotlin.collections.ArrayList
import kotlin.concurrent.thread
import kotlin.experimental.and

const val check1 = 12
const val check2 = 8
const val check3 = 4
const val flag1: Byte = 0
const val flag2: Byte = -1
fun Byte.check() = this == flag1 || this == flag2
class DevDesc(val dev: PcapNetworkInterface, val address: Inet4Address) {
  override fun toString(): String {
    return "[${address.hostAddress}] - ${dev.description}"
  }
}

enum class SniffOption {
  PortFilter,
  PPTPFilter,
  L2TPFilter
}

val settingHome = "${System.getProperty("user.home")}$separator.pubgradar"
val settingFile = File("$settingHome${separator}setting.properties")
const val PROP_NetworkInterface = "NetworkInterface"
const val PROP_SniffOption = "SniffOption"

class Sniffer {
  companion object: GameListener {
    
    override fun onGameOver() {
    }
    
    val nif: PcapNetworkInterface
    val localAddr: Inet4Address
    val sniffOption: SniffOption

    val preSelfCoords = Vector2()
    var preDirection = Vector2()
    var selfCoords = Vector2()
    var coordChange = false
    
    init {
      register(this)
      try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
      } catch (e: Exception) {
        e.printStackTrace()
      }
      
      var nif: PcapNetworkInterface? = null
      var sniffOption: SniffOption? = null
      val devs = Pcaps.findAllDevs()
      val prop = Properties()
      var found = false
      try {
        FileInputStream(settingFile).use {
          prop.load(it)
          val dev_desc = prop.getProperty(PROP_NetworkInterface)
          sniffOption = SniffOption.valueOf(prop.getProperty(PROP_SniffOption))
          nif = devs.first { it.description == dev_desc }
        }
        found = true
      } catch (e: Exception) {
        found = false
      }
      
      val choices = ArrayList<DevDesc>()
      for (dev in devs)
        dev.addresses
            .filter { it.address is Inet4Address }
            .mapTo(choices) { DevDesc(dev, it.address as Inet4Address) }
      if (choices.isEmpty()) {
        notify("No ipv4 network interface!!")
        System.exit(-1)
      }
      val arrayChoices = choices.toTypedArray()
      val msg = "Choose the network interface to sniff..."
      val group = ButtonGroup()
      val portFilter = JRadioButton("Direct network connection/virtual network adapter)",
                                    !found || sniffOption == PortFilter)
      val routeIpFilter = JRadioButton("PPTP tunneling",
                                       found && sniffOption == PPTPFilter)
      val l2tpFilter = JRadioButton("L2TP protocal", 
                                    found && sniffOption == L2TPFilter)
      group.add(portFilter)
      group.add(routeIpFilter)
      group.add(l2tpFilter)
      val netDevs = JComboBox(arrayChoices)
      if (found) netDevs.selectedItem = arrayChoices.firstOrNull { it.dev === nif }
      val params = arrayOf(msg, netDevs, portFilter, routeIpFilter, l2tpFilter)
      
      val option = showConfirmDialog(null, params, "Network interfaces", OK_CANCEL_OPTION)
      if (option == CANCEL_OPTION)
        System.exit(0)
      
      val choice = netDevs.selectedIndex
      if (choice == -1)
        System.exit(-1)
      
      val devDesc = arrayChoices[choice]
      nif = devDesc.dev
      val localAddr = devDesc.address
      
      when {
        portFilter.isSelected -> sniffOption = PortFilter
        routeIpFilter.isSelected -> sniffOption = PPTPFilter
        l2tpFilter.isSelected -> sniffOption = L2TPFilter
      }
      
      try {
        File(settingHome).mkdirs()
        FileOutputStream(settingFile).use {
          if (devDesc.dev.description == null) return@use
          prop.setProperty(PROP_NetworkInterface, devDesc.dev.description)
          prop.setProperty(PROP_SniffOption, sniffOption!!.name)
          prop.store(it, "network interface to sniff")
        }
      } catch (e: Exception) {
        notify("Cannot save setting file to $settingFile")
      }
      
      this.nif = nif!!
      this.localAddr = localAddr
      this.sniffOption = sniffOption!!
    }
    
    const val snapLen = 65536
    val mode = PROMISCUOUS
    const val timeout = 1
    
    const val PPTPFlag: Byte = 0b0011_0000
    const val ACKFlag: Byte = 0b1000_0000.toByte()
    
    fun ByteArray.toIntBE(pos: Int, num: Int): Int {
      var value = 0
      for (i in 0 until num)
        value = value or ((this[pos + num - 1 - i].toInt() and 0xff) shl 8 * i)
      return value
    }
    
    fun parsePPTPGRE(raw: ByteArray): Packet? {
      var i = 0
      if (raw[i] != PPTPFlag) return null//PPTP
      i++
      val hasAck = (raw[i] and ACKFlag) != 0.toByte()
      i++
      val protocolType = raw.toIntBE(i, 2)
      i += 2
      if (protocolType != 0x880b) return null
      val payloadLength = raw.toIntBE(i, 2)
      i += 2
      val callID = raw.toIntBE(i, 2)
      i += 2
      val seq = raw.toIntBE(i, 4)
      i += 4
      if (hasAck) {
        val ack = raw.toIntBE(i, 4)
        i += 4
      }
      if (raw[i] != 0x21.toByte()) return null//not ipv4
      i--
      raw[i] = 0
      val pppPkt = PppSelector.newPacket(raw, i, raw.size - i)
      return pppPkt.payload
    }

    fun parseL2TPPacket(raw: ByteArray): Packet? {
      // println(DatatypeConverter.printHexBinary(raw)+"  raw:"+raw.toString(Charset.defaultCharset()));
      var i = 0
      //control message return
      val isControl = (raw[i] and 0b1000_0000.toByte()) != 0.toByte()
      if(isControl){
        return null;
      }
      val hasLength = (raw[i] and 0b0100_0000.toByte()) != 0.toByte()
      if(!hasLength){
        return null;
      }
      i+=8;
      val pppPkt = PppSelector.newPacket(raw, i, raw.size - i)
      return pppPkt.payload
    }
    
    fun udp_payload(packet: Packet): UdpPacket? {
      return when (sniffOption) {
        PortFilter -> packet
        PPTPFilter -> parsePPTPGRE(packet[IpV4Packet::class.java].payload.rawData)
        L2TPFilter -> parseL2TPPacket(packet[UdpPacket::class.java].payload.rawData)
        
      }?.get(UdpPacket::class.java)
    }
    
    fun sniffLocationOnline() {
      val handle = nif.openLive(snapLen, mode, timeout)
      val filter = when (sniffOption) {
        PortFilter -> "udp"
        PPTPFilter -> "ip[9]=47"
        L2TPFilter -> "udp"
      }
      handle.setFilter(filter, OPTIMIZE)
      thread(isDaemon = true) {
        handle.loop(-1) { packet: Packet? ->
          try {
            packet!!
            val ip = packet[IpPacket::class.java]
            val udp = udp_payload(packet) ?: return@loop
            val raw = udp.payload.rawData
            val src = ip.header.srcAddr.toString()
            
            /*
            // if ("192.168" in src) {
              if (udp.header.dstPort.valueAsInt() in 7000..7999) {
                proc_raw_packet(raw, false)
              //}
            } else if (udp.header.srcPort.valueAsInt() in 7000..7999){
                proc_raw_packet(raw, true)
            }
            */
            /*
            if (raw.size == 44) {
                parseSelfLocation(raw)
            */
            if (udp.header.dstPort.valueAsInt() in 7000..7999)
              proc_raw_packet(raw, false)
            else if (udp.header.srcPort.valueAsInt() in 7000..7999)
              proc_raw_packet(raw)
          } catch (e: Exception) {
            //println("Sniffer error")
          }
        }
      }
    }
    
    fun sniffLocationOffline(filename: String): Thread {
      return thread(isDaemon = true) {
        val files = arrayOf("C:\\Users\\james\\Documents\\GitHub\\ADIM\\Data Mining\\Data\\Packet Captures\\New\\"+filename+".pcap")
        for (file in files) {
          val handle = Pcaps.openOffline(file)
          
          while (true) {
            try {
              val packet = handle.nextPacket ?: break
              val ip = packet[IpPacket::class.java]
              val udp = udp_payload(packet) ?: continue
              val raw = udp.payload.rawData
              // issue with local IP changing since packet capture
              if (ip.header.srcAddr.toString() == "/192.168.1.30") {
                //proc_raw_packet(raw, false)
                if (raw.size == 44)
                  coordChange = parseSelfLocation(raw)
              } else if (udp.header.srcPort.valueAsInt() in 7000..7999)
                proc_raw_packet(raw)
            } catch (e: IndexOutOfBoundsException) {
            } catch (e: Exception) {
            } catch (e: NotOpenException) {
              break
            }
            Thread.sleep(1)
          }
        }
      }
    }
    
    private fun parseSelfLocation(raw: ByteArray): Boolean {
      val len = raw.size
      val flag1 = raw[len - check1]
      val flag2 = raw[len - check2]
      val flag3 = raw[len - check3]
      if (flag1.check() && flag2.check() && flag3.check()) {
        val _x = raw.toIntBE(len - check1 + 1, 3)
        val _y = raw.toIntBE(len - check2 + 1, 3)
        val _z = raw.toIntBE(len - check3 + 1, 3)
        val x = 0.1250155302572263f * _x - 20.58662848625851f
        val y = -0.12499267869373985f * _y + 2097021.7946571815f
        val z = _z / 20.0f
        selfCoords = Vector2(x, y)
        return true
      }
      return false
    }
  }
}
