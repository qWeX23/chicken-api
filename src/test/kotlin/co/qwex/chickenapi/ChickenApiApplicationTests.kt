import co.qwex.chickenapi.ChickenApiApplication
import com.google.api.services.sheets.v4.Sheets
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@SpringBootTest(classes = [ChickenApiApplication::class])
class ChickenApiApplicationTests {

    @TestConfiguration
    internal class MockServiceConfig {
        @Bean
        @Primary // Ensures this mock bean takes precedence
        fun mockStorage(): Sheets {
            // Create a mock instance of the Storage service
            return Mockito.mock(Sheets::class.java)
        }
    }

    @Autowired
    private lateinit var storage: Sheets // Autowire the mocked Storage bean

    @Test
    fun contextLoads() {
    }
}
