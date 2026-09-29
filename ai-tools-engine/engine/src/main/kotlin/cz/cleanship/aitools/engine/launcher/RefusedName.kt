package cz.cleanship.aitools.engine.launcher

/**
 * The class of a name the launcher refuses as a secret name, which decides where else the engine refuses it.
 *
 * @property letter the letter the block of refused names of the launcher writes before each pattern of the class
 */
enum class RefusedNameClass(val letter: Char) {

    /**
     * Class A: a name that the launcher's shell, the loader and C library of that shell, or a program the launcher starts reads, or that the shell sets itself. The engine refuses it in every role of a server started through the launcher: secret, environment-only secret, plain variable, `env` key.
     */
    LAUNCHER('A'),

    /**
     * Class B: a name that changes what the server or a program it starts does, never the launcher. The engine refuses it as a secret read through the secrets manager, and anywhere in a `server.json`.
     */
    SERVER('B'),
}

/**
 * A pattern of secret names the launcher refuses, with its class.
 *
 * @property nameClass where else the engine refuses a name matching [pattern]
 * @property pattern an upper-case name, or a name with `*` as its first or its last character, where `*` stands for any text, the empty text included
 */
data class RefusedName(val nameClass: RefusedNameClass, val pattern: String)
