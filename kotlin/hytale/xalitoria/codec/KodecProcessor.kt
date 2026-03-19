package hytale.xalitoria.codec

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Visibility

class KodecProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver
            .getSymbolsWithAnnotation(Kodec::class.qualifiedName!!)
            .mapNotNull { ann -> (ann as? KSClassDeclaration)?.takeIf { it.parentDeclaration == null } }
            .forEach { klass -> try {
                val packageName = klass.packageName.asString()

                val imports = mutableSetOf(
                    "com.hypixel.hytale.codec.Codec",
                    "com.hypixel.hytale.codec.KeyedCodec",
                    "com.hypixel.hytale.codec.builder.BuilderCodec",
                ).collectImports(klass, packageName)

                klass.primaryConstructor
                    .takeIf { it != null && it.parameters.any(KSValueParameter::hasDefault) }
                    ?: run {
                        logger.error(
                            "@Kodec data class " + klass.simpleName.asString() +
                            "() must provide default values for all constructor parameters",
                            klass
                        )
                    }

                codeGenerator.createNewFile(
                    Dependencies(false, klass.containingFile!!),
                    packageName,
                    PREFIX + klass.simpleName.asString()
                ).writer().use { w ->
                    w.appendLine("package $packageName")
                    w.appendLine()
                    imports.sorted().forEach { w.appendLine("import $it") }
                    w.appendLine()
                    w.generateKodecObject(klass, packageName, "", true)
                }
            } catch (ex: Exception) {
                logger.error("Failed to generate codec for " + klass.simpleName.asString() + ": \n" + ex.message, klass)
            }   }
        return emptyList()
    }

    private fun Appendable.generateKodecObject(
        klass: KSClassDeclaration,
        packageName: String,
        indent: String,
        isRoot: Boolean
    ) {
        val localName = klass.qualifiedName!!.asString().removePrefix("$packageName.")
        val objectName = if (isRoot) PREFIX else { "" } + klass.simpleName.asString()

        if (isRoot) appendLine("@Suppress(\"ClassName\")")
        appendLine("${indent}object $objectName {")
        appendLine("$indent\tprivate val CODEC: BuilderCodec<$localName> =")

        val ctorRef = if (localName.contains(".")) localName.substringBeforeLast(".") + "::" + localName.substringAfterLast(".") else "::$localName"
        appendLine("$indent\t\tBuilderCodec.builder($localName::class.java, $ctorRef)")

        klass.getAllProperties().filter(::checkMutable).forEach { prop ->
            appendLine("$indent\t\t\t.append(")
            val cprop = prop.codecProp
            appendLine(indent + cprop.doNew)
            appendLine(indent + cprop.doSet)
            appendLine(indent + cprop.doGet)
            appendLine("$indent\t\t\t).add()")
        }

        appendLine("$indent\t\t\t.build()")
        appendLine()

        appendLine("$indent\tval $localName.Companion$EXTENSION: BuilderCodec<$localName>")
        appendLine("$indent\t\tget() = $objectName$EXTENSION")
        appendLine()

        klass.declarations
            .filterIsInstance<KSClassDeclaration>()
            .filter(::isKodec)
            .forEach { nested ->
                appendLine()
                generateKodecObject(nested, packageName, "$indent\t", false)
            }

        appendLine("$indent}")
    }

    private data class CodecProp(
        var codec: String,
        var isIterable: Boolean = false,
        var doNew: String? = null,
        var doSet: String? = null,
        var doGet: String? = null
    )

    companion object {
        private const val PREFIX = "Kodec_"
        private const val EXTENSION = ".CODEC"

        private fun basicCodec(prop: KSPropertyDeclaration, codecName: String): CodecProp  {
            val propName = prop.simpleName.asString()
            return CodecProp(codecName, false,
                "\t\t\t\tKeyedCodec(\"${prop.serialName}\", $codecName),",
                "\t\t\t\t{ c, v -> c.$propName = v },",
                "\t\t\t\t{ c -> c.$propName }",
            )
        }

        private fun arrayCodec(prop: KSPropertyDeclaration, codecName: String): CodecProp  {
            val propName = prop.simpleName.asString()
            return CodecProp(codecName, true,
                "\t\t\t\tKeyedCodec(\"${prop.serialName}\", $codecName),",
                if (prop.isMutable)
                "\t\t\t\t{ c, v -> c.$propName = v },"
                else
                "\t\t\t\t{ c, v -> c.$propName.forEachIndexed { i, _ -> c.$propName[i] = v[i] } },",
                "\t\t\t\t{ c -> c.$propName }"
            )
        }

        @OptIn(KspExperimental::class)
        private fun isKodec(decl: KSDeclaration) = decl.isAnnotationPresent(Kodec::class)

        private val KSType.codecPrimitive: String get() {
            val decl = declaration
            val qName = decl.qualifiedName!!.asString()
            fun nestedName() = qName.replace(decl.packageName.asString() + ".", "") + EXTENSION
            return when (decl) {
                is KSTypeAlias -> if (isKodec(decl)) nestedName() else decl.type.resolve().codecPrimitive
                is KSClassDeclaration if isKodec(decl) -> nestedName()
                else -> when (qName) {
                    "kotlin.String" -> "Codec.STRING"
                    "kotlin.Boolean" -> "Codec.BOOLEAN"
                    "kotlin.Double" -> "Codec.DOUBLE"
                    "kotlin.Float" -> "Codec.FLOAT"
                    "kotlin.Byte" -> "Codec.BYTE"
                    "kotlin.Short" -> "Codec.SHORT"
                    "kotlin.Int" -> "Codec.INTEGER"
                    "kotlin.Long" -> "Codec.LONG"
                    "java.util.UUID" -> "Codec.UUID_STRING"
                    else -> error("No codec for type $qName")
                }
            }
        }

        private val KSPropertyDeclaration.serialName: String get() {
            val result = annotations
                .find { it.annotationType.resolve().declaration.qualifiedName?.asString() == "kotlinx.serialization.SerialName" }
                ?.arguments
                ?.find { it.name?.asString() == "value" || it.name == null }
                ?.value as? String
                ?: simpleName.asString()

            return result
                .replaceFirstChar(Char::uppercaseChar)
                .let { Regex("[-_.]([a-z])").replace(it) { r -> r.groupValues[1].uppercase() } }
                .replace(Regex("[-_.]"), "")
        }

        private val KSPropertyDeclaration.codecProp: CodecProp get() {
            val type = type.resolve()
            val decl = type.declaration
            val noAnn = !isKodec(decl)
            if (decl is KSTypeAlias && noAnn) return mapPrimitiveCodec(this, decl.type.resolve())
            if (type.declaration !is KSClassDeclaration || noAnn) return mapPrimitiveCodec(this, type)
            val nestedName = decl.qualifiedName!!.asString().replace(decl.packageName.asString() + ".", "")
            val propName = simpleName.asString()
            return CodecProp(
                decl.simpleName.asString() + EXTENSION, false,
                "\t\t\t\tKeyedCodec(\"$serialName\", $nestedName$EXTENSION),",
                "\t\t\t\t{ c, v -> c.$propName = v },",
                "\t\t\t\t{ c -> c.$propName }"
            )
        }

        private val KSDeclaration.rootDeclaration: KSDeclaration get() {
            var current = parentDeclaration
            while (true) current = current?.parentDeclaration ?: return current ?: this
        }

        private fun mapPrimitiveCodec(
            prop: KSPropertyDeclaration,
            type: KSType
        ): CodecProp {
            val parentName = prop.parentDeclaration!!.simpleName.asString()
            val propName = prop.simpleName.asString()
            val declName = type.declaration.simpleName.asString()
            return when (val qName = type.declaration.qualifiedName?.asString()) {
                "kotlin.String" -> basicCodec(prop, "Codec.STRING")
                "kotlin.Boolean" -> basicCodec(prop, "Codec.BOOLEAN")
                "kotlin.Double" -> basicCodec(prop, "Codec.DOUBLE")
                "kotlin.Float" -> basicCodec(prop, "Codec.FLOAT")
                "kotlin.Byte" -> basicCodec(prop, "Codec.BYTE")
                "kotlin.Short" -> basicCodec(prop, "Codec.SHORT")
                "kotlin.Int" -> basicCodec(prop, "Codec.INTEGER")
                "kotlin.Long" -> basicCodec(prop, "Codec.LONG")
                "java.util.UUID" -> basicCodec(prop, "Codec.UUID_STRING")

                "kotlin.DoubleArray" -> arrayCodec(prop, "Codec.DOUBLE_ARRAY")
                "kotlin.FloatArray" -> arrayCodec(prop, "Codec.FLOAT_ARRAY")
                "kotlin.IntArray" -> arrayCodec(prop, "Codec.INT_ARRAY")
                "kotlin.LongArray" -> arrayCodec(prop, "Codec.LONG_ARRAY")

                "kotlin.Array", "kotlin.collections.ArrayList" -> {
                    val typeArg = type.arguments.firstOrNull()?.type?.resolve() ?: error("$declName must have a type argument")
                    CodecProp(
                        "com.hypixel.hytale.codec.codecs.array.ArrayCodec", true,
                        "\t\t\t\tKeyedCodec(\"${prop.serialName}\", com.hypixel.hytale.codec.codecs.array.ArrayCodec(${typeArg.codecPrimitive}, $parentName().$propName)),",
                        if (prop.isMutable)
                        "\t\t\t\t{ c, v -> c.$propName = v },"
                        else
                        "\t\t\t\t{ c, v -> c.$propName.forEachIndexed { i, _ -> c.$propName[i] = v[i] } },",
                        "\t\t\t\t{ c -> c.$propName }"
                    )
                }

                "kotlin.collections.MutableList", "kotlin.collections.List" -> {
                    val typeArg = type.arguments.firstOrNull()?.type?.resolve() ?: error("$declName must have a type argument")
                    CodecProp(
                        "com.hypixel.hytale.codec.codecs.array.ArrayCodec", true,
                        "\t\t\t\tKeyedCodec(\"${prop.serialName}\", com.hypixel.hytale.codec.codecs.array.ArrayCodec(${typeArg.codecPrimitive}) { $parentName().$propName.toTypedArray() }),",
                        if (qName != "kotlin.collections.MutableList" || prop.isMutable)
                        "\t\t\t\t{ c, v -> c.$propName = v },"
                        else
                        "\t\t\t\t{ c, v -> c.$propName.clear(); c.$propName.addAll(v) },",
                        "\t\t\t\t{ c -> c.$propName.toTypedArray() }"
                    )
                }

                "kotlin.collections.MutableSet", "kotlin.collections.Set" -> {
                    val typeArg = type.arguments.firstOrNull()?.type?.resolve() ?: error("$declName must have a type argument")
                    val unmod = qName == "kotlin.collections.Set"
                    CodecProp(
                        "com.hypixel.hytale.codec.codecs.set.SetCodec", true,
                        "\t\t\t\tKeyedCodec(\"${prop.serialName}\", com.hypixel.hytale.codec.codecs.set.SetCodec(${typeArg.codecPrimitive}, { $parentName().$propName${if (unmod) "" else ".toSet()"} }, $unmod)),",
                        if (unmod || prop.isMutable)
                        "\t\t\t\t{ c, v -> c.$propName = v },"
                        else
                        "\t\t\t\t{ c, v -> c.$propName.clear(); c.$propName.addAll(v) },",
                        "\t\t\t\t{ c -> c.$propName }"
                    )
                }

                "kotlin.collections.MutableMap", "kotlin.collections.Map" -> {
                    val keyType = type.arguments.getOrNull(0)?.type?.resolve() ?: error("$declName must have key type argument")
                    val valueType = type.arguments.getOrNull(1)?.type?.resolve() ?: error("$declName must have value type argument")
                    val unmod = qName == "kotlin.collections.Map"
                    CodecProp(
                        "com.hypixel.hytale.codec.codecs.map.MapCodec", true,
                        "\t\t\t\tKeyedCodec(\"${prop.serialName}\", com.hypixel.hytale.codec.codecs.map.MapCodec(${keyType.codecPrimitive}, { ${valueType.codecPrimitive} }, $unmod)),",
                        if (unmod || prop.isMutable)
                        "\t\t\t\t{ c, v -> c.$propName = v },"
                        else
                        "\t\t\t\t{ c, v -> c.$propName.clear(); c.$propName.putAll(v) },",
                        "\t\t\t\t{ c -> c.$propName }"
                    )
                }

                else -> error("No Codec mapping for property '${prop.serialName}' of type '$qName'")
            }
        }

        private fun MutableSet<String>.collectImports(
            klass: KSClassDeclaration,
            currentPackage: String
        ): MutableSet<String> {
            // Collect for this class
            klass.getAllProperties()
                .filter(::checkMutable)
                .forEach { prop -> collectImports(prop.type.resolve(), currentPackage) }

            // Recurse into nested @Kodec classes
            klass.declarations
                .filterIsInstance<KSClassDeclaration>()
                .filter(::isKodec)
                .forEach { nested -> collectImports(nested, currentPackage) }
            return this
        }

        private fun MutableSet<String>.collectImports(
            type: KSType,
            currentPackage: String
        ): MutableSet<String> {

            // Add import for @Kodec annotated types (if not in same package)
            (type.declaration as? KSClassDeclaration)?.takeIf(::isKodec)?.let { decl ->
                decl.packageName.asString()
                    .takeIf { it != currentPackage }
                    ?.let { add(it + "." + decl.simpleName.asString()) }
                val root = decl.rootDeclaration.simpleName.asString()
                add(decl.qualifiedName!!.asString().replace(root, PREFIX + root) + EXTENSION)
            }

            when (type.declaration.qualifiedName?.asString()) {
                "kotlin.Array",
                "kotlin.collections.ArrayList",
                "kotlin.collections.List",
                "kotlin.collections.MutableList",
                "kotlin.collections.Set",
                "kotlin.collections.MutableSet" -> {
                    type.arguments.firstOrNull()?.type?.resolve()?.let { elementType ->
                        collectImports(elementType, currentPackage)
                    }
                }
                "kotlin.collections.Map",
                "kotlin.collections.MutableMap" -> {
                    type.arguments.getOrNull(0)?.type?.resolve()?.let { keyType ->
                        collectImports(keyType, currentPackage)
                    }
                    type.arguments.getOrNull(1)?.type?.resolve()?.let { valueType ->
                        collectImports(valueType, currentPackage)
                    }
                }
            }
            return this
        }

        private val transients = arrayOf("kotlinx.serialization.Transient", "kotlin.jvm.Transient")
        private fun checkMutable(prop: KSPropertyDeclaration): Boolean =
            prop.hasBackingField &&
            prop.getVisibility() != Visibility.PRIVATE &&
            !prop.annotations.any { ann ->
                ann.annotationType.resolve().declaration.qualifiedName?.asString() in transients
            }
    }
}