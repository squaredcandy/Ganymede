import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.Timestamp
import com.squaredcandy.io.db.smartlight.SmartLightDatabase
import com.squaredcandy.io.db.util.DatabaseProvider
import com.squaredcandy.europa.model.SmartLight
import com.squaredcandy.europa.model.SmartLightCapability
import com.squaredcandy.europa.model.SmartLightData
import com.squaredcandy.protobuf.v1.model.*
import com.squaredcandy.protobuf.v1.model.SmartLightProto.*
import com.squaredcandy.protobuf.v1.provider.SmartLightCommandRequest
import com.squaredcandy.protobuf.v1.provider.SmartLightProviderServiceProto
import com.squaredcandy.protobuf.v1.user.*
import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import com.squaredcandy.ganymede.smartlight.user.RealSmartLightUserService
import com.squaredcandy.ganymede.smartlight.SmartLightServiceLink
import com.squaredcandy.ganymede.smartlight.provider.RealSmartLightProviderService
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.ExperimentalTime
import ResultSubject.Companion.assertThat

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExperimentalCoroutinesApi
@ExperimentalTime
class TestSmartLightUserService {

    @AfterEach
    fun setupDatabase() = runBlocking {
        database.getAllSmartLights().forEach {
            database.removeSmartLight(it.macAddress)
        }
    }

    companion object {
        private val database: SmartLightDatabase = getDatabase()
        private val serviceLink = SmartLightServiceLink(database)
        private val userService = RealSmartLightUserService(serviceLink)
        private val providerService = RealSmartLightProviderService(serviceLink)

        @JvmStatic
        @AfterAll
        private fun tearDownDatabase() {
            database.closeDatabase()
        }

        private fun getDatabase(): SmartLightDatabase {
            return DatabaseProvider.getSmartLightDatabase(
                driverClassName = "org.h2.Driver",
                jdbcUrl = "jdbc:h2:mem:test",
                username = null,
                password = null,
            )
        }
    }

    @Test
    fun `Insert blank mac address to get smart light`() = runBlocking {
        // Create request
        val request = GetSmartLightRequest {
            userId = ""
            macAddress = ""
        }

        // Send request and verify result
        val result = runCatching {
            userService.getSmartLight(request)
        }
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(StatusRuntimeException::class.java)
        assertThat((result.exceptionOrNull() as StatusRuntimeException).status).isEqualTo(Status.INVALID_ARGUMENT)
    }

    @Test
    fun `Insert invalid mac address to get smart light`() = runBlocking {
        // Create request
        val request = GetSmartLightRequest {
            userId = ""
            macAddress = "1234"
        }

        // Send request and verify result
        val result = runCatching {
            userService.getSmartLight(request)
        }
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(StatusRuntimeException::class.java)
        assertThat((result.exceptionOrNull() as StatusRuntimeException).status).isEqualTo(Status.NOT_FOUND)
    }

    @Test
    fun `Insert and get smart light`() = runBlocking {
        // Check database empty
        val smartLights = database.getAllSmartLights()
        assertThat(smartLights).isEmpty()

        // Insert smart light
        val testSmartLight = getTestSmartLightAllCapabilities()
        database.upsertSmartLight(testSmartLight)

        // Create request and send, verifying result
        val request = GetSmartLightRequest {
            userId = ""
            macAddress = testSmartLight.macAddress
        }
        val response = userService.getSmartLight(request)
        assertThat(response.smartLight.toSmartLight()).isEqualTo(testSmartLight)
    }

