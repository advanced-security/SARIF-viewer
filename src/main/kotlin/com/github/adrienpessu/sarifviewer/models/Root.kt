package com.github.adrienpessu.sarifviewer.models

import java.util.*

data class Root (
        var ref: String? = null,
        var commit_sha: String? = null,
        var analysis_key: String? = null,
        var environment: String? = null,
        var category: String? = null,
        var error: String? = null,
        var created_at: Date? = null,
        var results_count: Int = 0,
        var rules_count: Int = 0,
        var id: Int = 0,
        var url: String? = null,
        var sarif_id: String? = null,
        var tool: Tool? = null,
        var deletable: Boolean = false,
        var warning: String? = null
)
