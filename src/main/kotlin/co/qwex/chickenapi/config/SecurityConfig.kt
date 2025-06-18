package co.qwex.chickenapi.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/",
                        "/breeds/**",
                        "/api/v1/breeds/**",
                        "/api/v1/chickens/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/static/**",
                        "/favicon.ico",
                        "/login"
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt {}
            }
        return http.build()
    }
}
