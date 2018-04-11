package io.kaitai.struct.translators

import io.kaitai.struct.datatype.DataType._
import io.kaitai.struct.exprlang.Ast
import io.kaitai.struct.exprlang.Ast.expr
import io.kaitai.struct.format.Identifier
import io.kaitai.struct.languages.RustCompiler
import io.kaitai.struct.{RuntimeConfig, Utils}

class RustTranslator(provider: TypeProvider, config: RuntimeConfig) extends BaseTranslator(provider) {
  override def doByteArrayLiteral(arr: Seq[Byte]): String =
    "vec!([" + arr.map((x) =>
    	     "0x%0.2X".format(x & 0xff)
    ).mkString(", ") + "])"
  override def doByteArrayNonLiteral(elts: Seq[Ast.expr]): String =
    s"pack('C*', ${elts.map(translate).mkString(", ")})"

  override val asciiCharQuoteMap: Map[Char, String] = Map(
    '\t' -> "\\t",
    '\n' -> "\\n",
    '\r' -> "\\r",
    '"' -> "\\\"",
    '\\' -> "\\\\"
  )

  override def strLiteralUnicode(code: Char): String =
    "\\u{%x}".format(code.toInt)

  override def numericBinOp(left: Ast.expr, op: Ast.operator, right: Ast.expr) = {
    (detectType(left), detectType(right), op) match {
      case (_: IntType, _: IntType, Ast.operator.Div) =>
        s"${translate(left)} / ${translate(right)}"
      case (_: IntType, _: IntType, Ast.operator.Mod) =>
        s"${translate(left)} % ${translate(right)}"
      case _ =>
        super.numericBinOp(left, op, right)
    }
  }

  override def doLocalName(s: String) = {
    s match {
      case Identifier.ITERATOR => "tmpa"
      case Identifier.ITERATOR2 => "tmpb"
      case Identifier.INDEX => "i"
      case _ => s"self.${doName(s)}"
    }
  }

  override def doName(s: String) = s

  override def doEnumByLabel(enumTypeAbs: List[String], label: String): String = {
    val enumClass = types2classAbs(enumTypeAbs)
    s"$enumClass::${label.toUpperCase}"
  }
  override def doEnumById(enumTypeAbs: List[String], id: String) =
    // Just an integer, without any casts / resolutions - one would have to look up constants manually
    id

  override def doSubscript(container: expr, idx: expr): String =
    s"${translate(container)}[${translate(idx)}]"
  override def doIfExp(condition: expr, ifTrue: expr, ifFalse: expr): String =
    "if " + translate(condition) +
    	" { " + translate(ifTrue) + " } else { " +
	translate(ifFalse) + "}"

  // Predefined methods of various types
  override def strConcat(left: Ast.expr, right: Ast.expr): String =
    "format!(\"{}{}\", " + translate(left) + ", " + translate(right) + ")"

  override def strToInt(s: expr, base: expr): String =
    s"intval(${translate(s)}, ${translate(base)})"

  override def enumToInt(v: expr, et: EnumType): String =
    translate(v)

  override def boolToInt(v: expr): String =
    s"intval(${translate(v)})"

  override def floatToInt(v: expr): String =
    s"intval(${translate(v)})"

  override def intToStr(i: expr, base: expr): String = {
    val baseStr = translate(base)
    baseStr match {
      case "10" =>
        s"strval(${translate(i)})"
      case _ =>
        s"base_convert(strval(${translate(i)}), 10, $baseStr)"
    }
  }
  override def bytesToStr(bytesExpr: String, encoding: Ast.expr): String =
    s"${RustCompiler.kstreamName}::bytesToStr($bytesExpr, ${translate(encoding)})"
  override def bytesLength(b: Ast.expr): String =
    s"strlen(${translate(b)})"
  override def strLength(s: expr): String =
    s"strlen(${translate(s)})"
  override def strReverse(s: expr): String =
    s"strrev(${translate(s)})"
  override def strSubstring(s: expr, from: expr, to: expr): String =
    s"${translate(s)}.substring(${translate(from)}, ${translate(to)})"

  override def arrayFirst(a: expr): String =
    s"${translate(a)}.first()"
  override def arrayLast(a: expr): String =
    s"${translate(a)}.last()"
  override def arraySize(a: expr): String =
    s"${translate(a)}.len()"
  override def arrayMin(a: Ast.expr): String =
    s"${translate(a)}.iter().min()"
  override def arrayMax(a: Ast.expr): String =
    s"${translate(a)}.iter().max()"

  def types2classAbs(names: List[String]) =
    names match {
      case List("kaitai_struct") => RustCompiler.kstructName
      case _ => RustCompiler.types2classRel(names)
    }
}
