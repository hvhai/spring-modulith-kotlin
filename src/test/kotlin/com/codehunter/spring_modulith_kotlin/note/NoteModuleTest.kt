package com.codehunter.spring_modulith_kotlin.note

import org.junit.jupiter.api.Test

class NoteModuleTest {
    @Test
    fun `should render markdown to html`() {
        // Given
        val markdown = "This is *Markdown*"
        val markdownUtil = MarkdownUtil()

        // When
        val html = markdownUtil.renderHtml(markdown)

        // Then
        assert(html == "<p>This is <em>Markdown</em></p>\n")
    }

    @Test
    fun `should get content from github`() {
        // Given
        val githubService = GithubService()

        // When
        val content = githubService.getContent("repos/hvhai/public-vault/contents/Welcome.md")

        // Then
        assert(content.isNotBlank())
    }

    @Test
    fun `should get tree from github`() {
        // Given
        val githubService = GithubService()

        // When
        val tree = githubService.getTree("hvhai", "public-vault", "0b388bccbe72932dc448448650e2e68fda87218b")

        // Then
        assert(tree.root.children.isNotEmpty())
    }
}