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
    fun getTree(repo:String) {
        val client = RestClient.builder()
            .requestFactory(HttpComponentsClientHttpRequestFactory())
            .baseUrl("https://api.github.com")
            .build()
        val response = client.get()
            .uri("repos/$repo/git/trees/main")
            .header("Accept", "application/vnd.github.v3.raw")
            .retrieve()
            .toEntity<String>()
        println(response.body)
    }
}

@Controller
@RequestMapping("/note")
class NoteController{

    @GetMapping
    fun showNote(model: Model, @AuthenticationPrincipal principal: OidcUser?): String {
        if (principal != null) {
            model.addAttribute("profile", principal.claims)
        }
        val githubService = GithubService()
        val content = githubService.getContent("repos/hvhai/public-vault/contents/Welcome.md")
        val markdownUtil = MarkdownUtil()
        val html = markdownUtil.renderHtml(content)
        model.addAttribute("content", html)
        return "note"
    }

}