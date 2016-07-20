package controllers

import codecraft.platform.ICloud
import javax.inject._
import play.api._
import vanner.users._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json._
import play.api.mvc._
import scala.concurrent.duration._
import scala.concurrent.{Future, Await}

case class CloudSources(
  clouds: List[ICloud]
)

@Singleton
class UserController @Inject() (cloudSources: CloudSources) extends Controller {
  implicit val formatUser = Json.format[User]
  implicit val formatUserGetUser = Json.format[GetUser]
  implicit val formatUserGetUserReply = Json.format[GetUserReply]
  implicit val formatUserPostUser = Json.format[PostUser]
  implicit val formatUserPostUserReply = Json.format[PostUserReply]
  implicit val formatListUsers = Json.format[ListUsers]
  implicit val formatListUsersReply = Json.format[ListUsersReply]

  val primaryCloud = cloudSources.clouds match {
    case Nil => throw new Exception("No cloud sources are bound to this service")
    case cloud::_ => cloud
  }

  var knownUserIds = List.empty[String]

  primaryCloud subscribeEvent ("event.user.created", {
    case UserCreated(id) =>
      knownUserIds = id::knownUserIds
  })

  def getUser(cmd: GetUser, clouds: List[ICloud]): Future[Result] = clouds match {
    case Nil => Future{ Status(503) }
    case cloud::clouds =>
      (cloud requestCmd ("user.get", cmd, 15 seconds)).mapTo[GetUserReply] map {
        case GetUserReply(Some(user), 200, None) =>
          println(s"#### Got user from $cloud")
          Ok(Json toJson user)

        case GetUserReply(None, 404, error) =>
          // Check the next cloud.
          clouds match {
            case Nil => Status(404)(Json toJson error)
            case clouds => Await.result(getUser(cmd, clouds), 5 seconds)
          }

        case GetUserReply(None, code, error) =>
          Status(code)(error getOrElse "Undefined error")
      } recover {
        case e => Await.result(getUser(cmd, clouds), 5 seconds)
      }
  }

  def get(id: String) = Action.async { req =>
    val cmd = GetUser(id)

    // Find the user in US first, fallback to UK.
    getUser(cmd, cloudSources.clouds)
  }

  def list = Action.async { req =>
    (primaryCloud requestCmd ("user.list", ListUsers(0, 100), 5 seconds)).mapTo[ListUsersReply] map { reply =>
      Ok(Json toJson reply)
    }
  }

  def post = Action.async(BodyParsers.parse.json) { req =>
    req.body.validate[PostUser].fold(
      errors => Future {
        BadRequest(JsError toJson errors)
      },
      cmd => {
        primaryCloud.requestCmd("user.post", cmd, 5 seconds).mapTo[PostUserReply] map {
          case PostUserReply(Some(id), 201, None) =>
            Ok(Json toJson id)
          case PostUserReply(None, code, Some(error)) =>
            Status(code)(error)
        }
      }
    )
  }

  def put = Action.async(BodyParsers.parse.json) { req =>
    req.body.validate[PostUser].fold(
      errors => Future {
        BadRequest(JsError toJson errors)
      },
      cmd => {
        primaryCloud.requestCmd("user.put", cmd, 5 seconds).mapTo[PostUserReply] map {
          case PostUserReply(Some(id), code, None) if code >= 200 && code < 300 =>
            Status(code)(Json toJson id)
          case PostUserReply(None, code, Some(error)) =>
            Status(code)(error)
        }
      }
    )
  }
}

