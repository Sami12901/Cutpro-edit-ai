package com.cutpro.core.subtitles.renderer

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.cutpro.core.subtitles.karaoke.KaraokeEngine
import com.cutpro.core.subtitles.model.CaptionCue
import com.cutpro.core.subtitles.model.CaptionLayout
import com.cutpro.core.subtitles.model.CaptionStyle
import com.cutpro.core.subtitles.model.HorizontalAlignment
import com.cutpro.core.subtitles.model.TimedWord
import com.cutpro.core.subtitles.model.VerticalAlignment

class SubtitleCanvasRenderer {

    private data class WordLayout(
        val text: String,
        val originalWordIndex: Int, // >= 0 if from activeCue.words, -1 if plain text
        val width: Float
    )

    private data class LineLayout(
        val words: List<WordLayout>,
        val lineWidth: Float,
        val startX: Float,
        val baselineY: Float
    )

    fun renderAt(
        canvas: Canvas,
        viewportWidth: Int,
        viewportHeight: Int,
        timestampUs: Long,
        cues: List<CaptionCue>,
        defaultStyle: CaptionStyle = CaptionStyle.DEFAULT
    ) {
        if (viewportWidth <= 0 || viewportHeight <= 0) return
        val activeCue = KaraokeEngine.findActiveCue(cues, timestampUs) ?: return
        val style = activeCue.customStyle ?: defaultStyle
        val layout = activeCue.layout

        val scaleFactor = (viewportHeight / 360.0f).coerceAtLeast(1.0f)
        val scaledFontSize = style.fontSizeSp * scaleFactor

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.textColorArgb.toInt()
            textSize = scaledFontSize
            typeface = if (style.fontWeight >= 700) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            if (style.shadowColorArgb != 0L) {
                setShadowLayer(
                    style.shadowBlurPx * scaleFactor,
                    style.shadowOffsetX * scaleFactor,
                    style.shadowOffsetY * scaleFactor,
                    style.shadowColorArgb.toInt()
                )
            }
        }

