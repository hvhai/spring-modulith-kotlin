package com.codehunter.spring_modulith_kotlin.share

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.ResponseStatus


object ResponseFormatter {
    fun <T> handleList(items: Set<T>): ResponseEntity<ResponseDTO<Set<T>>> {
        val response: ResponseDTO<Set<T>> = ResponseDTO(items, null, null)
        return ResponseEntity(response, HttpHeaders(), HttpStatus.OK)
    }

    fun <T> handleList(items: List<T>?): ResponseEntity<ResponseDTO<List<T>>> {
        val response: ResponseDTO<List<T>> = ResponseDTO(items, null, null)
        return ResponseEntity(response, HttpHeaders(), HttpStatus.OK)
    }

    fun handleException(errorCode: ErrorCodes, detailMsg: String?): ResponseDTO<String> {
        val error = ApplicationError(errorCode.status, errorCode.title, detailMsg ?: errorCode.detail)
        val response: ResponseDTO<String> = ResponseDTO(null, listOf(error), null)
        return response
    }

    fun <T> handleSingle(item: T, headers: HttpHeaders?, status: HttpStatus): ResponseEntity<ResponseDTO<T>> {
        return ResponseEntity(ResponseDTO(item, null, null), headers, status)
    }
}

@ControllerAdvice
class ExceptionHandler() {
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(IdNotFoundException::class)
    @ResponseBody
    fun handleNotFound(request: HttpServletRequest, exception: Exception) =
        ResponseFormatter.handleException(ErrorCodes.NOT_FOUND, exception.message)

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseBody
    fun handleIllegalArgumentException(request: HttpServletRequest, exception: Exception) =
        ResponseFormatter.handleException(ErrorCodes.BAD_CLIENT_REQUEST, exception.message)
}
