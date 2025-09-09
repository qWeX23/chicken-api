import co.qwex.chickenapi.ChickenApiApplication
import co.qwex.chickenapi.config.TestConfig
import co.qwex.chickenapi.service.PendingBreed
import co.qwex.chickenapi.service.PendingBreedUpdate
import co.qwex.chickenapi.service.PendingChicken
import co.qwex.chickenapi.service.ReviewQueue
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.AppendValuesResponse
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest(classes = [ChickenApiApplication::class, TestConfig::class])
class ReviewQueueTests {
    @Autowired
    lateinit var reviewQueue: ReviewQueue

    @MockitoBean(answers = org.mockito.Answers.RETURNS_DEEP_STUBS)
    lateinit var sheets: Sheets

    @Value("\${google.sheets.db.spreadsheetId}")
    lateinit var spreadsheetId: String

    @Test
    fun `add breed enqueues and writes`() {
        val values = sheets.spreadsheets().values()
        val append = Mockito.mock(Sheets.Spreadsheets.Values.Append::class.java)
        Mockito.`when`(values.append(anyString(), anyString(), any())).thenReturn(append)
        Mockito.`when`(append.setValueInputOption(anyString())).thenReturn(append)
        Mockito.`when`(append.setInsertDataOption(anyString())).thenReturn(append)
        Mockito.`when`(append.execute()).thenReturn(AppendValuesResponse())

        val breed = PendingBreed(
            name = "TestBreed",
            origin = "Mars",
            eggColor = null,
            eggSize = null,
            eggNumber = null,
            temperament = null,
            description = null,
            imageUrl = null,
        )

        reviewQueue.addBreed(breed)
        Mockito.verify(values).append(
            Mockito.eq(spreadsheetId),
            Mockito.eq("pending_breeds"),
            Mockito.any(),
        )
        Mockito.verify(append).setValueInputOption("USER_ENTERED")
        Mockito.verify(append).setInsertDataOption("INSERT_ROWS")
        Mockito.verify(append).execute()
    }

    @Test
    fun `add breed update enqueues and writes`() {
        val values = sheets.spreadsheets().values()
        val append = Mockito.mock(Sheets.Spreadsheets.Values.Append::class.java)
        Mockito.`when`(values.append(anyString(), anyString(), any())).thenReturn(append)
        Mockito.`when`(append.setValueInputOption(anyString())).thenReturn(append)
        Mockito.`when`(append.setInsertDataOption(anyString())).thenReturn(append)
        Mockito.`when`(append.execute()).thenReturn(AppendValuesResponse())

        val update = PendingBreedUpdate(
            id = 1,
            name = "Updated",
            origin = null,
            eggColor = null,
            eggSize = null,
            eggNumber = null,
            temperament = null,
            description = null,
            imageUrl = null,
        )

        reviewQueue.addBreedUpdate(update)
        Mockito.verify(values).append(
            Mockito.eq(spreadsheetId),
            Mockito.eq("pending_breed_updates"),
            Mockito.any(),
        )
        Mockito.verify(append).setValueInputOption("USER_ENTERED")
        Mockito.verify(append).setInsertDataOption("INSERT_ROWS")
        Mockito.verify(append).execute()
    }

    @Test
    fun `add chicken enqueues and writes`() {
        val values = sheets.spreadsheets().values()
        val append = Mockito.mock(Sheets.Spreadsheets.Values.Append::class.java)
        Mockito.`when`(values.append(anyString(), anyString(), any())).thenReturn(append)
        Mockito.`when`(append.setValueInputOption(anyString())).thenReturn(append)
        Mockito.`when`(append.setInsertDataOption(anyString())).thenReturn(append)
        Mockito.`when`(append.execute()).thenReturn(AppendValuesResponse())

        val chicken = PendingChicken(
            name = "Henrietta",
            breedId = 1,
            imageUrl = "img",
        )

        reviewQueue.addChicken(chicken)
        Mockito.verify(values).append(
            Mockito.eq(spreadsheetId),
            Mockito.eq("pending_chickens"),
            Mockito.any(),
        )
        Mockito.verify(append).setValueInputOption("USER_ENTERED")
        Mockito.verify(append).setInsertDataOption("INSERT_ROWS")
        Mockito.verify(append).execute()
    }
}
