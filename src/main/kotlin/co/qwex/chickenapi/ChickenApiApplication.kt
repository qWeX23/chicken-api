package co.qwex.chickenapi

import co.qwex.chickenapi.repository.db.GoogleSheetsConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Import
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan
@Import(GoogleSheetsConfig::class)
@EnableCaching
@EnableAsync
@EnableScheduling
class ChickenApiApplication

fun main(args: Array<String>) {
    runApplication<ChickenApiApplication>(*args)
}
