package co.qwex.chickenapi

import co.qwex.chickenapi.repository.db.GoogleSheetsConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import org.springframework.cache.annotation.EnableCaching

@SpringBootApplication
@Import(GoogleSheetsConfig::class)
@EnableCaching
class ChickenApiApplication

fun main(args: Array<String>) {
    runApplication<ChickenApiApplication>(*args)
}
