package co.qwex.chickenapi.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean

@Configuration
class KoogAgentPropertiesValidationConfiguration {
    @Bean(name = ["configurationPropertiesValidator"])
    fun configurationPropertiesValidator(): LocalValidatorFactoryBean = LocalValidatorFactoryBean()
}
