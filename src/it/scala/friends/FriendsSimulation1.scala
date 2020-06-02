package friends

import cloudstate.samples.chat.friends.grpc.friends.FriendsGrpc
import cloudstate.samples.chat.friends.grpc.friends.FriendRequest

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import io.grpc.ManagedChannelBuilder
import com.github.phisgr.gatling.grpc.Predef._
import cloudstate.samples.chat.friends.grpc._
import scala.util.Try
import scala.util.Random
import io.gatling.commons.validation.Validation
import io.gatling.commons.validation._
import cloudstate.samples.chat.friends.grpc.friends.User
import cloudstate.samples.chat.friends.grpc.friends.Friend
import cloudstate.samples.chat.friends.grpc.friends.FriendsList

class FriendsSimulation1 extends Simulation {

  val host = scala.util.Properties.envOrElse("GATLING_HOST", "localhost")
  val portString = scala.util.Properties.envOrElse("GATLING_PORT", "9000")
  val port = Try(portString.toInt).getOrElse(9000)
  val numUsersString = scala.util.Properties.envOrElse("GATLING_NUM_USERS", "1")
  val numUsers = Try(numUsersString.toInt).getOrElse(1)

  val grpcConf = grpc(ManagedChannelBuilder.forAddress(host, port).usePlaintext())
  val userFeeder = csv("names.csv").eager.random
  val friendFeeder = csv("names.csv").eager.random

  val scn = scenario("add friend and get friends")
    .feed(userFeeder)
    .exec(
      grpc("Add Friend")
        .rpc(FriendsGrpc.METHOD_ADD)
         .payload(session =>
              for (
                user <- session("name").validate[String]
              ) yield FriendRequest(user, Some(Friend("bob")))))
    .exec(
      grpc("Get Friends")
        .rpc(FriendsGrpc.METHOD_GET_FRIENDS)
        .payload(session =>
              for (
                user <- session("name").validate[String]
              ) yield User(user))
        .extract(friends => Some(friends))(fl => fl.saveAs("friends")))
    .exec(session => {
      val fl = session("friends").as[FriendsList]
      println(fl)
      session
    })

  setUp(scn.inject(rampUsers(numUsers) during (30 seconds)).protocols(grpcConf))
}
