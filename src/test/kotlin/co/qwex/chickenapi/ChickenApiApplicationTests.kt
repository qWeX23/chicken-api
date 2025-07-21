import co.qwex.chickenapi.ChickenApiApplication
import co.qwex.chickenapi.config.TestConfig
import com.google.api.services.sheets.v4.Sheets
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest(classes = [ChickenApiApplication::class, TestConfig::class])
class ChickenApiApplicationTests {

    @MockitoBean
    private lateinit var sheetsService: Sheets

    @Test
    fun contextLoads() {
    }
}
