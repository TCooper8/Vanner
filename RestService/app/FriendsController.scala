package controllers

import codecraft.platform.ICloud
import javax.inject._
import play.api._
import vanner.friend._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json._
import play.api.mvc._
import scala.concurrent.duration._
import scala.concurrent.Future

@Singleton
class FriendController @Inject() (cloud: ICloud) extends Controller {
  implicit val formatFriendAddFriend = Json.format[AddFriend]
  implicit val formatFriendAddFriendReply = Json.format[AddFriendReply]

  def list(userId: String) = Action.async { req =>
    val cmd = ListFriends(userId)

    (cloud requestCmd ("friend.list", cmd, 5 seconds)).mapTo[ListFriendsReply] map {
      case ListFriendsReply(None, code, error) =>
        Status(code)(Json toJson error)
      case ListFriendsReply(Some(ids), code, None) if code >= 200 && code < 300 =>
        Status(code)(Json toJson ids)
    }
  }

  def post = Action.async(BodyParsers.parse.json) { req =>
    req.body.validate[AddFriend].fold(
      errors => Future {
        BadRequest(JsError toJson errors)
      },
      cmd => {
        cloud.requestCmd("friend.post", cmd, 5 seconds).mapTo[AddFriendReply] map {
          case AddFriendReply(code, None) if code >= 200 && code < 300 =>
            Status(code)
          case AddFriendReply(code, Some(error)) =>
            Status(code)(error)
        }
      }
    )
  }
}

