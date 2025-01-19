package com.codehunter.spring_modulith_kotlin.todo

import com.codehunter.spring_modulith_kotlin.ContainerBaseTest
import com.codehunter.spring_modulith_kotlin.SpringModulithKotlinApplication
import com.codehunter.spring_modulith_kotlin.WiremockInitializer
import com.codehunter.spring_modulith_kotlin.share.ResponseDTO
import com.codehunter.spring_modulith_kotlin.todo.internal.Todo
import com.codehunter.spring_modulith_kotlin.todo.internal.TodoEntity
import com.codehunter.spring_modulith_kotlin.todo.internal.TodoRepository
import com.codehunter.spring_modulith_kotlin.typeReference
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.*
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull


@Testcontainers
@SpringBootTest(
    classes = arrayOf(SpringModulithKotlinApplication::class),
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@ContextConfiguration(initializers = arrayOf(WiremockInitializer::class))
@ActiveProfiles("integration")
class TodoIntegrationTest : ContainerBaseTest() {
    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var todoRepository: TodoRepository

    @Autowired
    lateinit var wiremockServer: WireMockServer

    @AfterEach
    fun tearDown() {
        todoRepository.deleteAll()
    }

    companion object {

    }

    @BeforeEach
    fun setup() {
        mockAuthenticate()
    }

    private fun mockAuthenticate(): StubMapping? {
        val rsaPublicJWK = rsaKey.toPublicJWK()
        val jwkResponse = "{\"keys\": [" +
                rsaPublicJWK.toJSONString() +
                "]}"

        // return mock JWK response
        return wiremockServer.stubFor(
            WireMock.get("/jwks.json")
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(jwkResponse)
                )
        )
    }


    @Test
    fun `given valid data when create note called then return new note`() {
        // when
        val actual: ResponseEntity<ResponseDTO<Todo>> =
            restTemplate.exchange(
                "/api/todos",
                HttpMethod.POST,
                HttpEntity(
                    CreateNoteRequest("new note"),
                    HttpHeaders().apply {
                        setBearerAuth(token)
                    }),
                typeReference<ResponseDTO<Todo>>()
            )
        // then
        // verify response
        assertNotNull(actual)
        assertEquals(HttpStatusCode.valueOf(201), actual.statusCode)

        val newTodo = actual.body?.data
        assertNotNull(newTodo?.id)
        assertEquals("new note", newTodo?.note)
        assertFalse(newTodo!!.isDone)

        // verify database
        val allNotes = todoRepository.findAll()
        assertEquals(1, allNotes.size)
        val newTodoEntity = allNotes.get(0)
        assertNotNull(newTodoEntity?.id)
        assertEquals("new note", newTodoEntity.note)
        assertFalse(newTodoEntity.isDone)
    }

    @Test
    fun `given existent note when get note by id then return note`() {
        // given
        val existentNote = todoRepository.save<TodoEntity>(TodoEntity(null, "note", true))
        val id = existentNote.id
        val expectedNote = Todo(id!!, "note", true)

        // when
        val actual: ResponseEntity<ResponseDTO<Todo>> =
            restTemplate.exchange(
                "/api/todos/$id",
                HttpMethod.GET,
                HttpEntity(
                    null,
                    HttpHeaders().apply {
                        setBearerAuth(token)
                    }),
                typeReference<ResponseDTO<Todo>>()
            )
        // then
        // verify response
        assertNotNull(actual)
        assertEquals(HttpStatusCode.valueOf(200), actual.statusCode)
        val newTodo = actual.body?.data
        assertEquals(expectedNote, newTodo)
    }

    @Test
    fun `given 2 existent notes when get all notes then return 2 notes`() {
        // given
        val notes = todoRepository.saveAll<TodoEntity>(
            listOf<TodoEntity>(
                TodoEntity(null, "note1", true),
                TodoEntity(null, "note2", false),
            )
        )
        // when
        val actual: ResponseEntity<ResponseDTO<List<Todo>>> =
            restTemplate.exchange(
                "/api/todos",
                HttpMethod.GET,
                HttpEntity(
                    null,
                    HttpHeaders().apply {
                        setBearerAuth(token)
                    }),
                typeReference<ResponseDTO<List<Todo>>>()
            )
        // then
        // verify response
        assertNotNull(actual)
        assertEquals(HttpStatusCode.valueOf(200), actual.statusCode)
        val newTodo = actual.body?.data
        assertEquals(notes.map { it.toDomain() }.sortedBy { it.note }, newTodo?.sortedBy { it.note })
    }

    @Test
    fun `given existent note when update note by id then return updated note`() {
//        val mockAuth = mockAuthenticate()
        // given
        val existentNote = todoRepository.save<TodoEntity>(TodoEntity(null, "note", true))
        val id = existentNote.id
        val expectedNote = Todo(id!!, "updated note", true)

        // when
        val actual: ResponseEntity<ResponseDTO<Todo>> =
            restTemplate.exchange(
                "/api/todos",
                HttpMethod.PATCH,
                HttpEntity(
                    UpdateNoteRequest(id, "updated note"),
                    HttpHeaders().apply {
                        setBearerAuth(token)
                    }),
                typeReference<ResponseDTO<Todo>>()
            )
        // then
        // verify response
        assertNotNull(actual)
        assertEquals(HttpStatusCode.valueOf(200), actual.statusCode)
        val newTodo = actual.body?.data
        assertEquals(expectedNote, newTodo)

        // verify database
        val updatedNoteEntity = todoRepository.findByIdOrNull(id)
        assertNotNull(updatedNoteEntity)
        assertEquals(TodoEntity(id, "updated note", true), updatedNoteEntity)
    }

    @Test
    fun `given existent note when mark not as done by id then return updated note`() {
        // given note with isDone = false
        val existentNote = todoRepository.save<TodoEntity>(TodoEntity(null, "note", false))
        val id = existentNote.id
        val expectedNote = Todo(id!!, "note", true)

        // when
        val actual: ResponseEntity<ResponseDTO<Todo>> =
            restTemplate.exchange(
                "/api/todos/$id/done",
                HttpMethod.PATCH,
                HttpEntity(
                    null,
                    HttpHeaders().apply {
                        setBearerAuth(token)
                    }),
                typeReference<ResponseDTO<Todo>>()
            )
        // then
        // verify response
        assertNotNull(actual)
        assertEquals(HttpStatusCode.valueOf(200), actual.statusCode)
        val newTodo = actual.body?.data
        assertEquals(expectedNote, newTodo)

        // verify database
        val updatedNoteEntity = todoRepository.findByIdOrNull(id)
        assertNotNull(updatedNoteEntity)
        assertEquals(TodoEntity(id, "note", true), updatedNoteEntity)
    }

    @Test
    fun `given existent note when delete note by id then note is deleted with empty response`() {
        // given
        val existentNote = todoRepository.save<TodoEntity>(TodoEntity(null, "note", true))
        val id = existentNote.id

        // when
        val actual: ResponseEntity<ResponseDTO<Todo>> =
            restTemplate.exchange(
                "/api/todos/$id",
                HttpMethod.DELETE,
                HttpEntity(
                    null,
                    HttpHeaders().apply {
                        setBearerAuth(token)
                    }),
                typeReference<ResponseDTO<Todo>>()
            )
        // then
        // verify response
        assertNotNull(actual)
        assertEquals(HttpStatusCode.valueOf(204), actual.statusCode)

        // verify database
        val updatedNoteEntity = todoRepository.findByIdOrNull(id)
        assertNull(updatedNoteEntity)
        wiremockServer.removeStub(WireMock.get("/jwks.json"))
    }
}
