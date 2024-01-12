package com.github.adrienpessu.sarifviewer.toolWindow

import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Desktop
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URI
import java.net.URISyntaxException
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

class UrlCellRenderer : JLabel(), TableCellRenderer {
    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {

        if (value.toString().startsWith("http")) {
            text = "<html><a href=''>$value</a></html>"
            toolTipText = value.toString()

            // Check if the value is a URL
            try {
                val uri = URI(value.toString())
                cursor = Cursor(Cursor.HAND_CURSOR)

                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        if (Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().browse(uri)
                        }
                    }
                })

                foreground = Color.BLUE
            } catch (e: URISyntaxException) {
                cursor = Cursor(Cursor.DEFAULT_CURSOR)
            }

            return this
        } else {
            text = value.toString()
            cursor = Cursor(Cursor.DEFAULT_CURSOR)
        }

        return this
    }
}