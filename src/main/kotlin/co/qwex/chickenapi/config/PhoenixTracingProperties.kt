package co.qwex.chickenapi.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration for exporting Koog agent traces to Arize Phoenix over OTLP.
 */
@ConfigurationProperties(prefix = "koog.tracing.phoenix")
data class PhoenixTracingProperties(
    val enabled: Boolean = false,
    val endpoint: String = "http://localhost:6006/v1/traces",
    val projectName: String = "chicken-api",
    val serviceName: String = "chicken-api-agents",
    val serviceVersion: String = "0.0.1",
    val deploymentEnvironment: String = "local",
)
