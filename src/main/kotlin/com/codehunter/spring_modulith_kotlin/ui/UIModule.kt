package com.codehunter.spring_modulith_kotlin.ui

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.slf4j.LoggerFactory
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/")
class UIController(private val mapper: ObjectMapper = ObjectMapper().registerModule(JavaTimeModule())) {
    val log = LoggerFactory.getLogger(this::class.java)

    @GetMapping
    fun getIndexPage(model: Model, @AuthenticationPrincipal principal: OidcUser?): String {
        if (principal != null) {
            model.addAttribute("profile", principal.claims)
        }
        return "index"
    }

    @GetMapping("profile")
    fun profile(model: Model, @AuthenticationPrincipal oidcUser: OidcUser): String {
        model.addAttribute("profile", oidcUser.claims)
        model.addAttribute("profileJson", claimsToJson(oidcUser.claims))
        return "profile"
    }

    private fun claimsToJson(claims: Map<String, Any>): String {
        try {
            return mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(claims)
        } catch (jpe: JsonProcessingException) {
            log.error("Error parsing claims to JSON", jpe)
        }
        return "Error parsing claims to JSON."
    }
}