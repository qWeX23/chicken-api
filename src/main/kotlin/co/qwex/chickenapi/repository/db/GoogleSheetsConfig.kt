import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GoogleSheetsConfig {
    @Suppress("DEPRECATION")
    private val JSON_FACTORY = JacksonFactory.getDefaultInstance()
    private val APPLICATION_NAME = "Chicken API"

    @Bean
    fun sheetsService(): Sheets {
        // 1. Pick up credentials from ADC (GOOGLE_APPLICATION_CREDENTIALS)
        val credentials = GoogleCredentials
            .getApplicationDefault()
            .createScoped(listOf("https://www.googleapis.com/auth/spreadsheets.readonly"))

        // 2. Build the Sheets client
        return Sheets.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            JSON_FACTORY,
            HttpCredentialsAdapter(credentials),
        )
            .setApplicationName(APPLICATION_NAME)
            .build()
    }
}
