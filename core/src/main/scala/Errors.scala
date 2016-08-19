package demo

sealed abstract class ServiceError
object Errors {
  final case object AccountLocked extends ServiceError
  final case object DuplicateKey extends ServiceError
  final case object NotFound extends ServiceError
}

