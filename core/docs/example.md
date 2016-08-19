#Jump straight to the [Motivation](#motivation)

##AsyncCall Tutorial

All code can be copy and pasted into the REPL (via `sbt core/console`) if you want to play along.

This is some setup code that is only relevant if you're following along in the
REPL (If you ever see `AsyncCall(List())`, this means the printing finished before
the `Future` completed. If you see this, re-evaluate the variable, and it should
go away):
```scala
import scala.concurrent.Future
import java.util.concurrent.Executor
import scala.concurrent.ExecutionContext
//Please don't do this in production code!  It is used to execute
//the documentation code (mostly) synchronously to generate more readable output
implicit val synchronousExecutionContext = ExecutionContext.fromExecutor(new Executor {
  def execute(task: Runnable) = task.run()
})
```

###Basic Usage

First import cats goodies and the demo package
```scala
import cats._
// import cats._

import cats.data._
// import cats.data._

import demo._
// import demo._
```

Now let's take a standard Scala Option and convert it to a cats.data.Xor.
When converting an Option to an Xor the Some value will be converted to an Xor.Right
and a None will be converted to an Xor.Left.  You will need to supply the left value to tell cats what to use when a None is encountered
```scala
Option(5).toRightXor(Errors.NotFound)
// <console>:26: error: value toRightXor is not a member of Option[Int]
//  Clippy advises: you may need to import cats.implicits._
//        Option(5).toRightXor(Errors.NotFound)
//                  ^
```
I left an import out to demonstrate using Scala-Clippy to augment compiler messages with library specific error messages.
A common mistake new cats users make is forgetting to import cats.implicits._ and a timely reminder can help make the library easier to use.
Let's add the missing import and try again
```scala
import cats.implicits._
// import cats.implicits._

val xorFive: ServiceError Xor Int = Option(5).toRightXor(Errors.NotFound)
// xorFive: cats.data.Xor[demo.ServiceError,Int] = Right(5)
```
Then create your first AsyncCall instance
```scala
val five: AsyncCall[Int] = AsyncCall(Future(Xor.right(5)))
// five: demo.AsyncCall[Int] = XorT(Success(Right(5)))
```
Remember AsyncCall is a just a type alias for XorT[Future, ServiceError, ?]
```scala
val alsoFive: XorT[Future, ServiceError, Int] = XorT(Future(Xor.right(5)))
// alsoFive: cats.data.XorT[scala.concurrent.Future,demo.ServiceError,Int] = XorT(Success(Right(5)))
```

##AsyncCall Recipes
As we are transforming data remember to scroll to the right and look at the actual data
values the types hold to see how we are transforming them.

First, let setup a database that looks up usernames by id asynchronously.
```scala
case class User(id: Int, name: String, accountActive: Boolean)
// defined class User

object UserDb {
  private val usernames: Map[Int, User] = Map(
    1 -> User(1, "Shane", false),
    2 -> User(2, "Justin", true),
    3 -> User(3, "Josh", true)
  )
  
  //Setup a default notFound, there is no need to create a new one each time as NotFound is a singleton
  val notFound: AsyncCall[User] = AsyncCall(Future(Xor.Left(Errors.NotFound)))

  def findById(id: Int): AsyncCall[User] = {
    usernames.get(id).fold(notFound)(user => AsyncCall(Future(Xor.Right(user))))
  }
}
// defined object UserDb
```


###Make a successful call
```scala
val shane = UserDb.findById(1)
// shane: demo.AsyncCall[User] = XorT(Success(Right(User(1,Shane,false))))
```

###Make a failing call
```scala
val notFound = UserDb.findById(0)
// notFound: demo.AsyncCall[User] = XorT(Success(Left(NotFound)))
```

###Transform the result of a successful call with a local function using map
```scala
def greet(name: String): String = s"Hello $name"
// greet: (name: String)String

shane.map(user => greet(user.name))
// res3: cats.data.XorT[scala.concurrent.Future,demo.ServiceError,String] = XorT(Success(Right(Hello Shane)))
```
Map is right biased and does not transform the left hand side.
If you call it with a left value it will do nothing.
```scala
notFound.map(user => greet(user.name))
// res4: cats.data.XorT[scala.concurrent.Future,demo.ServiceError,String] = XorT(Success(Left(NotFound)))
```

