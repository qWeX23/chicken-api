package co.qwex.chickenapi.config

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [RequireKoogCredentialsValidator::class])
annotation class RequireKoogCredentials(
    val message: String = "koog.agent.api-key must be set when koog.agent.enabled is true",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)
