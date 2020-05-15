package raidboss

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import io.grpc.ManagedChannelBuilder
import com.github.phisgr.gatling.grpc.Predef._
import org.justinhj.raidbossservice.raidbossservice._
import scala.util.Try
import scala.util.Random
import io.gatling.commons.validation.Validation
import io.gatling.commons.validation._

object RandomPlayers {

  def randomPlayerFeeder(numUsers: Int) = new Iterator[Map[String,String]] {

    private val RNG = new Random

    override def hasNext = true

    override def next: Map[String, String] = {
      val id = s"player-${Math.abs((RNG.nextLong() % numUsers)).toString}"

      Map("playerId" -> id)
    }
  }
}

class RaidBossSimulation1 extends Simulation {

  val host = scala.util.Properties.envOrElse("GATLING_HOST", "localhost")
  val portString = scala.util.Properties.envOrElse("GATLING_PORT", "9000")
  val port = Try(portString.toInt).getOrElse(9000)
  val numUsersString = scala.util.Properties.envOrElse("GATLING_NUM_USERS", "1")
  val numUsers = Try(numUsersString.toInt).getOrElse(1)
  val numAttacksString = scala.util.Properties.envOrElse("GATLING_NUM_ATTACKS", "10")
  val numAttacks = Try(numAttacksString.toInt).getOrElse(10)

  val grpcConf = grpc(ManagedChannelBuilder.forAddress(host, port).usePlaintext())
  val userFeeder = RandomPlayers.randomPlayerFeeder(numUsers)

  val bossDefId = "Angry-Dog-1"
  val groupId = "Villagers-1"

  val instanceId = bossDefId + "-" + groupId + "-" + System.currentTimeMillis.toString

  private val RNG = new Random

  val scn = scenario("Create and attack")
    .exec(
      grpc("Create Boss")
        .rpc(RaidBossServiceGrpc.METHOD_CREATE_RAID_BOSS)
        .payload(RaidBossCreate(instanceId, bossDefId, groupId)))
    .repeat(numAttacks) {
        feed(userFeeder)
        .exec(
          grpc("Attack Boss")
            .rpc(RaidBossServiceGrpc.METHOD_ATTACK_RAID_BOSS)
            .payload(session =>
              for (
                playerId <- session("playerId").validate[String]
              ) yield RaidBossAttack(instanceId, playerId, Math.abs(RNG.nextLong()%19) + 1))
          )
    }
    .exec(
      grpc("View Boss")
        .rpc(RaidBossServiceGrpc.METHOD_VIEW_RAID_BOSS)
        .payload(RaidBossView(instanceId))
        .extract(rbi => Some(rbi))(rbi => rbi.saveAs("finalBoss"))
    )
    .exec(session => {
      val boss = session("finalBoss").as[RaidBossInstance]
      println(s"Leaderboard for boss ${boss.bossInstanceId} health ${boss.health}\n")
      boss.leaderboard.foreach {
        entry =>
          println(s"${entry.playerId}, ${entry.score}")
      }
      session
    })

  setUp(scn.inject(rampUsers(numUsers) during (30 seconds)).protocols(grpcConf))
}
