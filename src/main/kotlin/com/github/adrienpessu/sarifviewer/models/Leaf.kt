package com.github.adrienpessu.sarifviewer.models

data class Leaf(
        val leafName: String,
        val steps: List<String>,
        val location: String,
        val ruleName: String,
        val ruleDescription: String,
        val level: String,
        val githubAlertNumber: String,
        val githubAlertUrl: String,
) {
    override fun toString(): String {
        return leafName
    }
}