```scala
scala> import cats.data._
import cats.data._

scala> import scala.concurrent.Future
import scala.concurrent.Future

scala> import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext.Implicits.global

scala> val five: XorT[Future, String, Int] = XorT(Future.successful(Xor.right(5)))
five: cats.data.XorT[scala.concurrent.Future,String,Int] = XorT(scala.concurrent.impl.Promise$KeptPromise@63c6f64e)
```
```scala
scala> val ten = five.map(_ * 2)
<console>:18: error: could not find implicit value for parameter F: cats.Functor[scala.concurrent.Future]
 Clippy advises: Did you forget to import cats.std.all._?
       val ten = five.map(_ * 2)
                         ^
```
Import cats.std.all._ to fix the error.  
If you just want to import everything you are likely to need in one go use the imports from
https://github.com/typelevel/cats/blob/master/docs/src/main/tut/faq.md
```scala
scala> import cats.std.all._
import cats.std.all._

scala> val ten = five.map(_ * 2)
ten: cats.data.XorT[scala.concurrent.Future,String,Int] = XorT(List())
```
