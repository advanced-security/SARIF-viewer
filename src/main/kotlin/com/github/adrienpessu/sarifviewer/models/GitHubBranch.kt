package com.github.adrienpessu.sarifviewer.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubBranch (
    val ref: String = "",
    val commit_sha: String = "",
)