import com.google.inject.AbstractModule
import java.time.Clock

import akka.actor.ActorSystem
import vanner.users._
import vanner.friend._
import codecraft.platform.amqp._
import codecraft.platform.ICloud
import com.typesafe.config.ConfigFactory
import scala.collection.JavaConversions._
import controllers.CloudSources

object Constants {
	// Generate the routing information for user commands.
	val routingInfo = RoutingInfo(
    List(
      UserRoutingGroup.cmdInfo.map {
        case registry => (registry.key, registry)
      }.toMap,
      FriendRoutingGroup.cmdInfo.map {
        case registry => (registry.key, registry)
      }.toMap
    ).foldLeft(Map.empty[String, codecraft.codegen.CmdRegistry]) {
      case (acc, a) => acc ++ a
    },
    List(
      UserRoutingInfo.eventInfo,
      FriendRoutingInfo.eventInfo
    ).flatten map { eventInfo => (eventInfo.key -> eventInfo) } toMap,
    List(
      UserRoutingGroup.groupRouting,
      FriendRoutingGroup.groupRouting
    ).map(group => (group.queueName -> group)).toMap
  )
}

class Module extends AbstractModule {
  val config = ConfigFactory.load()
  val system = ActorSystem("cloud")

  val cloudA = config getString "cloud.cloudA" toInt
  val cloudB = config getString "cloud.cloudB" toInt

  val primaryCloud = AmqpCloud(
    system,
    s"amqp://rest:vanner@192.168.99.100:$cloudA/vanner",
    Constants.routingInfo
  ).asInstanceOf[ICloud]

  val secondaryCloud = AmqpCloud(
    system,
    s"amqp://rest:vanner@192.168.99.100:$cloudB/vanner",
    Constants.routingInfo
  ).asInstanceOf[ICloud]

  val cloudSources = {
    if (cloudA > cloudB) {
      CloudSources(List(primaryCloud, secondaryCloud))
    }
    else {
      CloudSources(List(secondaryCloud, primaryCloud))
    }
  }

  println("Cloud created")

  override def configure() = {
    println(s"Binding services...")
    bind(classOf[CloudSources]).toInstance(cloudSources)
    bind(classOf[ICloud]).toInstance(primaryCloud)
    println(s"Bound services")
  }
}
