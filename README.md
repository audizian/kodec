## `@Kodec`

`@Kodec` marks a Kotlin class for compile-time codec generation using KSP.
For each root `@Kodec`-annotated class, the processor generates a `BuilderCodec<T>` wiring up the class’s (supported) mutable properties to keyed codecs.

The main generated entrypoint is a companion extension property named `CODEC`, so you typically use it as `YourType.CODEC`.

## Get started

Add the following to your `build.gradle.kts`

```kotlin
plugins { 
    id("com.google.devtools.ksp") version "2.3.5"
}

repositories {
    mavenCentral()
    maven("https://maven.xali.host/releases")
}

dependencies {
    val kodec = "hytale.xalitoria:kodec:{latest-version}"
    implementation(kodec)
    ksp(kodec)
}
```

## What gets generated

For a root `@Kodec` class `MyType` (top-level annotation target, i.e., `parentDeclaration == null`):

1. A generated file/object named `Kodec_MyType` is created in the same package as `MyType`.
2. A companion extension is generated:
   - `val MyType.Companion.CODEC: BuilderCodec<MyType>`

Nested `@Kodec` classes inside the root type are also supported; the processor generates nested objects under `Kodec_<RootType>`.

## Requirements (class + properties)

### Class shape

- The class is expected to be a Kotlin type with a primary constructor (commonly a `data class`).
- The processor checks that the primary constructor exists and that constructor parameters support decoding defaults (see “Constructor defaults” below).

### Properties that are included in the codec

Only “mutable backing-field” properties are encoded/decoded:

- must be `var` (so the declaration has a backing field)
- must not be `private`
- must not be annotated with either:
  - `kotlinx.serialization.Transient`
  - `kotlin.jvm.Transient`

Properties that don’t match these rules are ignored by the generated codec.

## Constructor defaults

The generated codec relies on the class constructor for instantiation during decoding.
To keep decoding safe, provide default values for constructor parameters (or otherwise ensure the generated codec can construct the type when fields are missing).

## Key names and `@SerialName`

For each included property, the keyed name comes from `@kotlinx.serialization.SerialName` when present.
If `@SerialName` is not provided, the property name is used.

The processor normalizes the key by converting common delimiters (`-`, `_`, `.`) into a PascalCase-ish form:

- `my-custom-key` -> `MyCustomKey`

This is done to satisfy Hytale's naming schema convention. 
However, pascal-case was decided upon before observing into the actual name requirement; the first letter must be capitalized. 
This behavior may change in the compiler version.



## Supported property types

The processor maps Kotlin types to codecs as follows:

- Primitives: `String`, `Boolean`, `Double`, `Float`, `Byte`, `Short`, `Int`, `Long`
- UUID:
  - `java.util.UUID`
  - `kotlin.uuid.Uuid`<sup>*</sup>
- Arrays:
  - `DoubleArray`, `FloatArray`, `IntArray`, `LongArray`
- Collections:
  - `Array<T>` / `ArrayList<T>` / `List<T>` / `MutableList<T>`
  - `Set<T>` / `MutableSet<T>`
  - `Map<K, V>` / `MutableMap<K, V>`
- Custom types:
  - If the property type is another `@Kodec`-annotated class, the generated codec will recurse into it.

<sup>*</sup> - not tested

## Example 

```kotlin
@Serializable
@Kodec
data class CustomWorldConfig(
    @SerialName("auto-respawn")
    var autoRespawn: Boolean = false,
    var broadcast: Broadcast = Broadcast(),
    @SerialName("random-teleport")
    var randomTeleport: RandomTeleport = RandomTeleport(),
    @SerialName("spawn-protection")
    var spawnProtection: SpawnProtection = SpawnProtection(),
) {
    @Serializable
    @Kodec
    data class Broadcast(
        var join: String? = null,
        var leave: String? = null,
        var player: String? = null,
    )

    @Serializable
    @Kodec
    data class RandomTeleport(
        var enabled: Boolean = false,
        @SerialName("delay-seconds")
        var delaySeconds: Float = 3.0f,
        @SerialName("cooldown-seconds")
        var cooldownSeconds: Float = 30f,
        var bounds: MapBounds = MapBounds(doubleArrayOf(-5000.0, -5000.0, 5000.0, 5000.0)),
    )

    @Serializable
    @Kodec
    data class SpawnProtection(
        var bounds: MapBounds = MapBounds(doubleArrayOf(-8.0, -8.0, 8.0, 8.0)),
        var invulnerable: Boolean = true,
        @SerialName("enter-title")
        var enterTitle: String? = "Entering Spawn\nThis is a protected area",
        @SerialName("exit-title")
        var exitTitle: String? = "Leaving Spawn\nYou can now build",
    )
}
```

After compilation, you can reference the generated codec as `CustomWorldConfig.CODEC` (a `BuilderCodec<CustomWorldConfig>`).
