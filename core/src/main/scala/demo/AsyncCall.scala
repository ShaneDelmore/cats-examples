import cats.data.{ Xor, XorT }
import scala.concurrent.Future

package object demo {
  /**
    * AsyncCall is a convenience wrapper for the monad transformer XorT as we most commonly use the type
    * XorT[Future, RemoteServiceError, A] with only one of the three types varying.
    */
  type AsyncCall[A] = XorT[Future, ServiceError, A]

  object AsyncCall {

    def apply[A](input: XorT[Future, ServiceError, A]): AsyncCall[A] =
      input

    def apply[A](input: Future[ServiceError Xor A]): AsyncCall[A] =
      XorT(input)

  }
}



