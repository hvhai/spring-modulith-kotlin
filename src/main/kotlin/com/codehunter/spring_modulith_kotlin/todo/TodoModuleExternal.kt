package com.codehunter.spring_modulith_kotlin.todo

import com.codehunter.spring_modulith_kotlin.share.ResponseDTO
import com.codehunter.spring_modulith_kotlin.todo.internal.Todo
import com.codehunter.spring_modulith_kotlin.todo.internal.TodoEntity
import com.codehunter.spring_modulith_kotlin.todo.internal.TodoManager
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class TodoDTO(
    val id: String,
    val note: String,
    val isDone: Boolean
)

data class CreateNoteRequest(val note: String)
data class UpdateNoteRequest(val id: String, val note: String)

fun TodoEntity.toDomain(): Todo = Todo(id ?: throw IllegalArgumentException("is is null"), note, isDone)
fun Todo.toEntity(): TodoEntity = TodoEntity(id, note, isDone)
fun Todo.toDTO(): TodoDTO = TodoDTO(id, note, isDone)

@RestController
@RequestMapping("/api/todos")
class TodoController(private val todoManager: TodoManager) {
    val logger = LoggerFactory.getLogger(this::class.java)

    @PostMapping
    fun createNote(@RequestBody body: CreateNoteRequest): ResponseEntity<ResponseDTO<TodoDTO>> {
        logger.info("Create new note")
        return ResponseEntity(ResponseDTO(todoManager.createNewNote(body.note).toDTO(), null), HttpStatus.CREATED)
    }

    @GetMapping
    fun getAllNote(): ResponseEntity<ResponseDTO<List<TodoDTO>>> {
        logger.info("Get all note")
        return ResponseEntity(
            ResponseDTO(data = todoManager.getAllNote().map { it.toDTO() }, errorInfo = null),
            HttpStatus.OK
        )
    }

    @GetMapping("/{id}")
    fun getNote(@PathVariable id: String): ResponseEntity<ResponseDTO<TodoDTO>> {
        logger.info("Get note with id=$id")
        val response = ResponseDTO(data = todoManager.getNote(id).toDTO(), errorInfo = null)
        return ResponseEntity<ResponseDTO<TodoDTO>>(response, HttpStatus.OK)
    }

    @PatchMapping
    fun updateNote(@RequestBody body: UpdateNoteRequest): ResponseEntity<ResponseDTO<TodoDTO>> {
        logger.info("Update note with id=${body.id}")
        return ResponseEntity(
            ResponseDTO(data = todoManager.updateNote(body.id, body.note).toDTO(), errorInfo = null),
            HttpStatus.OK
        )
    }

    @PatchMapping("/{id}/done")
    fun markNoteAsDone(@PathVariable id: String): ResponseEntity<ResponseDTO<TodoDTO>> {
        logger.info("Mark note with id=$id as done")
        return ResponseEntity(ResponseDTO(data = todoManager.markAsDone(id).toDTO(), errorInfo = null), HttpStatus.OK)
    }

    @DeleteMapping("/{id}")
    fun deleteNote(@PathVariable id: String): ResponseEntity<HttpStatus> {
        logger.info("Delete note with id=$id")
        todoManager.deleteNote(id)
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build<HttpStatus>()
    }
}