    @Test
    fun `Open channel and get insert update`() = runBlocking {
        val testSmartLight = getTestSmartLightAllCapabilities()
        // Create and open channel
        val channel = Channel<SmartLightUserServiceProto.GetSmartLightResponse>(1)
        val streamJob = launch {
            val request = GetSmartLightRequest {
                userId = ""
                macAddress = testSmartLight.macAddress
            }
            userService.getSmartLightStream(request, channel)
        }

        // Collect flow and verify functionality
        channel.consumeAsFlow().test {
            var inserted = database.upsertSmartLight(testSmartLight)
            assertThat(inserted).isSuccess()
            var response = expectItem()
            assertThat(response.smartLight.toSmartLight()).isEqualTo(testSmartLight)
            val newTestSmartLight = testSmartLight.copy(name = "Modified name", lastUpdated = OffsetDateTime.now())
            inserted = database.upsertSmartLight(newTestSmartLight)
            assertThat(inserted).isSuccess()
            response = expectItem()
            assertThat(response.smartLight.toSmartLight()).isEqualTo(newTestSmartLight)
            expectNoEvents()
        }

        streamJob.cancelAndJoin()
    }

    @Test
    fun `Open stream and stress test`() = runBlocking {
        val testSmartLight = getTestSmartLightAllCapabilities()
        // Create and open channel
        val channel = Channel<SmartLightUserServiceProto.GetSmartLightResponse>(1)
        val streamJob = launch {
            val request = GetSmartLightRequest {
                userId = ""
                macAddress = testSmartLight.macAddress
            }
            userService.getSmartLightStream(request, channel)
        }

        // Collect flow and verify functionality
        channel.consumeAsFlow().test {
            var inserted = database.upsertSmartLight(testSmartLight)
            assertThat(inserted).isSuccess()
            var response = expectItem()
            assertThat(response.smartLight.toSmartLight()).isEqualTo(testSmartLight)

            val newTestSmartLight = testSmartLight.copy(name = "Modified name", lastUpdated = OffsetDateTime.now())
            inserted = database.upsertSmartLight(newTestSmartLight)
            assertThat(inserted).isSuccess()
            response = expectItem()
            assertThat(response.smartLight.toSmartLight()).isEqualTo(newTestSmartLight)

            val removed = database.removeSmartLight(newTestSmartLight.macAddress)
            assertThat(removed).isSuccess()

            expectNoEvents()
        }

        streamJob.cancelAndJoin()
    }

    @Test
    fun `Send update request to invalid smart light`() = runBlocking {
        val request = SetSmartLightPropertyRequest {
            macAddress = "1111"
            name {
                newName = "New Name"
            }
        }
        val response = userService.setSmartLightProperty(request)
        assertThat(response.updated).isFalse()
    }

    @Test
    fun `Send invalid update request`() = runBlocking {
        val request = SetSmartLightPropertyRequest {
        }
        val result = runCatching {
            userService.setSmartLightProperty(request)
        }

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(StatusRuntimeException::class.java)
        assertThat((result.exceptionOrNull() as StatusRuntimeException).status).isEqualTo(Status.INVALID_ARGUMENT)
    }

