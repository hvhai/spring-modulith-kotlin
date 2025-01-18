package com.codehunter.spring_modulith_kotlin

import com.fasterxml.jackson.core.type.TypeReference
import org.springframework.core.ParameterizedTypeReference
import java.lang.reflect.Type

inline fun <reified T> typeReference() = object : ParameterizedTypeReference<T>() {}
inline fun <reified T : Any> ParameterizedTypeReference<T>.toJacksonTypeRef(): TypeReference<T> {
    val theType: Type = this.type
    return object : TypeReference<T>() {
        override fun getType(): Type = theType
    }
}
