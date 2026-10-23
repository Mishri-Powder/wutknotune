package link.roadio.riff.config

/** Immutable, process-owned startup configuration. No Android or persistence dependency. */
class ConfigurationSnapshot internal constructor(
    private val globals: ConfigObject,
    private val skin: ConfigObject,
) {
    private val globalValues = flatten(globals)
    private val skinValues = Array(4) { form -> flatten(skin, form) }

    val activeSkin: String get() = globals.getValue("activeSkin") as String
    val applicationMode: String get() = globals["applicationMode"] as? String ?: "aaos"

    fun value(
        path: String,
        wide: Boolean = true,
        night: Boolean = false,
    ): Any =
        skinValues[(if (wide) 1 else 0) + (if (night) 2 else 0)][path]
            ?: globalValues[path]
            ?: error("Unknown configuration path: $path")

    fun number(
        path: String,
        wide: Boolean = true,
        night: Boolean = false,
    ): Double = (value(path, wide, night) as Number).toDouble()

    fun isWide(rootWidthDp: Float): Boolean = rootWidthDp >= number("geometry.wideThreshold")

    internal companion object {
        val SKIN_SECTIONS = setOf("geometry", "typography", "colors", "shape", "behavior")

        // Compile four immutable lookup tables once; scrolling never walks/splits JSON paths.
        private fun flatten(
            root: ConfigObject,
            form: Int? = null,
        ): ConfigObject {
            val result = linkedMapOf<String, Any>()

            fun visit(
                path: String,
                raw: Any,
            ) {
                var value = raw
                if (form != null) {
                    while (value is Map<*, *>) {
                        val branches = value.objectValue()
                        value =
                            when {
                                "day" in branches && "night" in branches -> branches.getValue(if (form and 2 != 0) "night" else "day")
                                "wide" in branches && "narrow" in branches -> branches.getValue(if (form and 1 != 0) "wide" else "narrow")
                                else -> break
                            }
                    }
                }
                result[path] = value
                if (value is Map<*, *>) value.objectValue().forEach { (key, child) -> visit("$path.$key", child) }
            }
            root.forEach { (key, value) -> visit(key, value) }
            return immutableObject(result)
        }

        // The build gate verifies these generated data before packaging; startup only selects them.
        fun packaged(document: ConfigObject): ConfigurationSnapshot =
            ConfigurationSnapshot(
                immutableObject(document - "skins"),
                document
                    .getValue("skins")
                    .objectValue()
                    .getValue("default")
                    .objectValue(),
            )
    }
}

object ProjectConfiguration {
    const val SCHEMA_VERSION = 3
    const val OVERRIDE_FILE_NAME = "ui_config.json"

    fun parse(userText: String): ConfigurationSnapshot = resolve(PackagedConfiguration.document, ConfigJson(userText).parse())

    fun validateValue(
        path: String,
        value: Any,
    ) {
        var node: ConfigurationSchema.Node =
            if (path.substringBefore('.') in ConfigurationSnapshot.SKIN_SECTIONS) {
                ConfigurationSchema.skin
            } else {
                ConfigurationSchema.globals("applicationMode" in PackagedConfiguration.document)
            }
        path.split('.').forEach { node = (node as ConfigurationSchema.ObjectNode).fields.getValue(it) }
        node.validate(value, partial = false)
    }

    internal fun resolve(
        packaged: ConfigObject,
        user: ConfigObject = emptyMap(),
    ): ConfigurationSnapshot {
        val configurable = "applicationMode" in packaged
        val schema = ConfigurationSchema.globals(configurable)
        val packagedGlobals = packaged - "skins"
        val userGlobals = user - "skins"
        schema.validate(packagedGlobals, partial = false)
        schema.validate(userGlobals, partial = true)
        val globals = schema.merge(packagedGlobals, userGlobals)
        schema.validate(globals, partial = false)
        val packagedSkins = packaged.getValue("skins").objectValue()
        val userSkins = user["skins"]?.objectValue().orEmpty()
        require(packagedSkins.containsKey("default")) { "Packaged default skin is missing" }
        val packagedDefault = packagedSkins.getValue("default").objectValue()
        ConfigurationSchema.skin.validate(packagedDefault, partial = false)
        packagedSkins.forEach { (name, value) ->
            require(name.isNotBlank()) { "Empty skin name" }
            ConfigurationSchema.skin.validate(value, partial = true)
        }
        userSkins.forEach { (name, value) ->
            require(name.isNotBlank()) { "Empty skin name" }
            ConfigurationSchema.skin.validate(value, partial = true)
        }
        val names = packagedSkins.keys + userSkins.keys
        val active = globals.getValue("activeSkin") as String
        require(active in names) { "Unknown active skin" }
        var selected: ConfigObject? = null
        // Validate inactive definitions too: rejection is atomic for the entire user document.
        names.forEach { name ->
            var effective = packagedDefault
            if (name != "default") packagedSkins[name]?.let { effective = ConfigurationSchema.skin.merge(effective, it) }
            userSkins["default"]?.let { effective = ConfigurationSchema.skin.merge(effective, it) }
            if (name != "default") userSkins[name]?.let { effective = ConfigurationSchema.skin.merge(effective, it) }
            ConfigurationSchema.skin.validate(effective, partial = false)
            ConfigurationSchema.validateSemantics(effective)
            if (name == active) selected = effective
        }
        return ConfigurationSnapshot(globals, checkNotNull(selected))
    }
}
