package co.qwex.chickenapi.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/api/v1/protected/**").authenticated()
                    .requestMatchers("/", "/breeds", "/breeds/*/view", "/swagger-ui/**", "/v3/api-docs/**", "/favicon_64.ico", "/api/**").permitAll()
                    .anyRequest().permitAll()
            }
            .oauth2ResourceServer { it.jwt() }
            .csrf { it.disable() }
        return http.build()
    }
}