    @Test
    fun `Stress test updating smart light property`() = runBlocking {
        // Check database empty
        val smartLights = database.getAllSmartLights()
        assertThat(smartLights).isEmpty()

        // Insert smart light
        val testSmartLight = getTestSmartLightAllCapabilities()
        val inserted = database.upsertSmartLight(testSmartLight)
        assertThat(inserted).isSuccess()

        // Create and connect stream
        val channel = Channel<SmartLightProviderServiceProto.ServerSmartLightCommand>(1)
        val commandStreamJob = launch {
            val commandRequest = SmartLightCommandRequest {
                ipAddress = testSmartLight.smartLightData.last().ipAddress
            }
            providerService.openSmartLightCommandStream(commandRequest, channel)
        }

        // Collect flow and verify functionality
        channel.receiveAsFlow().test {
            // Send update name request and verify
            val newName = "New name"
            val updateNameRequest = SetSmartLightPropertyRequest {
                macAddress = testSmartLight.macAddress
                name {
                    this.newName = newName
                }
            }
            var response = userService.setSmartLightProperty(updateNameRequest)
            assertThat(response.updated).isTrue()
            val nameItem = expectItem()
            assertThat(nameItem.commandCase).isEquivalentAccordingToCompareTo(SmartLightProviderServiceProto.ServerSmartLightCommand.CommandCase.SET_PROPERTY)
            assertThat(nameItem.setProperty.propertyCase).isEquivalentAccordingToCompareTo(SmartLightProviderServiceProto.SetSmartLightPropertyRequest.PropertyCase.NAME)
            assertThat(nameItem.setProperty.name.newName).isEqualTo(updateNameRequest.name.newName)

            // Send update power request and verify
            val newPower = !testSmartLight.smartLightData.last().isOn
            val updatePowerRequest = SetSmartLightPropertyRequest {
                macAddress = testSmartLight.macAddress
                power {
                    isOn = newPower
                }
            }
            response = userService.setSmartLightProperty(updatePowerRequest)
            assertThat(response.updated).isTrue()
            val powerItem = expectItem()
            assertThat(powerItem.commandCase).isEquivalentAccordingToCompareTo(SmartLightProviderServiceProto.ServerSmartLightCommand.CommandCase.SET_PROPERTY)
            assertThat(powerItem.setProperty.propertyCase).isEquivalentAccordingToCompareTo(SmartLightProviderServiceProto.SetSmartLightPropertyRequest.PropertyCase.POWER)
            assertThat(powerItem.setProperty.power.isOn).isEqualTo(updatePowerRequest.power.isOn)

            // Send update color request and verify
            var newColor = getSmartLightCapabilityColor()
            var updateColorRequest = SetSmartLightPropertyRequest {
                macAddress = testSmartLight.macAddress
                color {
                    color = newColor.toLightColorProtoModel()
                }
            }
            response = userService.setSmartLightProperty(updateColorRequest)
            assertThat(response.updated).isTrue()
            val colorItem = expectItem()
            assertThat(colorItem.commandCase).isEquivalentAccordingToCompareTo(SmartLightProviderServiceProto.ServerSmartLightCommand.CommandCase.SET_PROPERTY)
            assertThat(colorItem.setProperty.propertyCase).isEquivalentAccordingToCompareTo(SmartLightProviderServiceProto.SetSmartLightPropertyRequest.PropertyCase.COLOR)
            assertThat(colorItem.setProperty.color.color.toSmartLightColor()).isEqualTo(updateColorRequest.color.color.toSmartLightColor())

            // Send update other color request and verify
            newColor = getSmartLightCapabilityColor()
            updateColorRequest = SetSmartLightPropertyRequest {
                macAddress = testSmartLight.macAddress
                color {
                    color = newColor.toLightColorProtoModel()
                }
            }
            response = userService.setSmartLightProperty(updateColorRequest)
            assertThat(response.updated).isTrue()
            val colorItem2 = expectItem()
            assertThat(colorItem2.commandCase).isEquivalentAccordingToCompareTo(SmartLightProviderServiceProto.ServerSmartLightCommand.CommandCase.SET_PROPERTY)
            assertThat(colorItem2.setProperty.propertyCase).isEquivalentAccordingToCompareTo(SmartLightProviderServiceProto.SetSmartLightPropertyRequest.PropertyCase.COLOR)
            assertThat(colorItem2.setProperty.color.color.toSmartLightColor()).isEqualTo(updateColorRequest.color.color.toSmartLightColor())

            // Send update location request and verify
            val updateLocationRequest = SetSmartLightPropertyRequest {
                macAddress = testSmartLight.macAddress
                location {
                    location {
                        location = "New location"
                    }
                }
            }
            response = userService.setSmartLightProperty(updateLocationRequest)
            assertThat(response.updated).isTrue()
            val locationItem = expectItem()
            assertThat(locationItem.commandCase).isEquivalentAccordingToCompareTo(SmartLightProviderServiceProto.ServerSmartLightCommand.CommandCase.SET_PROPERTY)
            assertThat(locationItem.setProperty.propertyCase).isEquivalentAccordingToCompareTo(SmartLightProviderServiceProto.SetSmartLightPropertyRequest.PropertyCase.LOCATION)
            assertThat(locationItem.setProperty.location.location.toSmartLightLocation()).isEqualTo(updateLocationRequest.location.location.toSmartLightLocation())

            expectNoEvents()
            channel.close()
            expectComplete()
        }
        commandStreamJob.cancelAndJoin()
    }

