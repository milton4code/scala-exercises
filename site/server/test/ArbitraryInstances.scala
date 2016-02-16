import cats.data.Xor
import com.fortysevendeg.exercises.models.UserCreation.Request
import com.fortysevendeg.exercises.models.{ UserCreation, UserDoobieStore }
import com.fortysevendeg.exercises.persistence.domain.SaveUserProgress
import doobie.imports._
import org.scalacheck.{ Gen, Arbitrary }
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Shapeless._
import org.scalatest.Assertions
import shared.User

import scalaz.concurrent.Task

trait ArbitraryInstances extends Assertions {

  // Avoids creating strings with null chars because Postgres text fields don't support it.
  // see http://stackoverflow.com/questions/1347646/postgres-error-on-insert-error-invalid-byte-sequence-for-encoding-utf8-0x0
  implicit val stringArbitrary: Arbitrary[String] =
    Arbitrary(Gen.identifier.map(_.replaceAll("\u0000", "")))

  implicit val userSaveRequestArbitrary: Arbitrary[Request] =
    Arbitrary(for {
      login ← Gen.uuid
      name ← Gen.alphaStr
      githubId ← Gen.uuid
      githubUrl ← Gen.alphaStr
      pictureUrl ← Gen.alphaStr
      email ← Gen.alphaStr
    } yield Request(
      login = login.toString,
      name = name,
      githubId = githubId.toString,
      pictureUrl = pictureUrl,
      githubUrl = githubUrl,
      email = email
    ))

  def persistentUserArbitrary(implicit transactor: Transactor[Task]): Arbitrary[User] = {
    import UserCreation._
    Arbitrary(arbitrary[Request] map { request ⇒
      UserDoobieStore.create(request).transact(transactor).run match {
        case Xor.Right(user) ⇒ user
        case Xor.Left(error) ⇒ fail("Failed generating persistent users : $error")
      }
    })
  }

  case class UserProgressPair(request: SaveUserProgress.Request, user: User)

  implicit def saveUserProgressArbitrary(implicit transactor: Transactor[Task]): Arbitrary[UserProgressPair] = {

    Arbitrary(for {
      user ← persistentUserArbitrary.arbitrary
      request ← {
        arbitrary[SaveUserProgress.Request] map (p ⇒ p.copy(userId = user.id))
      }
    } yield UserProgressPair(request, user))
  }
}

object ArbitraryInstances extends ArbitraryInstances