###Recover a left value to a right with a local function using recover
Notice that recover is called with {}
The reason is that you pass a partial function to recover and it will only recover if the function matches.
Is this example we match on case NotFound, and recover to defaultUser.
If the error is something other than NotFound then we do not want to recover it, and the function will do nothing.
```scala
val defaultUser = User(0, "World", false)
// defaultUser: User = User(0,World,false)

val recovered = notFound.recover {
  case Errors.NotFound => defaultUser
}
// recovered: cats.data.XorT[scala.concurrent.Future,demo.ServiceError,User] = XorT(Success(Right(User(0,World,false))))
```
```scala
recovered.map(user => greet(user.name))
// res5: cats.data.XorT[scala.concurrent.Future,demo.ServiceError,String] = XorT(Success(Right(Hello World)))
```
###Recover a left value to a right with an asynchronous function using recoverWith
`recoverWith` behaves like `recover`, but instead of taking a function that returns `A`, it takes a function that
returns `AsyncCall[A]`.
```scala
val recovered = notFound.recoverWith {
  case Errors.NotFound => UserDb.findById(1)
}
// recovered: cats.data.XorT[scala.concurrent.Future,demo.ServiceError,User] = XorT(Success(Right(User(1,Shane,false))))
```

###Transform a right value to a left if a condition is not met using ensure
In This example we are ensuring the account is active, and if not returning AccountLocked 
```scala
val shaneIfActive = shane.ensure(Errors.AccountLocked)(_.accountActive)
// shaneIfActive: cats.data.XorT[scala.concurrent.Future,demo.ServiceError,User] = XorT(Success(Left(AccountLocked)))
```

###Transform a left value with a local function using leftMap
Maybe we don't want to let users know if an account is locked and would prefer to report that it is not found
Again we are using a partial function to only transform the error if it is AccountLocked
```scala
shaneIfActive.leftMap{
  case Errors.AccountLocked => Errors.NotFound //Don't leak security info, if the account is locked pretend it is not found
  case anyOtherError => anyOtherError //Do not map other errors
}
// res6: cats.data.XorT[scala.concurrent.Future,demo.ServiceError,User] = XorT(Success(Left(NotFound)))
```
 
###Make a remote call with the result of a successful call using flatMap.
Now let's setup a primary programming language service.
```scala
object LangDb {
  val userLangs: Map[Int, String] = Map(
    1 -> "Scala",
    2 -> "Jira",
    3 -> "JavaScript"
  )

  def findById(id: Int): AsyncCall[String] = {
    val missing: AsyncCall[String] = AsyncCall(Future(Xor.Left(Errors.NotFound)))
    userLangs.get(id)
      .fold(missing)(lang => AsyncCall(Future(Xor.Right(lang))))
  }
}
```

A common mistake is to use map when trying to transform a response with the results of a remote call.
```scala
val langForShane: AsyncCall[String] = shane.map(user => LangDb.findById(1))
// <console>:33: error: type mismatch;
//  found   : demo.AsyncCall[String]
//     (which expands to)  cats.data.XorT[scala.concurrent.Future,demo.ServiceError,String]
//  required: String
//  Clippy advises: This message may indicate you have an extra layer of nesting and need to flatten your result type.
// 		 This is most commonly caused by calling map instead of flatMap or recover instead of recoverWith when making a remote service call.
//        val langForShane: AsyncCall[String] = shane.map(user => LangDb.findById(1))
//                                                                               ^
```
This results in an extra layer of nesting that you need to flatten out.  
The compiler message is needlessly verbose.  The important parts are:
```
found: AsyncCall[String]
required: String
```
Whenever you see this pattern in your error message it most likely means you need to flatten your result types
using flatMap instead of map or recoverWith instead of recover.
```scala
val langForShane: AsyncCall[String] = shane.flatMap(user => LangDb.findById(user.id))
// langForShane: demo.AsyncCall[String] = XorT(Success(Right(Scala)))
```

Successive calls to flatMap are very common in Scala
```scala
UserDb.findById(1).
  flatMap(user => 
    LangDb.findById(user.id). 
      map(lang => 
        s"${user.name} spends all day in $lang"
      )
    )
// res7: cats.data.XorT[scala.concurrent.Future,demo.ServiceError,String] = XorT(Success(Right(Shane spends all day in Scala)))
```
Chained flatMap calls are often written in the "for comprehension" syntax.
This expression will return the same result as the call above.
```scala
for {
  user <- UserDb.findById(1) //flatMap
  lang <- LangDb.findById(user.id) //map
} yield s"${user.name} spends all day in $lang" 
// res8: cats.data.XorT[scala.concurrent.Future,demo.ServiceError,String] = XorT(Success(Right(Shane spends all day in Scala)))
```
In this case you might have noticed that both async calls we are making are independent of each other.
There is no reason to wait for the find user call to finish when we already know the id we are going to use 
to make the LangDb call.  When you are in this scenario you can improve the performance of the function
by starting both calls concurrently and using a for comprehension or flatMap to bring the results together.
```scala
//Start both service calls in parallel
val userResponse = UserDb.findById(2)
// userResponse: demo.AsyncCall[User] = XorT(Success(Right(User(2,Justin,true))))

val langResponse = LangDb.findById(2)
// langResponse: demo.AsyncCall[String] = XorT(Success(Right(Jira)))

//When both results are finished yield the result
for {
  user <- userResponse
  lang <- langResponse
} yield s"${user.name} spends all day in $lang"
// res11: cats.data.XorT[scala.concurrent.Future,demo.ServiceError,String] = XorT(Success(Right(Justin spends all day in Jira)))
```
###Make a series of remote calls and get back an AsyncCall that allows you to deal with them like a single async call
If you have an api that you would like to make a batch call to but the api does not support that functionality
it can be convenient to sequence your remote calls.
For example, if I want to get users 1-3 in my UserDb I will have to make three calls as the interface does not support
supplying a set of userIds to query by.
```scala
val allUsers: Vector[AsyncCall[User]] = (1 to 3).map(UserDb.findById).toVector
// allUsers: Vector[demo.AsyncCall[User]] = Vector(XorT(Success(Right(User(1,Shane,false)))), XorT(Success(Right(User(2,Justin,true)))), XorT(Success(Right(User(3,Josh,true)))))
```
This is difficult to work with, now I have a list of futures and don't even know if they have all completed yet or not.
We would prefer an AsyncCall[Vector[User]] instead.
```scala
val betterAllUsers: AsyncCall[Vector[User]] = allUsers.sequence
// betterAllUsers: demo.AsyncCall[Vector[User]] = XorT(Success(Right(Vector(User(1,Shane,false), User(2,Justin,true), User(3,Josh,true)))))
```
#Motivation
An `AsyncCall` combines the functionality of several different types with nice properties. Namely `Xor` which we use
for functional error handling and `Future` which we use for asynchronous functions.

