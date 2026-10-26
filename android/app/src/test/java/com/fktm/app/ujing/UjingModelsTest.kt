package com.fktm.app.ujing

import com.fktm.app.ujing.net.DeviceWashModel
import com.fktm.app.ujing.net.ModuleType
import com.fktm.app.ujing.net.OrderData
import com.fktm.app.ujing.net.UjingApi
import com.fktm.app.ujing.net.WasherStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class UjingModelsTest {

    @Test
    fun recognizesSupportedBleModuleTypes() {
        assertTrue(ModuleType.supportsBle(ModuleType.TYPE_BLE_NORDIC))
        assertTrue(ModuleType.supportsBle(ModuleType.TYPE_BLE_CYPRESS))
        assertTrue(ModuleType.supportsBle(ModuleType.TYPE_2G_BLE))
        assertTrue(ModuleType.supportsBle(ModuleType.TYPE_NB_BLE))
        assertFalse(ModuleType.supportsBle(ModuleType.TYPE_4G))
    }

    @Test
    fun runningOrderIncludesServerStartupStates() {
        listOf(
            WasherStatus.STARTING,
            WasherStatus.SELF_CLEANING_STARTING,
            WasherStatus.DETERGENT_DISPENSING,
            WasherStatus.SELF_CLEANING,
            WasherStatus.RUNNING,
        ).forEach { status ->
            assertTrue(OrderData(status = status).running)
        }
        assertFalse(OrderData(status = WasherStatus.COMPLETED).running)
    }

    @Test
    fun promotionPriceOnlyWinsWhenPositive() {
        assertEquals(
            250,
            DeviceWashModel(basePrice = 300, promotionPrice = 250).displayPriceCents,
        )
        assertEquals(
            300,
            DeviceWashModel(basePrice = 300, promotionPrice = 0).displayPriceCents,
        )
    }

    @Test
    fun acceptsOnlyKnownUjingQrHosts() {
        val api = UjingApi()
        assertTrue(api.isUjingQrCode("https://q.ujing.com.cn/ucqrc/index.html?cd=123456"))
        assertTrue(
            api.isUjingQrCode(
                "http://app.littleswan.com/u_download.html?type=Ujing&uuid=abc"
            )
        )
        assertFalse(api.isUjingQrCode("https://example.com/ucqrc/index.html?cd=123456"))
    }
}
