import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.squaredcandy.io.db.smartlight.SmartLightDatabase
import com.squaredcandy.io.db.util.DatabaseProvider
import com.squaredcandy.europa.model.SmartLight
import com.squaredcandy.europa.model.SmartLightCapability
import com.squaredcandy.protobuf.v1.model.*
import com.squaredcandy.protobuf.v1.provider.ProvideSmartLightRequest
import com.squaredcandy.protobuf.v1.provider.SmartLightCommandRequest
import com.squaredcandy.protobuf.v1.provider.SmartLightProviderServiceProto.*
import com.squaredcandy.protobuf.v1.model.SmartLightProto.*
import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.jupiter.api.*
import com.squaredcandy.ganymede.smartlight.provider.RealSmartLightProviderService
import com.squaredcandy.ganymede.smartlight.SmartLightServiceLink
import com.squaredcandy.ganymede.smartlight.model.SmartLightUpdateRequest
import kotlin.time.ExperimentalTime
import ResultSubject.Companion.assertThat

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExperimentalCoroutinesApi
@ExperimentalTime
class TestSmartLightProviderService {

    @AfterEach
    fun setupDatabase() = runBlocking {
        database.getAllSmartLights().forEach {
            database.removeSmartLight(it.macAddress)
        }
    }

    companion object {
        private val database: SmartLightDatabase = getDatabase()
        private val serviceLink = SmartLightServiceLink(database)
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
    fun `Provide smart light`() = runBlocking {
        // Check database empty
        var smartLights = database.getAllSmartLights()
        assertThat(smartLights).isEmpty()

        // Insert and verify inserted
        val testSmartLight = getTestSmartLightAllCapabilities()
        val request = ProvideSmartLightRequest {
            smartLight = testSmartLight.toSmartLightProtoModel()
        }
        val response = providerService.provideSmartLight(request)
        assertThat(response.updated).isTrue()

        smartLights = database.getAllSmartLights()
        assertThat(smartLights).hasSize(1)
        assertThat(smartLights[0]).isEqualTo(testSmartLight)
    }

    @Test
    fun `Provide smart light twice`() = runBlocking {
        // Check database empty
        var smartLights = database.getAllSmartLights()
        assertThat(smartLights).isEmpty()

        // Insert and verify inserted
        val testSmartLight = getTestSmartLightAllCapabilities()
        val request = ProvideSmartLightRequest {
            smartLight = testSmartLight.toSmartLightProtoModel()
        }
        var response = providerService.provideSmartLight(request)
        assertThat(response.updated).isTrue()

        smartLights = database.getAllSmartLights()
        assertThat(smartLights).hasSize(1)
        assertThat(smartLights[0]).isEqualTo(testSmartLight)

        // Insert again and verify nothing changed
        response = providerService.provideSmartLight(request)
        assertThat(response.updated).isFalse()

        smartLights = database.getAllSmartLights()
        assertThat(smartLights).hasSize(1)
        assertThat(smartLights[0]).isEqualTo(testSmartLight)
    }

    @Test
    fun `Provide two different smart lights`() = runBlocking {
        // Check database empty
        var smartLights = database.getAllSmartLights()
        assertThat(smartLights).isEmpty()

        // Insert and verify inserted
        val testSmartLight = getTestSmartLightAllCapabilities()
        val request = ProvideSmartLightRequest {
            smartLight = testSmartLight.toSmartLightProtoModel()
        }
        var response = providerService.provideSmartLight(request)
        assertThat(response.updated).isTrue()

        smartLights = database.getAllSmartLights()
        assertThat(smartLights).hasSize(1)
        assertThat(smartLights[0]).isEqualTo(testSmartLight)

        // Insert second and verify second inserted
        val testSmartLight2 = getTestSmartLightNoLocation()
        val request2 = ProvideSmartLightRequest {
            smartLight = testSmartLight2.toSmartLightProtoModel()
        }
        response = providerService.provideSmartLight(request2)
        assertThat(response.updated).isTrue()

        smartLights = database.getAllSmartLights()
        assertThat(smartLights).hasSize(2)
        val result = smartLights.all { it == testSmartLight || it == testSmartLight2 }
        assertThat(result).isTrue()
    }

    @Test
    fun `Provide smart lights stress test`() = runBlocking {
        // Check database empty
        var smartLights = database.getAllSmartLights()
        assertThat(smartLights).isEmpty()

        // Insert 4 smart lights and verify inserted
        val testSmartLight = getTestSmartLightAllCapabilities()
        val testSmartLight2 = getTestSmartLightNoLocation()
        val testSmartLight3 = getTestSmartLightNoColor()
        val testSmartLight4 = getTestSmartLightNoCapabilities()

        val requestList = listOf(
            testSmartLight.toProvideSmartLightRequest(),
            testSmartLight2.toProvideSmartLightRequest(),
            testSmartLight3.toProvideSmartLightRequest(),
            testSmartLight4.toProvideSmartLightRequest(),
        )

        val deferredResponseList = requestList.map { request ->
            async { providerService.provideSmartLight(request) }
        }

        val deferredList = deferredResponseList.map { it.await() }
        deferredList.forEach { assertThat(it.updated).isTrue() }

        smartLights = database.getAllSmartLights()
        assertThat(smartLights).hasSize(4)
        val result = smartLights.all {
            it == testSmartLight ||
                it == testSmartLight2 ||
                it == testSmartLight3 ||
                it == testSmartLight4
        }
        assertThat(result).isTrue()
    }

    @Test
    fun `Open a command stream with no IP Address`() = runBlocking {
        // Open stream
        val channel = Channel<ServerSmartLightCommand>(1)
        val commandStreamJob = launch {
            val commandRequest = SmartLightCommandRequest {}
            providerService.openSmartLightCommandStream(commandRequest, channel)
        }

        // Send command and verify it was received
        channel.receiveAsFlow().test {
            val error = expectError()
            assertThat(error).isInstanceOf(StatusRuntimeException::class.java)
            assertThat((error as StatusRuntimeException).status).isEqualTo(Status.INVALID_ARGUMENT)
            expectNoEvents()
        }
        commandStreamJob.cancelAndJoin()
    }

    @Test
    fun `Open a two command streams with the same IP Address`() = runBlocking {
        // Check database empty
        var smartLights = database.getAllSmartLights()
        assertThat(smartLights).isEmpty()

        // Insert and verify inserted
        val testSmartLight = getTestSmartLightAllCapabilities()
        val providerRequest = ProvideSmartLightRequest {
            smartLight = testSmartLight.toSmartLightProtoModel()
        }
        val provideResponse = providerService.provideSmartLight(providerRequest)
        assertThat(provideResponse.updated).isTrue()

        smartLights = database.getAllSmartLights()
        assertThat(smartLights).hasSize(1)
        assertThat(smartLights[0]).isEqualTo(testSmartLight)

        // Open stream
        val channel = Channel<ServerSmartLightCommand>(1)
        val channel2 = Channel<ServerSmartLightCommand>(1)
        val commandRequest = SmartLightCommandRequest {
            ipAddress = testSmartLight.smartLightData.last().ipAddress
        }
        val commandStreamJob = launch {
            providerService.openSmartLightCommandStream(commandRequest, channel)
        }

        val commandStreamJob2 = launch {
            providerService.openSmartLightCommandStream(commandRequest, channel2)
        }
        // Send command and verify it was received
        channel2.receiveAsFlow().test {
            val error = expectError()
            assertThat(error).isInstanceOf(StatusRuntimeException::class.java)
            assertThat((error as StatusRuntimeException).status).isEqualTo(Status.ALREADY_EXISTS)
            expectNoEvents()
        }
        commandStreamJob.cancelAndJoin()
        commandStreamJob2.cancelAndJoin()
    }

    @Test
    fun `Open a command stream and send a name update command`() = runBlocking {
        // Check database empty
        var smartLights = database.getAllSmartLights()
        assertThat(smartLights).isEmpty()

        // Insert and verify inserted
        val testSmartLight = getTestSmartLightAllCapabilities()
        val providerRequest = ProvideSmartLightRequest {
            smartLight = testSmartLight.toSmartLightProtoModel()
        }
        val provideResponse = providerService.provideSmartLight(providerRequest)
        assertThat(provideResponse.updated).isTrue()

        smartLights = database.getAllSmartLights()
        assertThat(smartLights).hasSize(1)
        assertThat(smartLights[0]).isEqualTo(testSmartLight)

        // Open stream
        val channel = Channel<ServerSmartLightCommand>(1)
        val commandStreamJob = launch {
            val commandRequest = SmartLightCommandRequest {
                ipAddress = testSmartLight.smartLightData.last().ipAddress
            }
            providerService.openSmartLightCommandStream(commandRequest, channel)
        }

        // Send command and verify it was received
        channel.receiveAsFlow().test {
            val updateRequest = SmartLightUpdateRequest.Name(testSmartLight.macAddress, "name name")
            val response = serviceLink.userSetSmartLight(updateRequest)
            assertThat(response).isSuccess()
            val firstItem = expectItem()
            assertThat(firstItem.commandCase).isEquivalentAccordingToCompareTo(ServerSmartLightCommand.CommandCase.SET_PROPERTY)
            assertThat(firstItem.setProperty.propertyCase).isEquivalentAccordingToCompareTo(SetSmartLightPropertyRequest.PropertyCase.NAME)
            assertThat(firstItem.setProperty.name.newName).isEqualTo(updateRequest.name)

            expectNoEvents()
            channel.close()
            expectComplete()
        }
        commandStreamJob.cancelAndJoin()
    }

    @Test
    fun `Open a command stream and send all commands`() = runBlocking {
        // Check database empty
        var smartLights = database.getAllSmartLights()
        assertThat(smartLights).isEmpty()

        // Insert and verify inserted
        val testSmartLight = getTestSmartLightAllCapabilities()
        val providerRequest = ProvideSmartLightRequest {
            smartLight = testSmartLight.toSmartLightProtoModel()
        }
        val provideResponse = providerService.provideSmartLight(providerRequest)
        assertThat(provideResponse.updated).isTrue()

        smartLights = database.getAllSmartLights()
        assertThat(smartLights).hasSize(1)
        assertThat(smartLights[0]).isEqualTo(testSmartLight)

        // Open stream
        val channel = Channel<ServerSmartLightCommand>(1)
        val commandStreamJob = launch {
            val commandRequest = SmartLightCommandRequest {
                ipAddress = testSmartLight.smartLightData.last().ipAddress
            }
            providerService.openSmartLightCommandStream(commandRequest, channel)
        }

        // Send command and verify it was received
        channel.receiveAsFlow().test {
            // Send name request
            val updateNameRequest = SmartLightUpdateRequest.Name(testSmartLight.macAddress, "name name")
            var response = serviceLink.userSetSmartLight(updateNameRequest)
            assertThat(response).isSuccess()
            val nameItem = expectItem()
            assertThat(nameItem.commandCase).isEquivalentAccordingToCompareTo(ServerSmartLightCommand.CommandCase.SET_PROPERTY)
            assertThat(nameItem.setProperty.propertyCase).isEquivalentAccordingToCompareTo(SetSmartLightPropertyRequest.PropertyCase.NAME)
            assertThat(nameItem.setProperty.name.newName).isEqualTo(updateNameRequest.name)

            // Send power request
            val updatePowerRequest = SmartLightUpdateRequest.Power(testSmartLight.macAddress, false)
            response = serviceLink.userSetSmartLight(updatePowerRequest)
            assertThat(response).isSuccess()
            val powerItem = expectItem()
            assertThat(powerItem.commandCase).isEquivalentAccordingToCompareTo(ServerSmartLightCommand.CommandCase.SET_PROPERTY)
            assertThat(powerItem.setProperty.propertyCase).isEquivalentAccordingToCompareTo(SetSmartLightPropertyRequest.PropertyCase.POWER)
            assertThat(powerItem.setProperty.power.isOn).isEqualTo(updatePowerRequest.isOn)

            // Send color request
            val updateColorRequest = SmartLightUpdateRequest.Color(testSmartLight.macAddress, getSmartLightCapabilityColor())
            response = serviceLink.userSetSmartLight(updateColorRequest)
            assertThat(response).isSuccess()
            val colorItem = expectItem()
            assertThat(colorItem.commandCase).isEquivalentAccordingToCompareTo(ServerSmartLightCommand.CommandCase.SET_PROPERTY)
            assertThat(colorItem.setProperty.propertyCase).isEquivalentAccordingToCompareTo(SetSmartLightPropertyRequest.PropertyCase.COLOR)
            assertThat(colorItem.setProperty.color.color.toSmartLightColor()).isEqualTo(updateColorRequest.color)

            // Send other type of color request
            val updateColorRequest2 = SmartLightUpdateRequest.Color(testSmartLight.macAddress, getSmartLightCapabilityColor())
            response = serviceLink.userSetSmartLight(updateColorRequest2)
            assertThat(response).isSuccess()
            val colorItem2 = expectItem()
            assertThat(colorItem2.commandCase).isEquivalentAccordingToCompareTo(ServerSmartLightCommand.CommandCase.SET_PROPERTY)
            assertThat(colorItem2.setProperty.propertyCase).isEquivalentAccordingToCompareTo(SetSmartLightPropertyRequest.PropertyCase.COLOR)
            assertThat(colorItem2.setProperty.color.color.toSmartLightColor()).isEqualTo(updateColorRequest2.color)

            // Send location request
            val updateLocationRequest = SmartLightUpdateRequest.Location(testSmartLight.macAddress, SmartLightCapability.SmartLightLocation("Home/New Location"))
            response = serviceLink.userSetSmartLight(updateLocationRequest)
            assertThat(response).isSuccess()
            val locationItem = expectItem()
            assertThat(locationItem.commandCase).isEquivalentAccordingToCompareTo(ServerSmartLightCommand.CommandCase.SET_PROPERTY)
            assertThat(locationItem.setProperty.propertyCase).isEquivalentAccordingToCompareTo(SetSmartLightPropertyRequest.PropertyCase.LOCATION)
            assertThat(locationItem.setProperty.location.location.toSmartLightLocation()).isEqualTo(updateLocationRequest.Location)

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
    private fun SmartLight.toProvideSmartLightRequest(): ProvideSmartLightRequest {
        return ProvideSmartLightRequest {
            smartLight = toSmartLightProtoModel()
        }
    }
    private fun SmartLight.toSmartLightProtoModel(): SmartLightProtoModel {
        return SmartLightProtoModel {
            name = this@toSmartLightProtoModel.name
            macAddress = this@toSmartLightProtoModel.macAddress
            created {
                seconds = this@toSmartLightProtoModel.created.toEpochSecond()
                nanos = this@toSmartLightProtoModel.created.nano
            }
            updated {
                seconds = this@toSmartLightProtoModel.lastUpdated.toEpochSecond()
                nanos = this@toSmartLightProtoModel.lastUpdated.nano
            }
            this@toSmartLightProtoModel.smartLightData.forEach { smartLightData ->
                addData {
                    timestamp {
                        seconds = smartLightData.timestamp.toEpochSecond()
                        nanos = smartLightData.timestamp.nano
                    }
                    isConnected = smartLightData.isConnected
                    if(smartLightData.ipAddress != null) {
                        ipAddress = smartLightData.ipAddress
                    }
                    isOn = smartLightData.isOn
                    smartLightData.capabilities.forEach { smartLightCapability ->
                        when(smartLightCapability) {
                            is SmartLightCapability.SmartLightColor.SmartLightHSB -> {
                                color {
                                    hsb {
                                        hue = smartLightCapability.hue
                                        saturation = smartLightCapability.saturation
                                        brightness = smartLightCapability.brightness
                                    }
                                }
                            }
                            is SmartLightCapability.SmartLightColor.SmartLightKelvin -> {
                                color {
                                    kelvin {
                                        kelvin = smartLightCapability.kelvin
                                        brightness = smartLightCapability.brightness
                                    }
                                }
                            }
                            is SmartLightCapability.SmartLightLocation -> {
                                location {
                                    location = smartLightCapability.location
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}