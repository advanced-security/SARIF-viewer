package com.github.adrienpessu.sarifviewer.services

import com.github.adrienpessu.sarifviewer.utils.GitHubInstance
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions.assertThat

class GitHubAuthenticationServiceTest : BasePlatformTestCase() {
    
    private lateinit var authService: GitHubAuthenticationService
    
    override fun setUp() {
        super.setUp()
        authService = GitHubAuthenticationService(project)
    }
    
    fun testGitHubDotComUsesBuiltInAuth() {
        val github = GitHubInstance.DOT_COM
        assertThat(github.useBuiltInAuth).isTrue()
    }
    
    fun testGHESUsesTokenAuth() {
        val ghes = GitHubInstance("github.private.example")
        assertThat(ghes.useBuiltInAuth).isFalse()
    }
    
    fun testGetAuthenticatedTokenForGHESWithToken() {
        val ghes = GitHubInstance("github.private.example")
        ghes.token = "test-token"
        
        val token = authService.getAuthenticatedToken(ghes)
        assertThat(token).isEqualTo("test-token")
    }
    
    fun testGetAuthenticatedTokenForGHESWithoutToken() {
        val ghes = GitHubInstance("github.private.example")
        // No token set
        
        val token = authService.getAuthenticatedToken(ghes)
        assertThat(token).isNull()
    }
}