package co.qwex.chickenapi

import GoogleSheetsConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootApplication
@Import(GoogleSheetsConfig::class)
class ChickenApiApplication

fun main(args: Array<String>) {
    runApplication<ChickenApiApplication>(*args)
}
