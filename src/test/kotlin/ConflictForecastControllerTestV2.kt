import aero.airlab.challenge.conflictforecast.ConflictForecastApplication
import aero.airlab.challenge.conflictforecast.api.ConflictForecastRequest
import aero.airlab.challenge.conflictforecast.api.ConflictForecastResponse
import aero.airlab.challenge.conflictforecast.service.ConflictForecastService
import aero.airlab.challenge.conflictforecast.service.ConflictForecastServiceV2
import aero.airlab.challenge.conflictforecast.service.GeoJsonMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.io.File

@ExtendWith(SpringExtension::class)
@SpringBootTest(classes = [ConflictForecastApplication::class])
@AutoConfigureMockMvc
class ConflictForecastControllerTestV2 {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockK
    private lateinit var conflictForecastServiceV2: ConflictForecastServiceV2

    @MockK
    private lateinit var geoJsonMapper: GeoJsonMapper

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @Test
    @Order(1)
    fun `should return ConflictForecastResponse`() = runBlocking {
        // Arrange
        val request = readJsonFile("requests/demo-request-2.json", ConflictForecastRequest::class.java)
        val expectedResponse = readJsonFile("responses/demo-response-2.json", ConflictForecastResponse::class.java)

        coEvery { conflictForecastServiceV2.createConflict(request) } returns expectedResponse
        // Act & Assert
        mockMvc.post("/v2/forecasts/conflicts") {
            contentType = MediaType.APPLICATION_JSON
            content = jacksonObjectMapper().writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            content { json(jacksonObjectMapper().writeValueAsString(expectedResponse)) }
        }
    }

    @Test
    @Order(2)
    fun `should return Map containing GeoJSON features`() {
        // Arrange
        val request = readJsonFile("requests/demo-request-2.json", ConflictForecastRequest::class.java)
        //val classObject: Class<Map<String, *>> = Map::class.java
        val classObject: Class<out Map<String, Any>> = mutableMapOf<String, Any>().javaClass

        val expectedFeatureCollection = readJsonFile("responses/demo-feature-response-2.json", classObject)

        coEvery { geoJsonMapper.createConflictsAsFeatureCollectionsV2(request) } returns expectedFeatureCollection

        // Act & Assert
        mockMvc.post("/v2/forecasts/conflict-features") {
            contentType = MediaType.APPLICATION_JSON
            content = jacksonObjectMapper().writeValueAsString(request)
        }.andExpect {
            status { isOk() }
//            content { json(jacksonObjectMapper().writeValueAsString(expectedFeatureCollection)) }
        }
    }

    fun <T> readJsonFile(fileName: String, clazz: Class<T>): T {
        val mapper = jacksonObjectMapper()
        val fileContent = File("src/test/resources/$fileName").readText()
        return mapper.readValue(fileContent, clazz)
    }


}
