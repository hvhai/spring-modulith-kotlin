package com.codehunter.spring_modulith_kotlin

import com.codehunter.spring_modulith_kotlin.share.UserDTO
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt

class AuthenticationUtil private constructor() {
    init {
        throw IllegalStateException("Utility class")
    }

    companion object {
        val user: UserDTO
            get() {
                val auth = SecurityContextHolder.getContext().authentication
                val principal = auth.principal as Jwt
                val userId = principal.getClaimAsString("sub")
                val username = principal.getClaimAsString("preferred_username")
                return UserDTO(userId, username)
            }
    }
}