    private fun LightColorProtoModel.toSmartLightColor(): SmartLightCapability.SmartLightColor {
        return when(colorCase) {
            LightColorProtoModel.ColorCase.HSB -> SmartLightCapability.SmartLightColor.SmartLightHSB(hsb.hue, hsb.saturation, hsb.brightness)
            LightColorProtoModel.ColorCase.KELVIN -> SmartLightCapability.SmartLightColor.SmartLightKelvin(kelvin.kelvin, kelvin.brightness)
            LightColorProtoModel.ColorCase.COLOR_NOT_SET, null -> throw StatusRuntimeException(Status.INVALID_ARGUMENT)
        }
    }
    private fun LightLocationProtoModel.toSmartLightLocation(): SmartLightCapability.SmartLightLocation {
        return SmartLightCapability.SmartLightLocation(location)
    }
    private fun SmartLightCapability.SmartLightColor.toLightColorProtoModel(): LightColorProtoModel {
        return LightColorProtoModel {
            when(this@toLightColorProtoModel) {
                is SmartLightCapability.SmartLightColor.SmartLightHSB -> {
                    hsb {
                        hue = this@toLightColorProtoModel.hue
                        saturation = this@toLightColorProtoModel.saturation
                        brightness = this@toLightColorProtoModel.brightness
                    }
                }
                is SmartLightCapability.SmartLightColor.SmartLightKelvin -> {
                    kelvin {
                        kelvin = this@toLightColorProtoModel.kelvin
                        brightness = this@toLightColorProtoModel.brightness
                    }
                }
            }
        }
    }
    private fun SmartLightProtoModel.toSmartLight(offset: ZoneOffset = OffsetDateTime.now().offset): SmartLight {
        return SmartLight(
            name = this.name,
            macAddress = this.macAddress,
            created = this.created.toOffsetDateTime(offset),
            lastUpdated = this.updated.toOffsetDateTime(offset),
            smartLightData = this.dataList.map { data -> data.toSmartLightData(offset) }
        )
    }
    private fun Timestamp.toOffsetDateTime(offset: ZoneOffset): OffsetDateTime {
        return Instant.ofEpochSecond(seconds, nanos.toLong()).atOffset(offset)
    }
    private fun SmartLightDataProtoModel.toSmartLightData(offset: ZoneOffset): SmartLightData {
        val capabilityList = mutableListOf(
            getColorCapability(),
            getLocationCapability(),
        ).filterNotNull()
        return SmartLightData(
            timestamp = timestamp.toOffsetDateTime(offset),
            ipAddress = ipAddress,
            isOn = isOn,
            capabilities = capabilityList
        )
    }
    private fun SmartLightDataProtoModel.getColorCapability(): SmartLightCapability.SmartLightColor? {
        return when (color?.colorCase) {
            LightColorProtoModel.ColorCase.HSB -> {
                SmartLightCapability.SmartLightColor.SmartLightHSB(
                    color.hsb.hue,
                    color.hsb.saturation,
                    color.hsb.brightness,
                )
            }
            LightColorProtoModel.ColorCase.KELVIN -> {
                SmartLightCapability.SmartLightColor.SmartLightKelvin(
                    color.kelvin.kelvin,
                    color.kelvin.brightness,
                )
            }
            LightColorProtoModel.ColorCase.COLOR_NOT_SET, null -> null
        }
    }
    private fun SmartLightDataProtoModel.getLocationCapability(): SmartLightCapability.SmartLightLocation? {
        return if(hasLocation()) {
            SmartLightCapability.SmartLightLocation(location.location)
        } else null
    }
}