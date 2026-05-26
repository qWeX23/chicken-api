package co.qwex.chickenapi.config

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(prefix = "koog.tracing.phoenix", name = ["enabled"], havingValue = "true")
class PhoenixTelemetryConfig {
    @Bean
    fun phoenixSpanExporter(properties: PhoenixTracingProperties): OtlpHttpSpanExporter =
        OtlpHttpSpanExporter.builder()
            .setEndpoint(properties.endpoint)
            .addHeader("x-project-name", properties.projectName)
            .build()

    @Bean
    fun phoenixResourceAttributes(properties: PhoenixTracingProperties): Map<String, Any> =
        mapOf(
            "deployment.environment" to properties.deploymentEnvironment,
            "openinference.project.name" to properties.projectName,
        )
}
