package com.github.adrienpessu.sarifviewer.services

import com.github.adrienpessu.sarifviewer.utils.GitHubInstance
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

@Service(Service.Level.PROJECT)
class GitHubAuthenticationService(private val project: Project) {
    
    fun getAuthenticatedToken(gitHubInstance: GitHubInstance): String? {
        return when {
            gitHubInstance.useBuiltInAuth -> {
                getGitHubDotComToken()
            }
            else -> {
                // For GHES instances, use the configured token
                if (gitHubInstance.token.isNotEmpty()) {
                    gitHubInstance.token
                } else {
                    null
                }
            }
        }
    }
    
    private fun getGitHubDotComToken(): String? {
        // Try to get token from git credentials
        return try {
            getTokenFromGitCredentials()
        } catch (e: Exception) {
            // If git credentials don't work, prompt for authentication
            promptForGitHubAuth()
        }
    }
    
    private fun getTokenFromGitCredentials(): String? {
        // This would typically use git credential helper
        // For now, return null to trigger authentication prompt
        return null
    }
    
    private fun promptForGitHubAuth(): String? {
        // Show dialog to user explaining they need to authenticate
        val result = Messages.showOkCancelDialog(
            project,
            "GitHub authentication is required to access the API.\n\n" +
                    "Please ensure you're logged into GitHub in IntelliJ IDEA.\n" +
                    "Go to Settings > Version Control > GitHub to configure authentication.",
            "GitHub Authentication Required",
            "Open Settings",
            "Cancel",
            Messages.getInformationIcon()
        )
        
        if (result == Messages.OK) {
            // Open settings - this would typically open the GitHub settings page
            // For now, we'll just return null and let the user configure manually
            return null
        }
        
        return null
    }
    
    fun isGitHubDotComAuthenticated(): Boolean {
        val token = getGitHubDotComToken()
        return token != null && token.isNotEmpty()
    }
    
    fun testAuthentication(gitHubInstance: GitHubInstance): Boolean {
        val token = getAuthenticatedToken(gitHubInstance)
        if (token == null || token.isEmpty()) {
            return false
        }
        
        return try {
            val connection = URI("${gitHubInstance.apiBase}/user")
                .toURL()
                .openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }
            
            connection.responseCode == 200
        } catch (e: IOException) {
            false
        }
    }
}