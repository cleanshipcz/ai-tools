package cz.cleanship.telemetry.logging

import org.slf4j.Logger

/**
 * Convenience extensions for SLF4J 2.x event API with map-based structured fields.
 */
fun Logger.info(message: String, fields: Map<String, Any?> = emptyMap(), t: Throwable? = null) {
    val eb = atInfo()
    fields.forEach { (k, v) -> eb.addKeyValue(k, v) }
    if (t != null) eb.setCause(t)
    eb.log(message)
}

fun Logger.warn(message: String, fields: Map<String, Any?> = emptyMap(), t: Throwable? = null) {
    val eb = atWarn()
    fields.forEach { (k, v) -> eb.addKeyValue(k, v) }
    if (t != null) eb.setCause(t)
    eb.log(message)
}

fun Logger.error(message: String, fields: Map<String, Any?> = emptyMap(), t: Throwable? = null) {
    val eb = atError()
    fields.forEach { (k, v) -> eb.addKeyValue(k, v) }
    if (t != null) eb.setCause(t)
    eb.log(message)
}

fun Logger.debug(message: String, fields: Map<String, Any?> = emptyMap(), t: Throwable? = null) {
    val eb = atDebug()
    fields.forEach { (k, v) -> eb.addKeyValue(k, v) }
    if (t != null) eb.setCause(t)
    eb.log(message)
}
