package com.codehunter.spring_modulith_kotlin.todo.internal

import com.codehunter.spring_modulith_kotlin.share.IdNotFoundException
import com.codehunter.spring_modulith_kotlin.todo.toDomain
import com.codehunter.spring_modulith_kotlin.todo.toEntity
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Entity
@Table(name = "todo")
data class TodoEntity(
    @Id @GeneratedValue(strategy = GenerationType.UUID) val id: String?,
    val note: String, val isDone: Boolean
)


data class Todo(
    val id: String,
    var note: String,
    var isDone: Boolean = false
) {
    fun markDone(): Todo {
        this.isDone = true
        return this
    }

    fun updateNote(newNote: String): Todo {
        this.note = newNote
        return this
    }
}

@Repository
interface TodoRepository : JpaRepository<TodoEntity, String>

@Service
@Transactional
class TodoManager(private val todoRepository: TodoRepository) {
    fun createNewNote(note: String): Todo {
        val todo = TodoEntity(null, note, false)
        return todoRepository.save<TodoEntity>(todo).toDomain()
    }

    fun getNote(id: String): Todo {
        val todoEntity = todoRepository.findByIdOrNull(id) ?: throw IdNotFoundException("Todo with $id notfound")
        return todoEntity.toDomain()
    }

    fun getAllNote() = todoRepository.findAll().map { it.toDomain() }
    fun updateNote(id: String, newNote: String): Todo {
        val oldNote = todoRepository.findByIdOrNull(id) ?: throw IdNotFoundException("Todo with $id notfound")
        val updatedNote = oldNote.toDomain().updateNote(newNote)
        return todoRepository.save<TodoEntity>(updatedNote.toEntity()).toDomain()
    }

    fun markAsDone(id: String): Todo {
        val oldNote = todoRepository.findByIdOrNull(id) ?: throw IdNotFoundException("Todo with $id notfound")
        val updatedNote = oldNote.toDomain().markDone()
        return todoRepository.save<TodoEntity>(updatedNote.toEntity()).toDomain()
    }

    fun deleteNote(id: String) {
        todoRepository.deleteById(id)
    }
}


