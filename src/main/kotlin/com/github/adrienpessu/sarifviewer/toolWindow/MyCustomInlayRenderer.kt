package com.github.adrienpessu.sarifviewer.toolWindow

import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Font
import java.awt.Graphics
import java.awt.Rectangle

class MyCustomInlayRenderer(private val text: String) : EditorCustomElementRenderer {

    private val myFont = Font("Courrier new", Font.ITALIC, 12)
    override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {

        (inlay.editor as EditorImpl).apply {
            g.font = myFont
            g.color = colorsScheme.defaultForeground
            g.drawString(text, targetRegion.x, targetRegion.y + ascent)
        }
    }

    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        return (inlay.editor as EditorImpl).getFontMetrics(myFont.style).stringWidth(text)
    }
}