package com.github.adrienpessu.sarifviewer.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubPull(
    val prNumber: Int = 0,
    val head: GitHubBranch = GitHubBranch(),
    val base: GitHubBranch = GitHubBranch(),
    val prTitle: String = "",
    val commit: String = "",
)