        val strokePaint = if (style.strokeWidthPx > 0f) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.strokeColorArgb.toInt()
                textSize = scaledFontSize
                this.style = Paint.Style.STROKE
                strokeWidth = style.strokeWidthPx * scaleFactor
                typeface = if (style.fontWeight >= 700) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
        } else null

        val availableWidth = (viewportWidth * (1.0f - 2.0f * layout.marginHorizontalPercent)).coerceAtLeast(1.0f)
        val spaceWidth = textPaint.measureText(" ")
        val activeWordIndex = KaraokeEngine.findActiveWordIndex(activeCue, timestampUs)

        // 1. Build wrapped lines of words
        val linesOfWords = if (activeCue.words.isNotEmpty()) {
            wrapTimedWords(
                words = activeCue.words,
                rawText = activeCue.text,
                textPaint = textPaint,
                spaceWidth = spaceWidth,
                availableWidth = availableWidth,
                maxLines = layout.maxLines
            )
        } else {
            wrapPlainText(
                rawText = activeCue.text,
                textPaint = textPaint,
                spaceWidth = spaceWidth,
                availableWidth = availableWidth,
                maxLines = layout.maxLines
            )
        }

        if (linesOfWords.isEmpty()) return

        // 2. Compute vertical baselines for each line
        val lineCount = linesOfWords.size
        val lineSpacing = scaledFontSize * 1.3f
        val lineBaselines = FloatArray(lineCount)

        when (layout.verticalAlignment) {
            VerticalAlignment.TOP -> {
                val firstBaseline = viewportHeight * layout.marginVerticalPercent + scaledFontSize
                for (k in 0 until lineCount) {
                    lineBaselines[k] = firstBaseline + k * lineSpacing
                }
            }
            VerticalAlignment.CENTER -> {
                val totalBlockHeight = (lineCount - 1) * lineSpacing
                val firstBaseline = (viewportHeight - totalBlockHeight) * 0.5f + scaledFontSize * 0.35f
                for (k in 0 until lineCount) {
                    lineBaselines[k] = firstBaseline + k * lineSpacing
                }
            }
            VerticalAlignment.BOTTOM -> {
                val lastBaseline = viewportHeight * (1.0f - layout.marginVerticalPercent)
                for (k in 0 until lineCount) {
                    lineBaselines[k] = lastBaseline - (lineCount - 1 - k) * lineSpacing
                }
            }
        }

        // 3. Compute horizontal positioning and LineLayout objects
        val lines = linesOfWords.mapIndexed { k, wordList ->
            val textWidth = wordList.sumOf { it.width.toDouble() }.toFloat() +
                spaceWidth * (wordList.size - 1).coerceAtLeast(0)
            val startX = when (layout.horizontalAlignment) {
                HorizontalAlignment.START -> viewportWidth * layout.marginHorizontalPercent
                HorizontalAlignment.CENTER -> (viewportWidth - textWidth) * 0.5f
                HorizontalAlignment.END -> viewportWidth * (1.0f - layout.marginHorizontalPercent) - textWidth
            }
            LineLayout(
                words = wordList,
                lineWidth = textWidth,
                startX = startX,
                baselineY = lineBaselines[k]
            )
        }

        // 4. Draw background box if enabled
        if (style.backgroundColorArgb != 0L) {
            val padH = style.backgroundPaddingHorizontalPx * scaleFactor
            val padV = style.backgroundPaddingVerticalPx * scaleFactor
            val r = style.backgroundCornerRadiusPx * scaleFactor
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.backgroundColorArgb.toInt()
                this.style = Paint.Style.FILL
            }

            val minX = lines.minOf { it.startX } - padH
            val maxX = lines.maxOf { it.startX + it.lineWidth } + padH
            val minY = lines.first().baselineY - scaledFontSize - padV * 0.5f
            val maxY = lines.last().baselineY + padV * 0.8f

            val boxRect = RectF(minX, minY, maxX, maxY)
            canvas.drawRoundRect(boxRect, r, r, bgPaint)
        }

        // 5. Draw words with two-pass outline & sample-accurate karaoke highlight
        for (line in lines) {
            var curX = line.startX
            val y = line.baselineY

            for (wordItem in line.words) {
                val isActive = (wordItem.originalWordIndex >= 0 && wordItem.originalWordIndex == activeWordIndex)
                val curColor = if (isActive) style.activeWordColorArgb.toInt() else style.textColorArgb.toInt()
                val curScale = if (isActive) style.activeWordScaleFactor else 1.0f

                textPaint.color = curColor
                if (curScale != 1.0f) {
                    canvas.save()
                    val wordCenterX = curX + wordItem.width * 0.5f
                    val wordCenterY = y - scaledFontSize * 0.5f
                    canvas.scale(curScale, curScale, wordCenterX, wordCenterY)
                    strokePaint?.let { canvas.drawText(wordItem.text, curX, y, it) }
                    canvas.drawText(wordItem.text, curX, y, textPaint)
                    canvas.restore()
                } else {
                    strokePaint?.let { canvas.drawText(wordItem.text, curX, y, it) }
                    canvas.drawText(wordItem.text, curX, y, textPaint)
                }

                curX += wordItem.width + spaceWidth
            }
        }
    }

    private fun wrapTimedWords(
        words: List<TimedWord>,
        rawText: String,
        textPaint: Paint,
        spaceWidth: Float,
        availableWidth: Float,
        maxLines: Int
    ): List<List<WordLayout>> {
        // Detect explicit newline boundaries between words from rawText
        val newlineAfterWord = BooleanArray(words.size)
        var searchPos = 0
        for (i in 0 until words.size - 1) {
            val wText = words[i].word
            val idx = rawText.indexOf(wText, searchPos)
            if (idx >= 0) {
                val nextWText = words[i + 1].word
                val nextIdx = rawText.indexOf(nextWText, idx + wText.length)
                if (nextIdx >= 0) {
                    val gap = rawText.substring(idx + wText.length, nextIdx)
                    if (gap.contains('\n')) {
                        newlineAfterWord[i] = true
                    }
                    searchPos = nextIdx
                }
            }
        }

        val result = mutableListOf<MutableList<WordLayout>>()
        var curLine = mutableListOf<WordLayout>()
        var curLineWidth = 0f

        for (i in words.indices) {
            val timedWord = words[i]
            val cleanWord = timedWord.word.replace("\n", "").trim()
            if (cleanWord.isEmpty()) continue

            val wWidth = textPaint.measureText(cleanWord)
            val wordItem = WordLayout(cleanWord, originalWordIndex = i, width = wWidth)

            val testWidth = if (curLine.isEmpty()) wWidth else curLineWidth + spaceWidth + wWidth
            val prevHadNewline = if (i > 0) newlineAfterWord[i - 1] else false
            val exceedsWidth = curLine.isNotEmpty() && testWidth > availableWidth

            if ((prevHadNewline || exceedsWidth) && result.size < maxLines - 1) {
                result.add(curLine)
                curLine = mutableListOf(wordItem)
                curLineWidth = wWidth
            } else {
                curLine.add(wordItem)
                curLineWidth = testWidth
            }
        }

        if (curLine.isNotEmpty() && result.size < maxLines) {
            result.add(curLine)
        }

        return result
    }

    private fun wrapPlainText(
        rawText: String,
        textPaint: Paint,
        spaceWidth: Float,
        availableWidth: Float,
        maxLines: Int
    ): List<List<WordLayout>> {
        val explicitLines = rawText.split('\n')
        val result = mutableListOf<MutableList<WordLayout>>()
        var curLine = mutableListOf<WordLayout>()
        var curLineWidth = 0f

        for (expLine in explicitLines) {
            val tokens = expLine.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (tokens.isEmpty()) continue

            // Starting a new explicit line forces a line break if current line has content
            if (curLine.isNotEmpty() && result.size < maxLines - 1) {
                result.add(curLine)
                curLine = mutableListOf()
                curLineWidth = 0f
            }

            for (tok in tokens) {
                val wWidth = textPaint.measureText(tok)
                val wordItem = WordLayout(tok, originalWordIndex = -1, width = wWidth)
                val testWidth = if (curLine.isEmpty()) wWidth else curLineWidth + spaceWidth + wWidth

                if (curLine.isNotEmpty() && testWidth > availableWidth && result.size < maxLines - 1) {
                    result.add(curLine)
                    curLine = mutableListOf(wordItem)
                    curLineWidth = wWidth
                } else {
                    curLine.add(wordItem)
                    curLineWidth = testWidth
                }
            }
        }

        if (curLine.isNotEmpty() && result.size < maxLines) {
            result.add(curLine)
        }

        return result
    }
}
