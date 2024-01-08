package com.github.adrienpessu.sarifviewer.models

data class Leaf(
        val leafName: String,
        val address: String,
        val steps: List<String>,
        val location: String,
        val ruleId: String,
        val ruleName: String,
        val ruleDescription: String,
        val level: String,
        val kind: String,
        val githubAlertNumber: String,
        val githubAlertUrl: String,
) {
    override fun toString(): String {

        val icon = when (level) {
            "error" -> "🛑"
            "warning" -> "⚠️"
            "note" -> "📝"
            else -> ""
        }

        return "$icon $address"
    }
}