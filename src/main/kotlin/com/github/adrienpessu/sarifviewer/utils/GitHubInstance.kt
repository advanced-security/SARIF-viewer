package com.github.adrienpessu.sarifviewer.utils

import org.jetbrains.annotations.VisibleForTesting
import java.net.URI

data class GitHubInstance(val hostname: String, val apiBase: String = "https://$hostname/api/v3") {
    // Keep this out of the constructor so that it doesn't accidentally end up in a toString() output
    var token: String = ""
    
    // Flag to indicate if this instance should use IntelliJ's built-in GitHub authentication
    val useBuiltInAuth: Boolean = hostname == "github.com"

    fun extractRepoNwo(remoteUrl: String?): String? {
        if (remoteUrl?.startsWith("https") == true) {
            return URI(remoteUrl).path.replace(Regex("^/"), "").replace(Regex(".git$"), "")
        } else if (remoteUrl?.startsWith("git@") == true) {
            return remoteUrl.replace(Regex("^git@$hostname:"), "").replace(Regex(".git$"), "")
        }
        return null
    }

    companion object {
        val DOT_COM: GitHubInstance = GitHubInstance("github.com", "https://api.github.com")

        @VisibleForTesting
        fun extractHostname(remoteUrl: String?): String? {
            return if (remoteUrl?.startsWith("https") == true) {
                URI(remoteUrl).host
            } else if (remoteUrl?.startsWith("git@") == true) {
                remoteUrl.substringAfter("git@").substringBefore(":")
            } else {
                null
            }
        }

        fun fromRemoteUrl(remoteUrl: String): GitHubInstance? {
            val hostname = extractHostname(remoteUrl)
            if (hostname == DOT_COM.hostname) {
                return DOT_COM
            } else if (!hostname.isNullOrEmpty()){
                return GitHubInstance(hostname)
            }
            return null
        }
    }
}
