package io.kaitai.struct

import io.kaitai.struct.datatype.DataType.{KaitaiStreamType, UserTypeInstream}
import io.kaitai.struct.datatype.{Endianness, FixedEndian, InheritedEndian}
import io.kaitai.struct.format._
import io.kaitai.struct.languages.GoCompiler
import io.kaitai.struct.languages.components.ExtraAttrs

import scala.collection.mutable.ListBuffer

class GoClassCompiler(
  classSpecs: ClassSpecs,
  override val topClass: ClassSpec,
  config: RuntimeConfig
) extends ClassCompiler(classSpecs, topClass, config, GoCompiler) {

  override def compileClass(curClass: ClassSpec): Unit = {
    provider.nowClass = curClass

    val extraAttrs = ListBuffer[AttrSpec]()
    extraAttrs += AttrSpec(List(), IoIdentifier, KaitaiStreamType)
    extraAttrs += AttrSpec(List(), RootIdentifier, UserTypeInstream(topClassName, None))
    extraAttrs += AttrSpec(List(), ParentIdentifier, curClass.parentType)

    extraAttrs ++= getExtraAttrs(curClass)

    if (!curClass.doc.isEmpty)
      lang.classDoc(curClass.name, curClass.doc)

    // Enums declaration defines types, so they need to go first
    compileEnums(curClass)

    // Basic struct declaration
    lang.classHeader(curClass.name)
    compileAttrDeclarations(curClass.seq ++ extraAttrs)
    curClass.instances.foreach { case (instName, instSpec) =>
      compileInstanceDeclaration(instName, instSpec)
    }
    lang.classFooter(curClass.name)

    // Constructor = Read() function
    compileReadFunction(curClass)

    compileInstances(curClass, extraAttrs)

    compileAttrReaders(curClass.seq ++ extraAttrs)

    // Recursive types
    compileSubclasses(curClass)
  }

  def compileReadFunction(curClass: ClassSpec) = {
    lang.classConstructorHeader(
      curClass.name,
      curClass.parentType,
      topClassName,
      curClass.meta.endian.contains(InheritedEndian),
      curClass.params
    )
    // FIXME
    val defEndian = curClass.meta.endian match {
      case Some(fe: FixedEndian) => Some(fe)
      case _ => None
    }
    compileSeq(curClass.seq, new ListBuffer[AttrSpec], defEndian)
    lang.classConstructorFooter
  }

  override def compileInstance(className: List[String], instName: InstanceIdentifier, instSpec: InstanceSpec, extraAttrs: ListBuffer[AttrSpec], endian: Option[Endianness]): Unit = {
    // FIXME: support calculated endianness

    // Determine datatype
    val dataType = instSpec.dataTypeComposite

    if (!instSpec.doc.isEmpty)
      lang.attributeDoc(instName, instSpec.doc)
    lang.instanceHeader(className, instName, dataType, instSpec.isNullable)
    lang.instanceCheckCacheAndReturn(instName)

    instSpec match {
      case vi: ValueInstanceSpec =>
        lang.attrParseIfHeader(instName, vi.ifExpr)
        lang.instanceCalculate(instName, dataType, vi.value)
        lang.attrParseIfFooter(vi.ifExpr)
      case i: ParseInstanceSpec =>
        lang.attrParse(i, instName, extraAttrs, None) // FIXME
    }

    lang.instanceSetCalculated(instName)
    lang.instanceReturn(instName)
    lang.instanceFooter
  }

  def getExtraAttrs(curClass: ClassSpec): List[AttrSpec] = {
    // We want only values of ParseInstances, which are AttrSpecLike.
    // ValueInstances are ignored, as they can't currently generate
    // any extra attributes (i.e. no `size`, no `process`, etc)
    val parseInstances = curClass.instances.values.collect {
      case inst: AttrLikeSpec => inst
    }

    (curClass.seq ++ parseInstances).foldLeft(List[AttrSpec]())(
      (attrs, attr) => attrs ++ ExtraAttrs.forAttr(attr)
    )
  }
}