##Xor
`Xor` is a type that helps to do smarter functional error handling. `Xor` behaves a lot like an `Option`, but instead
having a `None` state, it can give a reason for why something failed. `Xor` has two mutually exclusive branches:
Conventionally `Xor.Right` is used when things go as exepected, and `Xor.Left` is used when something goes wrong.
Typically we use an `ServiceError` in the `Xor.Left` case.

```scala
def findById(id: Int): Xor[ServiceError, String] =
  LangDb.userLangs.get(id) match {
    case Some(lang) => Xor.Right(lang)
    case None       => Xor.Left(Errors.NotFound)
  }
```
```scala
  findById(1)
// res12: cats.data.Xor[demo.ServiceError,String] = Right(Scala)

  findById(4)
// res13: cats.data.Xor[demo.ServiceError,String] = Left(NotFound)
```

##Future
`Future` is a type that allows for asynchronous computation—it is a Scala wrapper for a `Promise`.
In our code base, we use a `Future` when performing external calls,
to a database or to another service.

The compiler needs an execution context to be able to perform asynchronous operations.
Typically we do this by importing an implicit execution context:

```scala
def findById(id: Int): Future[String] =
  Future(LangDb.userLangs(id))
```
```scala
  findById(1)
// res14: scala.concurrent.Future[String] = Success(Scala)

  findById(4)
// res15: scala.concurrent.Future[String] = Failure(java.util.NoSuchElementException: key not found: 4)
```

##Future[Xor[...]]
What we would like to do in practice is use a type that combines the functional error handling of `Xor` with the ]
asynchronicity of `Future`. The problem is that this makes it tedious to transform data.

For example, if we want to increment the value inside of an asynchronous `Xor`, we have to `map` twice:
```scala
val futureSeven = Future(Xor.Right(7): Xor[ServiceError, Int])
// futureSeven: scala.concurrent.Future[cats.data.Xor[demo.ServiceError,Int]] = Success(Right(7))

futureSeven.map { xorSeven => xorSeven.map { seven => seven + 1 } }
// res16: scala.concurrent.Future[cats.data.Xor[demo.ServiceError,Int]] = Success(Right(8))
```

##AsyncCall
```scala
```
`AsyncCall` is a type we use to combine an `Xor` and a `Future` in order to avoid having to `map` inside of a `map`.

```scala
val AsyncCallSeven: AsyncCall[Int] = AsyncCall(futureSeven)
// AsyncCallSeven: demo.AsyncCall[Int] = XorT(Success(Right(7)))

val AsyncCallEight = AsyncCallSeven.map { seven => seven + 1 }
// AsyncCallEight: cats.data.XorT[scala.concurrent.Future,demo.ServiceError,Int] = XorT(Success(Right(8)))
```

If, for some reason, we want to get back to a `Future[Xor[ServiceError, Int]]`, we can take the `value` of the `AsyncCall`:
```scala
AsyncCallEight.value
// res17: scala.concurrent.Future[cats.data.Xor[demo.ServiceError,Int]] = Success(Right(8))
```

##AsyncCall
We use `AsyncCall[Future, ServiceError, A]` (where `A` is any arbitrary type) so frequently, that we have given it a
special name: `AsyncCall`. `AsyncCall` is just an alias to an `AsyncCall`; this is why when you make an `AsyncCall` the
compiler will show you the type as `AsyncCall`.

```scala
AsyncCall(futureSeven)
// res18: demo.AsyncCall[Int] = XorT(Success(Right(7)))
```

