package com.codehunter.spring_modulith_kotlin.todo

import com.codehunter.spring_modulith_kotlin.todo.internal.Todo
import com.codehunter.spring_modulith_kotlin.todo.internal.TodoManager
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

@WebMvcTest(TodoController::class)
@WithMockUser
class TodoControllerTest(@Autowired val mockMvc: MockMvc, @Autowired val mapper: ObjectMapper) {
    @MockkBean
    lateinit var todoManager: TodoManager

    @Test
    fun `given existent note when get note by id then return note`() {
        // given
        val id = "id"
        every { todoManager.getNote(id) }.returns(Todo(id, "note", false))

        // when
        mockMvc.get("/api/todos/$id")
            .andExpect { status { isOk() } }
            .andExpect { content { contentType(MediaType.APPLICATION_JSON) } }
            .andExpect {
                jsonPath("$.data.note") {
                    value("note")
                }
            }
    }

    @Test
    fun `given 2 existent notes when get all notes then return 2 notes`() {
        // given
        every { todoManager.getAllNote() }
            .returns(
                listOf(
                    Todo("id-1", "note1", false),
                    Todo("id-2", "note2", false),
                )
            )

        // when
        mockMvc.get("/api/todos")
            .andExpect { status { isOk() } }
            .andExpect { content { contentType(MediaType.APPLICATION_JSON) } }
            .andExpectAll {
                jsonPath("$.data[0].id") { value("id-1") }
                jsonPath("$.data[0].note") { value("note1") }
                jsonPath("$.data[0].isDone") { value(false) }
                jsonPath("$.data[1].id") { value("id-2") }
                jsonPath("$.data[1].note") { value("note2") }
                jsonPath("$.data[1].isDone") { value(false) }
            }
    }

    @Test
    fun `given valid note value when create note then return new note`() {
        // given
        val expectedNote = Todo("id", "note", false)
        every { todoManager.createNewNote("note") }.returns(expectedNote)

        // when
        mockMvc.post("/api/todos") {
            content = mapper.writeValueAsString(CreateNoteRequest("note"))
            contentType = MediaType.APPLICATION_JSON
            with(csrf())
        }
            .andExpect { status { isCreated() } }
            .andExpect { content { contentType(MediaType.APPLICATION_JSON) } }
            .andExpectAll {
                jsonPath("$.data.id") { value("id") }
                jsonPath("$.data.note") { value("note") }
                jsonPath("$.data.isDone") { value(false) }
            }
    }

    @Test
    @WithMockUser
    fun `given existent note when update note by id then return updated note`() {
        // given
        val expectUpdatedNote = Todo("id", "note updated", false)
        every { todoManager.updateNote(any(), any()) }.returns(expectUpdatedNote)

        // when
        mockMvc.patch("/api/todos") {
            content = mapper.writeValueAsString(UpdateNoteRequest("id", "note updated"))
            contentType = MediaType.APPLICATION_JSON
            with(csrf())
        }
            .andExpect { status { isOk() } }
            .andExpect { content { contentType(MediaType.APPLICATION_JSON) } }
            .andExpectAll {
                jsonPath("$.data.id") { value("id") }
                jsonPath("$.data.note") { value("note updated") }
                jsonPath("$.data.isDone") { value(false) }
            }
    }
}