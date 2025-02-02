package com.codehunter.spring_modulith_kotlin

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
    fromApplication<SpringModulithKotlinApplication>().with(TestContainersConfiguration::class).run(*args)
}
