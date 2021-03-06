/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql
package catalyst
package expressions

import org.apache.spark.sql.catalyst.types._

/**
 * Returns the item at `ordinal` in the Array `child` or the Key `ordinal` in Map `child`.
 */
case class GetItem(child: Expression, ordinal: Expression) extends Expression {
  type EvaluatedType = Any

  val children = child :: ordinal :: Nil
  /** `Null` is returned for invalid ordinals. */
  override def nullable = true
  override def references = children.flatMap(_.references).toSet
  def dataType = child.dataType match {
    case ArrayType(dt) => dt
    case MapType(_, vt) => vt
  }
  override lazy val resolved =
    childrenResolved &&
    (child.dataType.isInstanceOf[ArrayType] || child.dataType.isInstanceOf[MapType])

  override def toString = s"$child[$ordinal]"

  override def apply(input: Row): Any = {
    if (child.dataType.isInstanceOf[ArrayType]) {
      val baseValue = child.apply(input).asInstanceOf[Seq[_]]
      val o = ordinal.apply(input).asInstanceOf[Int]
      if (baseValue == null) {
        null
      } else if (o >= baseValue.size || o < 0) {
        null
      } else {
        baseValue(o)
      }
    } else {
      val baseValue = child.apply(input).asInstanceOf[Map[Any, _]]
      val key = ordinal.apply(input)
      if (baseValue == null) {
        null
      } else {
        baseValue.get(key).orNull
      }
    }
  }
}

/**
 * Returns the value of fields in the Struct `child`.
 */
case class GetField(child: Expression, fieldName: String) extends UnaryExpression {
  type EvaluatedType = Any

  def dataType = field.dataType
  def nullable = field.nullable

  protected def structType = child.dataType match {
    case s: StructType => s
    case otherType => sys.error(s"GetField is not valid on fields of type $otherType")
  }

  lazy val field =
    structType.fields
        .find(_.name == fieldName)
        .getOrElse(sys.error(s"No such field $fieldName in ${child.dataType}"))

  lazy val ordinal = structType.fields.indexOf(field)

  override lazy val resolved = childrenResolved && child.dataType.isInstanceOf[StructType]

  override def apply(input: Row): Any = {
    val baseValue = child.apply(input).asInstanceOf[Row]
    if (baseValue == null) null else baseValue(ordinal)
  }

  override def toString = s"$child.$fieldName"
}
