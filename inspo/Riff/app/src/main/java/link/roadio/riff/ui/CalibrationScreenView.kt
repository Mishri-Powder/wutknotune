package link.roadio.riff.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/** Raw-framebuffer calibration surface. Every rendered dimension derives from W, H, S, or template width. */
@SuppressLint("ViewConstructor")
class CalibrationScreenView(
    context: Context,
    private val session: CalibrationSessionState,
    palette: UiPalette,
    var drawable: (String) -> Drawable,
    private val onCancel: () -> Unit,
    private val onAccept: (Float, Float) -> Unit,
) : View(context) {
    // Color injection never changes raw-pixel geometry or candidate measurements.
    // Authority: https://wiki.271.pics/Riff/LATEST/ui-color-system#controlled-and-image-backgrounds
    var palette: UiPalette = palette
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private fun translucent(
        color: Int,
        alpha: Int,
    ): Int = (color and 0x00ffffff) or (alpha shl 24)

    private enum class Mode { CARD, US_NOTE, EURO_NOTE }

    private enum class Menu { NONE, EURO }

    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hatch = Paint(Paint.ANTI_ALIAS_FLAG)
    private var mode = Mode.entries[session.modeIndex]
    private var menu = Menu.NONE
    private var euroIndex = session.euroIndex
    private var candidateXPxPerMm = session.candidateXPxPerMm ?: 1f
    private var candidateYPxPerMm = session.candidateYPxPerMm ?: 1f
    private var initialWidth = session.initialWidth ?: 1f
    private var initialHeight = session.initialHeight ?: 1f
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragEligible = false
    private var dragging = false
    private var message = ""
    private val modeRect = RectF()
    private val euroRect = RectF()
    private val cancelRect = RectF()
    private val okRect = RectF()
    private val menuRects = mutableListOf<RectF>()
    private val guidancePlate = RectF()
    private val densityPlate = RectF()
    private val protectedTextPlates = Path()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isFocusable = true
        contentDescription = "Screen calibration; drag anywhere above the buttons to resize the reference outline"
    }

    override fun onSizeChanged(
        width: Int,
        height: Int,
        oldWidth: Int,
        oldHeight: Int,
    ) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (session.isInitialized) {
            reflowInitialBounds()
        } else {
            resetCandidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val geometry = geometry()
        canvas.drawColor(palette.background)
        drawInstructions(canvas, geometry)
        drawProposedDensity(canvas, geometry)
        drawTemplate(canvas, geometry)
        drawBottomBar(canvas, geometry)
        if (menu != Menu.NONE) drawMenu(canvas, geometry)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && event.actionIndex != 0) return false
        val geometry = geometry()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = downX
                lastY = downY
                dragEligible = menu == Menu.NONE && inDragSurface(event.x, event.y, geometry)
                dragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragEligible || event.pointerCount != 1) return true
                if (!dragging) {
                    val activationDistance = geometry.scale * DRAG_ACTIVATION_FRACTION
                    if (
                        !CalibrationAdjustment.hasReachedActivationDistance(
                            deltaX = event.x - downX,
                            deltaY = event.y - downY,
                            activationDistancePx = activationDistance,
                        )
                    ) {
                        return true
                    }
                    dragging = true
                    lastX = downX
                    lastY = downY
                }
                val dx = event.x - lastX
                val dy = event.y - lastY
                val reference = referenceSize(geometry)
                when {
                    mode == Mode.CARD -> {
                        candidateXPxPerMm =
                            CalibrationAdjustment.apply(
                                currentPx = candidateXPxPerMm * reference.first,
                                deltaPx = dx,
                                initialPx = initialWidth,
                                canvasMaximumPx = geometry.canvas.width(),
                                minimumScale = MIN_SCALE,
                            ) / reference.first
                        candidateYPxPerMm =
                            CalibrationAdjustment.apply(
                                currentPx = candidateYPxPerMm * reference.second,
                                deltaPx = dy,
                                initialPx = initialHeight,
                                canvasMaximumPx = geometry.bar.bottom - geometry.canvas.top,
                                minimumScale = MIN_SCALE,
                            ) / reference.second
                    }

                    isPortraitNote(geometry) -> {
                        candidateYPxPerMm =
                            CalibrationAdjustment.apply(
                                currentPx = candidateYPxPerMm * reference.second,
                                deltaPx = dy,
                                initialPx = initialHeight,
                                canvasMaximumPx = geometry.canvas.height(),
                                minimumScale = MIN_SCALE,
                            ) / reference.second
                        candidateXPxPerMm = candidateYPxPerMm
                    }

                    else -> {
                        candidateXPxPerMm =
                            CalibrationAdjustment.apply(
                                currentPx = candidateXPxPerMm * reference.first,
                                deltaPx = dx,
                                initialPx = initialWidth,
                                canvasMaximumPx = geometry.canvas.width(),
                                minimumScale = MIN_SCALE,
                            ) / reference.first
                        candidateYPxPerMm = candidateXPxPerMm
                    }
                }
                saveSession()
                lastX = event.x
                lastY = event.y
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!dragging) {
                    handleTap(event.x, event.y, geometry)
                    performClick()
                }
                dragging = false
                dragEligible = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                dragEligible = false
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun handleTap(
        x: Float,
        y: Float,
        geometry: Geometry,
    ) {
        if (menu != Menu.NONE) {
            val selected = menuRects.indexOfFirst { it.contains(x, y) }
            if (selected >= 0) {
                euroIndex = selected
                session.euroIndex = euroIndex
                menu = Menu.NONE
                resetCandidate()
            } else {
                menu = Menu.NONE
            }
            invalidate()
            return
        }
        when {
            modeRect.contains(x, y) -> {
                mode = Mode.entries[CalibrationProposal.nextIndex(mode.ordinal, Mode.entries.size)]
                session.modeIndex = mode.ordinal
                resetCandidate()
            }

            euroRect.contains(x, y) && mode == Mode.EURO_NOTE -> {
                menu = Menu.EURO
            }

            cancelRect.contains(x, y) -> {
                onCancel()
            }

            okRect.contains(x, y) -> {
                accept(geometry)
            }
        }
        invalidate()
    }

    private fun accept(geometry: Geometry) {
        val ppiX = candidateXPxPerMm * MILLIMETRES_PER_INCH
        val ppiY = if (mode == Mode.CARD) candidateYPxPerMm * MILLIMETRES_PER_INCH else ppiX
        val diagonal = hypot(geometry.screenWidth / ppiX, geometry.screenHeight / ppiY)
        if (diagonal !in MIN_DIAGONAL..MAX_DIAGONAL) {
            message = "Implied diagonal %.1f in; adjust and retry".format(diagonal)
            invalidate()
            return
        }
        onAccept(ppiX, ppiY)
    }

    private fun drawTemplate(
        canvas: Canvas,
        geometry: Geometry,
    ) {
        val reference = referenceSize(geometry)
        val portraitNote = isPortraitNote(geometry)
        val width = if (portraitNote) geometry.canvas.width() else candidateXPxPerMm * reference.first
        val height =
            when {
                mode == Mode.CARD -> candidateYPxPerMm * reference.second
                portraitNote -> candidateYPxPerMm * reference.second
                else -> geometry.bar.bottom - geometry.canvas.top
            }
        val rect = RectF(geometry.canvas.left, geometry.canvas.top, geometry.canvas.left + width, geometry.canvas.top + height)
        val stroke = geometry.scale * STROKE_FRACTION
        val radius = if (mode == Mode.CARD) width * CARD_RADIUS_FRACTION else width * NOTE_RADIUS_FRACTION
        // Readable text plates exclude sample decoration; the one physical alignment border stays complete.
        // Authority: https://wiki.271.pics/Riff/LATEST/calibration-screen-layout#5-layout-continued-1
        protectedTextPlates.reset()
        val plateRadius = geometry.scale * TEXT_BOX_RADIUS_FRACTION
        protectedTextPlates.addRoundRect(guidancePlate, plateRadius, plateRadius, Path.Direction.CW)
        protectedTextPlates.addRoundRect(densityPlate, plateRadius, plateRadius, Path.Direction.CW)
        canvas.save()
        canvas.clipOutPath(protectedTextPlates)
        fill.color = translucent(palette.surface, 0x55)
        canvas.drawRoundRect(rect, radius, radius, fill)
        canvas.save()
        canvas.clipRect(rect)
        hatch.color = translucent(palette.muted, 0x66)
        hatch.strokeWidth = geometry.scale * HATCH_WIDTH_FRACTION
        val period = geometry.scale * HATCH_PERIOD_FRACTION
        var offset = -height
        while (offset < width) {
            canvas.drawLine(rect.left + offset, rect.bottom, rect.left + offset + height, rect.top, hatch)
            offset += period
        }
        canvas.restore()
        if (mode == Mode.CARD) {
            drawCardMagneticStripe(canvas, rect, radius, geometry)
        }

        val featherRadius = width * FEATHER_RADIUS_FRACTION
        line.color = translucent(palette.muted, 0x88)
        line.strokeWidth = stroke
        line.style = Paint.Style.STROKE
        line.maskFilter = BlurMaskFilter(featherRadius, BlurMaskFilter.Blur.NORMAL)
        val featherOffset = width * FEATHER_OFFSET_FRACTION
        if (mode == Mode.CARD) {
            val feather = RectF(rect.left, rect.top, rect.right + featherOffset, rect.bottom + featherOffset)
            canvas.drawRoundRect(feather, radius, radius, line)
        } else if (portraitNote) {
            canvas.drawLine(rect.left, rect.bottom + featherOffset, rect.right, rect.bottom + featherOffset, line)
        } else {
            canvas.drawLine(rect.right + featherOffset, rect.top, rect.right + featherOffset, rect.bottom, line)
        }
        canvas.restore()
        line.maskFilter = null
        line.color = palette.text
        line.strokeWidth = stroke
        line.style = Paint.Style.STROKE
        if (mode == Mode.CARD) {
            canvas.drawRoundRect(rect, radius, radius, line)
        } else if (portraitNote) {
            canvas.drawLine(rect.left, rect.top, rect.right, rect.top, line)
            canvas.drawLine(rect.left, rect.bottom, rect.right, rect.bottom, line)
        } else {
            canvas.drawLine(rect.left, rect.top, rect.left, rect.bottom, line)
            canvas.drawLine(rect.right, rect.top, rect.right, rect.bottom, line)
        }
    }

    private fun drawCardMagneticStripe(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        geometry: Geometry,
    ) {
        val shortEdge = min(rect.width(), rect.height())
        val stripeWidth = shortEdge * MAGNETIC_STRIPE_WIDTH_FRACTION
        val stripeCentre = shortEdge * MAGNETIC_STRIPE_CENTRE_FRACTION
        val stripe =
            if (isPortraitCard(geometry)) {
                RectF(
                    rect.left + stripeCentre - stripeWidth / 2f,
                    rect.top,
                    rect.left + stripeCentre + stripeWidth / 2f,
                    rect.bottom,
                )
            } else {
                RectF(
                    rect.left,
                    rect.top + stripeCentre - stripeWidth / 2f,
                    rect.right,
                    rect.top + stripeCentre + stripeWidth / 2f,
                )
            }
        val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        fill.color = translucent(palette.text, 0xAA)
        canvas.drawRect(stripe, fill)
        canvas.restore()
    }

    private fun drawInstructions(
        canvas: Canvas,
        geometry: Geometry,
    ) {
        val inset = geometry.scale * DENSITY_LABEL_INSET_FRACTION
        text.color = palette.text
        text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        text.textAlign = Paint.Align.CENTER
        text.textSize = geometry.scale * DENSITY_LABEL_TEXT_FRACTION
        val maxTextWidth = geometry.canvas.width() - inset * 4f
        val lines = instructionLines(geometry).flatMap { wrapInstruction(it, maxTextWidth) }
        val lineHeight = (text.descent() - text.ascent()) * INSTRUCTION_LINE_HEIGHT_MULTIPLIER
        val boxHeight = lineHeight * lines.size + inset * 2f
        val boxWidth = min(geometry.canvas.width(), (lines.maxOfOrNull(text::measureText) ?: 0f) + inset * 4f)
        val box =
            RectF(
                geometry.canvas.centerX() - boxWidth / 2f,
                geometry.canvas.bottom - inset - boxHeight,
                geometry.canvas.centerX() + boxWidth / 2f,
                geometry.canvas.bottom - inset,
            )
        guidancePlate.set(box)
        fill.color = palette.focused
        val radius = geometry.scale * TEXT_BOX_RADIUS_FRACTION
        canvas.drawRoundRect(box, radius, radius, fill)
        lines.forEachIndexed { index, value ->
            canvas.drawText(value, box.centerX(), box.top + inset - text.ascent() + index * lineHeight, text)
        }
    }

    private fun drawProposedDensity(
        canvas: Canvas,
        geometry: Geometry,
    ) {
        val ppiX = candidateXPxPerMm * MILLIMETRES_PER_INCH
        val ppiY = if (mode == Mode.CARD) candidateYPxPerMm * MILLIMETRES_PER_INCH else ppiX
        val value = CalibrationProposal.dpiLabel(ppiX, ppiY)
        val inset = geometry.scale * DENSITY_LABEL_INSET_FRACTION
        text.color = palette.muted
        text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        text.textAlign = Paint.Align.RIGHT
        text.textSize = geometry.scale * DENSITY_LABEL_TEXT_FRACTION
        val right = geometry.canvas.right - inset
        val baseline = geometry.canvas.top + inset - text.ascent()
        densityPlate.set(
            right - text.measureText(value) - inset / 2f,
            geometry.canvas.top + inset / 2f,
            right + inset / 2f,
            baseline + text.descent() + inset / 2f,
        )
        fill.color = palette.focused
        val radius = geometry.scale * TEXT_BOX_RADIUS_FRACTION
        canvas.drawRoundRect(densityPlate, radius, radius, fill)
        canvas.drawText(value, right, baseline, text)
    }

    private fun drawBottomBar(
        canvas: Canvas,
        geometry: Geometry,
    ) {
        // Each plate occludes the passive sample independently; gaps must expose its texture.
        // Authority: https://wiki.271.pics/Riff/LATEST/calibration-screen-layout#52-bottom-bar
        val gap = geometry.scale * GAP_FRACTION
        val usable = geometry.bar.width() - gap * 3f
        val selector = usable * SELECTOR_WIDTH_SHARE
        val action = usable * ACTION_WIDTH_SHARE
        modeRect.set(geometry.bar.left, geometry.bar.top, geometry.bar.left + selector, geometry.bar.bottom)
        euroRect.set(modeRect.right + gap, geometry.bar.top, modeRect.right + gap + selector, geometry.bar.bottom)
        cancelRect.set(euroRect.right + gap, geometry.bar.top, euroRect.right + gap + action, geometry.bar.bottom)
        okRect.set(cancelRect.right + gap, geometry.bar.top, geometry.bar.right, geometry.bar.bottom)
        drawControl(canvas, modeRect, modeLabel(), modeIcon(), true)
        drawControl(
            canvas,
            euroRect,
            "",
            denominationIcon(),
            mode == Mode.EURO_NOTE,
            SELECTOR_ICON_FRACTION,
        )
        drawControl(canvas, cancelRect, "", drawable("ic_cancel"), true)
        drawControl(canvas, okRect, "", drawable("ic_ok"), true)
    }

    private fun drawControl(
        canvas: Canvas,
        rect: RectF,
        label: String,
        icon: Drawable?,
        enabled: Boolean,
        iconFraction: Float? = null,
    ) {
        val radius = rect.height() * CONTROL_CORNER_RADIUS_FRACTION
        fill.color = if (enabled) palette.surface else translucent(palette.surface, INACTIVE_PLATE_ALPHA)
        canvas.drawRoundRect(rect, radius, radius, fill)
        val contentColor = if (enabled) palette.text else palette.disabledIcon
        val iconSize =
            rect.height() *
                (iconFraction ?: if (label.isBlank()) ACTION_ICON_FRACTION else SELECTOR_ICON_FRACTION)
        icon?.setTint(contentColor)
        val iconLeft =
            if (label.isBlank()) {
                rect.centerX() - iconSize / 2f
            } else {
                text.textSize = rect.height() * CONTROL_TEXT_FRACTION
                val groupWidth = iconSize + rect.height() * CONTROL_CONTENT_GAP_FRACTION + text.measureText(label)
                rect.centerX() - groupWidth / 2f
            }
        icon?.setBounds(
            iconLeft.toInt(),
            (rect.centerY() - iconSize / 2f).toInt(),
            (iconLeft + iconSize).toInt(),
            (rect.centerY() + iconSize / 2f).toInt(),
        )
        icon?.draw(canvas)
        if (label.isNotBlank()) {
            text.color = contentColor
            text.textAlign = Paint.Align.LEFT
            text.textSize = rect.height() * CONTROL_TEXT_FRACTION
            text.typeface = Typeface.DEFAULT
            canvas.drawText(
                label,
                iconLeft + iconSize + rect.height() * CONTROL_CONTENT_GAP_FRACTION,
                rect.centerY() - (text.ascent() + text.descent()) / 2f,
                text,
            )
        }
    }

    private fun drawMenu(
        canvas: Canvas,
        geometry: Geometry,
    ) {
        val labels = EURO_LABELS
        val itemHeight = geometry.scale * MENU_ITEM_HEIGHT_FRACTION
        val menuWidth = geometry.canvas.width() * MENU_WIDTH_FRACTION
        val left = geometry.canvas.right - menuWidth
        menuRects.clear()
        labels.forEachIndexed { index, label ->
            val rect =
                RectF(
                    left,
                    geometry.canvas.top + index * itemHeight,
                    geometry.canvas.right,
                    geometry.canvas.top + (index + 1) * itemHeight,
                )
            menuRects += rect
            fill.color = palette.surface
            canvas.drawRect(rect, fill)
            line.color = palette.text
            line.style = Paint.Style.STROKE
            line.strokeWidth = geometry.scale * STROKE_FRACTION
            canvas.drawRect(rect, line)
            text.color = palette.text
            text.textAlign = Paint.Align.CENTER
            text.textSize = itemHeight * MENU_TEXT_FRACTION
            canvas.drawText(label, rect.centerX(), rect.centerY() - (text.ascent() + text.descent()) / 2f, text)
        }
    }

    private fun resetCandidate() {
        if (width <= 0 || height <= 0) return
        val geometry = geometry()
        val reference = referenceSize(geometry)
        val scale = initialScale(geometry, reference)
        initialWidth = reference.first * scale
        initialHeight = reference.second * scale
        candidateXPxPerMm = scale
        candidateYPxPerMm = scale
        saveSession()
        message = ""
        invalidate()
    }

    private fun reflowInitialBounds() {
        if (width <= 0 || height <= 0) return
        val geometry = geometry()
        val reference = referenceSize(geometry)
        val scale = initialScale(geometry, reference)
        initialWidth = reference.first * scale
        initialHeight = reference.second * scale
        saveSession()
    }

    private fun initialScale(
        geometry: Geometry,
        reference: Pair<Float, Float>,
    ): Float =
        if (mode == Mode.CARD) {
            CalibrationProposal.orientationInvariantCardInitialScale(
                screenWidth = geometry.screenWidth,
                screenHeight = geometry.screenHeight,
                longEdgeMm = CARD_WIDTH_MM,
                shortEdgeMm = CARD_HEIGHT_MM,
                paddingFraction = PADDING_FRACTION,
                controlHeightFraction = CONTROL_HEIGHT_FRACTION,
                gapFraction = GAP_FRACTION,
                initialCap = INITIAL_CAP,
                initialReduction = INITIAL_REDUCTION,
            )
        } else {
            CalibrationProposal.orientationInvariantInitialScale(
                screenWidth = geometry.screenWidth,
                screenHeight = geometry.screenHeight,
                referenceWidthMm = reference.first,
                referenceHeightMm = reference.second,
                measureBothAxes = false,
                paddingFraction = PADDING_FRACTION,
                controlHeightFraction = CONTROL_HEIGHT_FRACTION,
                gapFraction = GAP_FRACTION,
                initialCap = INITIAL_CAP,
                initialReduction = INITIAL_REDUCTION,
            )
        }

    private fun saveSession() {
        session.modeIndex = mode.ordinal
        session.euroIndex = euroIndex
        session.candidateXPxPerMm = candidateXPxPerMm
        session.candidateYPxPerMm = candidateYPxPerMm
        session.initialWidth = initialWidth
        session.initialHeight = initialHeight
    }

    private fun inDragSurface(
        x: Float,
        y: Float,
        geometry: Geometry,
    ): Boolean = x >= 0f && x < geometry.screenWidth && y >= 0f && y < geometry.bar.top

    private fun isPortraitNote(geometry: Geometry) = mode != Mode.CARD && geometry.screenHeight > geometry.screenWidth

    private fun isPortraitCard(geometry: Geometry) = mode == Mode.CARD && geometry.screenHeight > geometry.screenWidth

    private fun geometry(): Geometry {
        val screenWidth = width.toFloat()
        val screenHeight = height.toFloat()
        val scale = min(screenWidth, screenHeight)
        val padding = scale * PADDING_FRACTION
        val barHeight = scale * CONTROL_HEIGHT_FRACTION
        val gap = scale * GAP_FRACTION
        return Geometry(
            screenWidth,
            screenHeight,
            scale,
            RectF(padding, padding, screenWidth - padding, screenHeight - padding - barHeight - gap),
            RectF(padding, screenHeight - padding - barHeight, screenWidth - padding, screenHeight - padding),
        )
    }

    private fun referenceSize(geometry: Geometry): Pair<Float, Float> =
        when (mode) {
            Mode.CARD -> {
                CalibrationProposal.orientationMatchedCardReference(
                    screenWidth = geometry.screenWidth,
                    screenHeight = geometry.screenHeight,
                    longEdgeMm = CARD_WIDTH_MM,
                    shortEdgeMm = CARD_HEIGHT_MM,
                )
            }

            Mode.US_NOTE -> {
                US_NOTE_WIDTH_MM to US_NOTE_WIDTH_MM
            }

            Mode.EURO_NOTE -> {
                EURO_WIDTHS_MM[euroIndex] to EURO_WIDTHS_MM[euroIndex]
            }
        }

    private fun modeLabel() =
        when (mode) {
            Mode.CARD -> "CARD"
            Mode.US_NOTE -> "\$US"
            Mode.EURO_NOTE -> "€"
        }

    private fun modeIcon() =
        drawable(
            when (mode) {
                Mode.CARD -> "ic_scale_card_ruled"

                Mode.US_NOTE,
                Mode.EURO_NOTE,
                -> "ic_scale_banknote"
            },
        )

    private fun denominationIcon() =
        if (mode != Mode.EURO_NOTE) {
            null
        } else {
            drawable(
                listOf(
                    "ic_scale_eur_5",
                    "ic_scale_eur_10",
                    "ic_scale_eur_20",
                    "ic_scale_eur_50",
                )[euroIndex],
            )
        }

    private fun instructionLines(geometry: Geometry): List<String> {
        val lines =
            when (mode) {
                Mode.CARD -> {
                    listOf(
                        "SCREEN CALIBRATION",
                        "CARD · 85.60 × 53.98 mm",
                        "Place card at upper-left.",
                        "Drag anywhere above buttons to resize.",
                        "Match solid right and bottom borders.",
                    )
                }

                Mode.US_NOTE -> {
                    listOf(
                        "SCREEN CALIBRATION",
                        "US NOTE · 66.29 mm",
                        "Place note at upper-left.",
                        if (isPortraitNote(geometry)) {
                            "Long edge left-to-right; match bottom border."
                        } else {
                            "Long edge top-to-bottom; match right border."
                        },
                        "Drag anywhere above buttons to resize.",
                    )
                }

                Mode.EURO_NOTE -> {
                    listOf(
                        "SCREEN CALIBRATION",
                        "EURO NOTE · ${EURO_LABELS[euroIndex]} · ${EURO_WIDTHS_MM[euroIndex].toInt()} mm",
                        "Place note at upper-left.",
                        if (isPortraitNote(geometry)) {
                            "Long edge left-to-right; match bottom border."
                        } else {
                            "Long edge top-to-bottom; match right border."
                        },
                        "Drag anywhere above buttons to resize.",
                    )
                }
            }
        return if (message.isBlank()) lines else lines + message
    }

    private fun wrapInstruction(
        value: String,
        maxWidth: Float,
    ): List<String> {
        if (text.measureText(value) <= maxWidth) return listOf(value)
        val lines = mutableListOf<String>()
        var current = ""
        value.split(' ').forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (current.isNotEmpty() && text.measureText(candidate) > maxWidth) {
                lines += current
                current = word
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    private data class Geometry(
        val screenWidth: Float,
        val screenHeight: Float,
        val scale: Float,
        val canvas: RectF,
        val bar: RectF,
    )

    private companion object {
        const val INACTIVE_PLATE_ALPHA = 0x33
        const val PADDING_FRACTION = 0.025f
        const val CONTROL_HEIGHT_FRACTION = 0.14f
        const val GAP_FRACTION = 0.02f
        const val STROKE_FRACTION = 0.008f
        const val HATCH_WIDTH_FRACTION = 0.006f
        const val HATCH_PERIOD_FRACTION = 0.018f
        const val DRAG_ACTIVATION_FRACTION = 0.010f
        const val INITIAL_CAP = 0.88f
        const val INITIAL_REDUCTION = 0.98f
        const val MIN_SCALE = 0.20f
        const val FEATHER_RADIUS_FRACTION = 0.025f
        const val FEATHER_OFFSET_FRACTION = 0.012f
        const val CARD_RADIUS_FRACTION = 0.037f
        const val NOTE_RADIUS_FRACTION = 0.006f
        const val TEXT_BOX_RADIUS_FRACTION = 0.024f
        const val INSTRUCTION_LINE_HEIGHT_MULTIPLIER = 1.12f
        const val DENSITY_LABEL_TEXT_FRACTION = 0.055f
        const val DENSITY_LABEL_INSET_FRACTION = 0.015f
        const val SELECTOR_WIDTH_SHARE = 0.20f
        const val ACTION_WIDTH_SHARE = 0.30f
        const val CONTROL_CORNER_RADIUS_FRACTION = 0.20f
        const val ACTION_ICON_FRACTION = 0.68f
        const val SELECTOR_ICON_FRACTION = 0.42f
        const val CONTROL_CONTENT_GAP_FRACTION = 0.08f
        const val CONTROL_TEXT_FRACTION = 0.28f
        const val MENU_ITEM_HEIGHT_FRACTION = 0.16f
        const val MENU_WIDTH_FRACTION = 0.42f
        const val MENU_TEXT_FRACTION = 0.30f
        const val CARD_WIDTH_MM = 85.60f
        const val CARD_HEIGHT_MM = 53.98f
        const val MAGNETIC_STRIPE_WIDTH_FRACTION = 12.70f / CARD_HEIGHT_MM
        const val MAGNETIC_STRIPE_CENTRE_FRACTION = 12.00f / CARD_HEIGHT_MM
        const val US_NOTE_WIDTH_MM = 66.29f
        const val MILLIMETRES_PER_INCH = 25.4f
        const val MIN_DIAGONAL = 4f
        const val MAX_DIAGONAL = 20f
        val EURO_WIDTHS_MM = listOf(62f, 67f, 72f, 77f)
        val EURO_LABELS = listOf("€5", "€10", "€20", "€50")
    }
}
