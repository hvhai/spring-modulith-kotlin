package com.codehunter.spring_modulith_kotlin.note

import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.client.RestClient
import org.springframework.web.client.toEntity


class MarkdownUtil {
    fun renderHtml(markdown: String): String {
        val parser: Parser = Parser.builder().build()
        val document: Node = parser.parse(markdown)
        val renderer = HtmlRenderer.builder().build()
        return renderer.render(document)
    }
}

class GithubService {
    fun getContent(path: String): String {
        val client = RestClient.builder()
            .requestFactory(HttpComponentsClientHttpRequestFactory())
            .baseUrl("https://api.github.com")
            .build()
        val response = client.get()
            .uri(path)
            .header("Accept", "application/vnd.github.v3.raw")
            .retrieve()
            .toEntity<String>()
        return response.body ?: ""
    }

    fun getTree(user: String, repo: String, hash: String): NoteTree {
        val client = RestClient.builder()
            .requestFactory(HttpComponentsClientHttpRequestFactory())
            .baseUrl("https://api.github.com")
            .build()
        val response = client.get()
            .uri("repos/$user/$repo/git/trees/$hash?recursive=true")
            .header("Accept", "application/vnd.github.v3.raw")
            .retrieve()
            .toEntity<GhTree>()
        println(response.body)
        val ghTree = response.body ?: GhTree("", "", listOf(), false)
        val noteTree = NoteTree(NoteTreeItem(mutableListOf(), repo, repo, null))

        ghTree.tree.forEach {
            var current = noteTree.root
            val path = it.path.split("/")
            for (item in path) {
                val child = current.children.find { it.filename.split("/").last() == item }
                if (child != null) {
                    current = child
                } else {
                    val newChild = NoteTreeItem(mutableListOf(), item, it.path, current)
                    current.children.add(newChild)
                    current = newChild
                }
            }
        }
        // display all path of the tree noteTree.root.children
        displayTree(noteTree, "")
        return noteTree
    }

    private fun displayTree(noteTree: NoteTree, indent: String = "") {
        noteTree.root.children.forEach {
            println("$indent${it.filename} [${it.path}]")
            displayTree(
                NoteTree(NoteTreeItem(it.children, it.filename, it.path, it.parent)), "$indent  "
            )
        }
    }
}

class GhTree(val sha: String, val url: String, val tree: List<GhTreeItem>, val truncated: Boolean) {
}

class GhTreeItem(
    val path: String,
    val mode: String,
    val type: String,
    val sha: String,
    val size: Int,
    val url: String
)

class NoteTree(val root: NoteTreeItem)

class NoteTreeItem(
    val children: MutableList<NoteTreeItem> = mutableListOf<NoteTreeItem>(),
    val filename: String,
    val path: String,
    val parent: NoteTreeItem?
)

@Controller
@RequestMapping("/note")
class NoteController {

    @GetMapping
    fun showNote(
        model: Model,
        @RequestParam user: String?,
        @RequestParam repo: String?,
        @RequestParam hash: String?,
        @AuthenticationPrincipal principal: OidcUser?,
        @RequestParam displayPath: String?
    ): String {
        if (principal != null) {
            model.addAttribute("profile", principal.claims)
        }
        val githubService = GithubService()

        // tree section
        val noteTree = githubService.getTree(
            user ?: "Froussios",
            repo ?: "Intro-To-RxJava",
            hash ?: "e9da6ce5ea836352503f180d7fda7fc50000142a"
        )
        model.addAttribute("noteTree", noteTree.root)
        model.addAttribute("user", user ?: "Froussios")
        model.addAttribute("repo", repo ?: "Intro-To-RxJava")
        model.addAttribute("hash", hash ?: "e9da6ce5ea836352503f180d7fda7fc50000142a")

        // content section
        if (displayPath != null) {
            val content = githubService.getContent("repos/${user}/${repo}/contents/${displayPath}")
            val markdownUtil = MarkdownUtil()
            val html = markdownUtil.renderHtml(content)
            model.addAttribute("content", html)
            model.addAttribute("displayPath", displayPath)
        } else {
            model.addAttribute("content", "")
        }

        return "note"
    }
}