```tut
import cats.data._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

val five: XorT[Future, String, Int] = XorT(Future.successful(Xor.right(5)))
```
```tut:fail
val ten = five.map(_ * 2)
```
Import cats.std.all._ to fix the error.  
If you just want to import everything you are likely to need in one go use the imports from
https://github.com/typelevel/cats/blob/master/docs/src/main/tut/faq.md
```tut
import cats.std.all._
val ten = five.map(_ * 2)
```