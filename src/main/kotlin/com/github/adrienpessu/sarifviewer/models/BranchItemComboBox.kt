package com.github.adrienpessu.sarifviewer.models

data class BranchItemComboBox(
    val prNumber: Int = 0,
    val head: String = "",
    val base: String = "",
    val prTitle: String = "",
    val commit: String = "",
) {
    override fun toString(): String {
        if (prNumber == 0) {
            return head
        } else {
            return "pr$prNumber ($prTitle)"
        }
    }
}