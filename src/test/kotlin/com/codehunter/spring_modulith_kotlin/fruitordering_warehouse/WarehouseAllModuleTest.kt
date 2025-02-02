package com.codehunter.spring_modulith_kotlin.fruitordering_warehouse

import com.codehunter.spring_modulith_kotlin.TestContainerConfig
import com.codehunter.spring_modulith_kotlin.TestSecurityConfiguration
import com.codehunter.spring_modulith_kotlin.WiremockInitializer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.modulith.test.ApplicationModuleTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration

@ApplicationModuleTest(
    mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ContextConfiguration(initializers = arrayOf(WiremockInitializer::class))
@Import(value = arrayOf(TestSecurityConfiguration::class, TestContainerConfig::class))
@ActiveProfiles("integration")
class WarehouseAllModuleTest {

}