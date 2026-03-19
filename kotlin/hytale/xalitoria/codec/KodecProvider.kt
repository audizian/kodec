package hytale.xalitoria.codec

import com.google.devtools.ksp.processing.*

class KodecProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        KodecProcessor(environment.codeGenerator, environment.logger)
}