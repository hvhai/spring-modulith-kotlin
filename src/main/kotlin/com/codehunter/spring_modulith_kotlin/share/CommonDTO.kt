package com.codehunter.spring_modulith_kotlin.share

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpStatus

enum class ErrorCodes(val status: HttpStatus, val title: String, val detail: String) {
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not Found", "Requested item was not found."),
    BAD_CLIENT_REQUEST(HttpStatus.BAD_REQUEST, "Invalid client request", "Received an invalid client request"),
    GENERIC_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Generic Exception", "An unexpected error was encountered."),
    UNHANDLED_EXCEPTION(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Unhandled Exception",
        "The service failed to handle an error."
    )
}

class MetaDataModel

data class ResponseDTO<T>(
    val data: T?,
    @JsonProperty("errors")
    val errorInfo: List<ApplicationError>?,
    val meta: MetaDataModel? = null
)

data class IdNotFoundException(override val message: String) : Exception(message)

class ApplicationError(
    @JsonIgnore val status: HttpStatus,
    @JsonProperty(value = "title") val title: String,
    @JsonProperty(value = "detail") val detail: String
)

data class UserDTO(val id: String, val username: String)

