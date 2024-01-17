package com.github.adrienpessu.sarifviewer.models

data class View(
    val key: String = "",
    val value: String = ""
) {
    override fun toString() = value

    companion object {
        val RULE = View("rules", "View by rules")
        val LOCATION = View("location", "View by location")
        val ALERT_NUMBER = View("alert num", "View by GHAS alert number")
        val views = arrayOf(RULE, LOCATION, ALERT_NUMBER)
    }
}

