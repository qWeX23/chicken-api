package co.qwex.chickenapi.config

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

class RequireKoogCredentialsValidator : ConstraintValidator<RequireKoogCredentials, KoogAgentProperties> {
    override fun isValid(
        value: KoogAgentProperties?,
        context: ConstraintValidatorContext,
    ): Boolean {
        if (value == null || !value.enabled) {
            return true
        }

        val hasApiKey = !value.apiKey.isNullOrBlank()
        if (hasApiKey) {
            return true
        }

        context.disableDefaultConstraintViolation()
        context.buildConstraintViolationWithTemplate(
            "koog.agent.api-key must be set when koog.agent.enabled is true",
        ).addConstraintViolation()
        return false
    }
}
