import co.qwex.chickenapi.ChickenApiApplication
import com.google.api.services.sheets.v4.Sheets
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean

@SpringBootTest(classes = [ChickenApiApplication::class])
class ChickenApiApplicationTests {

    @MockBean
    private lateinit var sheetsService: Sheets

    @Test
    fun contextLoads() {
    }
}
