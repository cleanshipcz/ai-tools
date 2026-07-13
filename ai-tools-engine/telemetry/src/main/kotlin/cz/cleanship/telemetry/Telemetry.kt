package cz.cleanship.telemetry

object Telemetry {
    /**
     * Creates a [TelemetryFacade] instance.
     *
     * @param config telemetry configuration
     * @return a new telemetry facade
     */
    fun create(config: TelemetryConfig = TelemetryConfig.fromEnvironment()): TelemetryFacade = DefaultTelemetry(config)
}
