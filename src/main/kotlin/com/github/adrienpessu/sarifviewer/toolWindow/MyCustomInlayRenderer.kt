package com.github.adrienpessu.sarifviewer.toolWindow

import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Font
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D

class MyCustomInlayRenderer(private val text: String) : EditorCustomElementRenderer {

    private val myFont = Font("Courrier new", Font.ITALIC, 12)
    override fun paint(inlay: Inlay<*>, g: Graphics2D, targetRegion: Rectangle2D, textAttributes: TextAttributes) {

        (inlay.editor as EditorImpl).apply {
            g.font = myFont
            g.color = colorsScheme.defaultForeground
            g.drawString(text, targetRegion.x.toInt(), targetRegion.y.toInt() + ascent)
        }
    }

    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        return (inlay.editor as EditorImpl).getFontMetrics(myFont.style).stringWidth(text)
    }
}