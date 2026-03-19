package hytale.xalitoria.codec.extra

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.function.FunctionCodec
import kotlin.uuid.Uuid

object Kodec_Uuid {
    private val CODEC = FunctionCodec(Codec.STRING, { name: String -> Uuid.fromByteArray(name.toByteArray()) }, { obj: Uuid -> obj.toString() })

    val Uuid.Companion.CODEC
        get() = Kodec_Uuid.CODEC
}