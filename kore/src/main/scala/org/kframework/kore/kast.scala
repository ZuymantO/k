// Copyright (c) 2014 K Team. All Rights Reserved.

package org.kframework.kore

import org.kframework._

import collection.JavaConverters._
import collection.LinearSeq
import KORE._
import scala.collection.AbstractIterator
import scala.collection.IterableLike
import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.Builder
import scala.collection.mutable.ListBuffer

/* Interfaces */

sealed trait KORE // marker for KORE final classes added as a result of a discussion with Brandon about sealing

trait HasAttributes {
  def att: Attributes
}

trait K extends HasAttributes {
  protected type This <: K

  def copy(att: Attributes): This
}

trait KItem extends K

trait KLabel extends KLabelToString {
  val name: String
}

trait KToken extends KItem with KORE with KTokenToString {
  val sort: Sort
  val s: String
}

trait Sort extends SortToString {
  def name: String
}

/* Data Structures */

class KList(protected[kore] val delegate: List[K])
  extends KAbstractCollection with Indexed[Int, K]
  with KListToString with KORE {
  type This = KList

  override def canEqual(that: Any) = that.isInstanceOf[KList]
  def newBuilder(): Builder[K, KList] = KList.newBuilder(att)

  def get(i: Int) = delegate.lift(i)
  def att = Attributes()

  def copy(att: Attributes): KList = this
}

case class KApply(val klabel: KLabel, val klist: KList, val att: Attributes = Attributes())
  extends KAbstractCollection with Indexed[Int, K]
  with KApplyToString with KORE with Equals {
  type This = KApply

  protected[kore] def delegate: Iterable[K] = klist.delegate

  def get(i: Int) = klist.get(i)

  def newBuilder: Builder[K, KApply] = klist.newBuilder() mapResult { new KApply(klabel, _, att) }

  override def canEqual(that: Any) = that match {
    case t: KApply => t.klabel == klabel
    case _ => false
  }

  def copy(att: Attributes): KApply = KApply(klabel, klist, att)
}

case class KUninterpretedToken(sort: Sort, s: String, override val att: Attributes = Attributes())
  extends KToken with KTokenToString with KORE {
  type This = KToken
  def copy(att: Attributes): KToken = new KUninterpretedToken(sort, s, att)
}

case class ConcreteKLabel(name: String) extends KLabel with KORE {
  def apply(ks: K*) = new KApply(this, KList(ks))
}

case class KSequence(val ks: List[K], val att: Attributes = Attributes())
  extends KAbstractCollection with KSequenceToString with KORE {
  type This = KSequence
  def delegate = ks

  def newBuilder(): Builder[K, KSequence] = KSequence.newBuilder(att)
  def copy(att: Attributes): KSequence = new KSequence(ks, att)

  def canEqual(that: Any) = that.isInstanceOf[KSequence]
}

case class KVariable(name: String, att: Attributes = Attributes())
  extends KItem with KORE with KLabel with KVariableToString {
  type This = KVariable
  def copy(att: Attributes): KVariable = new KVariable(name, att)
}

case class KRewrite(left: K, right: K, att: Attributes = Attributes())
  extends KAbstractCollection with KORE with KRewriteToString {
  type This = KRewrite
  def copy(att: Attributes): KRewrite = new KRewrite(left, right, att)
  val klist = KList(left, right)

  protected[kore] def delegate: Iterable[K] = Seq(left, right)

  def newBuilder: Builder[K, KRewrite] = ListBuffer[K]() mapResult {
    case List(left, right) => KRewrite(left, right)
  }
}

/*  Constructors */

object KList extends CanBuildKCollection {
  type This = KList

  def apply(l: Iterable[K]): KList = (newBuilder() ++= l).result()

  def newBuilder(att: Attributes = Attributes()): Builder[K, KList] =
    new AssocBuilder[K, List[K], KList](ListBuffer()) mapResult { new KList(_) }

  def unapplySeq(l: KList): Option[Seq[K]] = Some(l.delegate.toSeq)
}

object KToken {
  def apply(sort: Sort, s: String, att: Attributes = Attributes()) =
    KUninterpretedToken(sort, s, att)

  def apply(sort: Sort, s: String) =
    KUninterpretedToken(sort, s, Attributes())

  def unapply(t: KToken) = Some((t.sort, t.s, t.att))
}

object KVariable {
  val it = this
}

object KSequence extends CanBuildKCollection {
  type This = KSequence

  def newBuilder(att: Attributes = Attributes()) =
    new AssocBuilder[K, List[K], KSequence](ListBuffer())
      .mapResult { new KSequence(_, att) }
}

object KRewrite {
  def apply(klist: KList, att: Attributes): KRewrite = klist match {
    case KList(left, right) => new KRewrite(left, right, att)
  }
  def apply(list: K*): KRewrite = apply(KList(list: _*), Attributes())
}

object EmptyK {
  def apply() = KSequence(List(), Attributes())
}

object KLabel extends ConcreteKLabel("KLabel") {
  def apply(name: String) = ConcreteKLabel(name)
  def unapply(klabel: ConcreteKLabel): Option[String] = Some(klabel.name)
}

/* Constructors for matching KORE */

object KLabelWithQuotes {
  def apply(s: String) = {
    KLabel(s.stripPrefix("`").stripSuffix("`"))
  }
}

case class UninterpretedSort(name: String) extends Sort

object Sort {
  def apply(s: String) = UninterpretedSort(s)
  def unapply(s: Sort): Option[String] = Some(s.name)
}

object KORE {
  implicit def seqOfKsToKList(ks: Seq[K]) = KList(ks: _*)

  implicit def SymbolToKLabel(s: Symbol) = KLabel(s.name)
}
