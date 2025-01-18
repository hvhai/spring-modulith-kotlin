package com.codehunter.spring_modulith_kotlin.ui

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/")
class UIController {
    @GetMapping
    fun getIndexPage() = "index"
}