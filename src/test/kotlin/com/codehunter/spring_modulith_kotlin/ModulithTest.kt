package com.codehunter.spring_modulith_kotlin

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter


class ModulithTest {
    private val modules = ApplicationModules.of(SpringModulithKotlinApplication::class.java)

    @Test
    fun `should be able to run test`() {
        modules.verify()
    }

    @Test
    fun writeDocumentationSnippets() {
        Documenter(modules)
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml()
            .writeAggregatingDocument()
    }
}