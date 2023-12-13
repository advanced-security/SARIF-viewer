package com.github.adrienpessu.sarifviewer.util

import com.github.adrienpessu.sarifviewer.utils.GitHubInstance
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class GithubInstanceTest {
    companion object {
        const val DOTCOM_GIT_URL = "git@github.com:adrienpessu/SARIF-viewer.git"
        const val DOTCOM_HTTPS_URL = "https://github.com/adrienpessu/SARIF-viewer.git"
        const val GHES_GIT_URL = "git@github.private.domain:adrienpessu/SARIF-viewer.git"
        const val GHES_HTTPS_URL = "https://github.private.domain/adrienpessu/SARIF-viewer.git"

        const val DOTCOM_HOSTNAME = "github.com"
        const val GHES_HOSTNAME = "github.private.domain"
        const val REPO_NWO = "adrienpessu/SARIF-viewer"

        val DOTCOM_INSTANCE = GitHubInstance.DOT_COM
        val GHES_INSTANCE = GitHubInstance(GHES_HOSTNAME)
    }

    @Test
    fun testExtractHostnameFromSsh() {
        assertThat(GitHubInstance.extractHostname(DOTCOM_GIT_URL)).isEqualTo(DOTCOM_HOSTNAME)
        assertThat(GitHubInstance.extractHostname(GHES_GIT_URL)).isEqualTo(GHES_HOSTNAME)
    }

    @Test
    fun testExtractHostnameFromHttps() {
        assertThat(GitHubInstance.extractHostname(DOTCOM_HTTPS_URL)).isEqualTo(DOTCOM_HOSTNAME)
        assertThat(GitHubInstance.extractHostname(GHES_HTTPS_URL)).isEqualTo(GHES_HOSTNAME)
    }

    @Test
    fun testExtractRepoNwoFromSsh() {
        assertThat(DOTCOM_INSTANCE.extractRepoNwo(DOTCOM_GIT_URL)).isEqualTo(REPO_NWO)
        assertThat(GHES_INSTANCE.extractRepoNwo(GHES_GIT_URL)).isEqualTo(REPO_NWO)
    }

    @Test
    fun testExtractRepoNwoFromHttps() {
        assertThat(DOTCOM_INSTANCE.extractRepoNwo(DOTCOM_HTTPS_URL)).isEqualTo(REPO_NWO)
        assertThat(GHES_INSTANCE.extractRepoNwo(GHES_HTTPS_URL)).isEqualTo(REPO_NWO)
    }
}