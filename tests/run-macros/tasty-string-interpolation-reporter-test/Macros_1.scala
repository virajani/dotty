import scala.quoted._
import scala.quoted.autolift.given
import scala.quoted.matching._

import scala.language.implicitConversions

object Foo {
  implicit object StringContextOps {
    inline def (ctx: => StringContext) foo (args: => Any*): String = ${ Macro.foo('ctx, 'args) }
  }
}


object TestFooErrors { // Defined in tests
  implicit object StringContextOps {
    inline def (ctx: => StringContext) foo (args: => Any*): List[(Int, Int, Int, String)] = ${ Macro.fooErrors('ctx, 'args) }
  }
}

object Macro {

  def foo(sc: Expr[StringContext], argsExpr: Expr[Seq[Any]])(given qctx: QuoteContext): Expr[String] = {
    (sc, argsExpr) match {
      case ('{ StringContext(${ExprSeq(parts)}: _*) }, ExprSeq(args)) =>
        val reporter = new Reporter {
          def errorOnPart(msg: String, partIdx: Int): Unit = {
            import qctx.tasty._
            error(msg, parts(partIdx).unseal.pos)
          }
        }
        fooCore(parts, args, reporter)
    }
  }

  def fooErrors(sc: Expr[StringContext], argsExpr: Expr[Seq[Any]])(given qctx: QuoteContext): Expr[List[(Int, Int, Int, String)]] = {
    (sc, argsExpr) match {
      case ('{ StringContext(${ExprSeq(parts)}: _*) }, ExprSeq(args)) =>
        val errors = List.newBuilder[Expr[(Int, Int, Int, String)]]
        val reporter = new Reporter {
          def errorOnPart(msg: String, partIdx: Int): Unit = {
            import qctx.tasty._
            val pos = parts(partIdx).unseal.pos
            errors += '{ Tuple4($partIdx, ${pos.start}, ${pos.end}, $msg) }
          }
        }
        fooCore(parts, args, reporter) // Discard result
        Expr.ofList(errors.result())
    }


  }


  private def fooCore(parts: Seq[Expr[String]], args: Seq[Expr[Any]], reporter: Reporter)(given QuoteContext): Expr[String] = {
    for ((part, idx) <- parts.zipWithIndex) {
      val Const(v: String) = part
      if (v.contains("#"))
        reporter.errorOnPart("Cannot use #", idx)
    }

    '{ StringContext(${Expr.ofList(parts)}: _*).s(${Expr.ofList(args)}: _*) }
  }


  trait Reporter {
    def errorOnPart(msg: String, partIdx: Int): Unit
  }


}
