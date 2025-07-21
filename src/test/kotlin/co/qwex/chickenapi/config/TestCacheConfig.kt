package co.qwex.chickenapi.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.cache.CacheManager
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import java.util.concurrent.TimeUnit

@TestConfiguration
class TestCacheConfig {

    @Bean
    fun cacheManager(): CacheManager {
        val cacheManager = CaffeineCacheManager()
        cacheManager.setCaffeine(caffeineConfig())
        cacheManager.setCacheNames(setOf("buckets"))
        return cacheManager
    }

    @Bean
    fun caffeineConfig(): Caffeine<Any, Any> {
        return Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterAccess(1, TimeUnit.MINUTES)
            .recordStats()
    }
}
