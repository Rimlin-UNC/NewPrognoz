package com.meteoanalyst.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meteoanalyst.app.ui.theme.GlassDim
import com.meteoanalyst.app.ui.theme.GlassHighlight
import com.meteoanalyst.app.ui.theme.GlassStroke

/**
 * Стеклянная карточка Glassmorphism (ТЗ п.6): полупрозрачный градиент,
 * тонкая светлая кромка, скругления в стиле iOS.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier
            .border(0.5.dp, GlassStroke, shape)
            .background(
                Brush.verticalGradient(listOf(GlassHighlight, GlassDim)),
                shape
            )
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/**
 * Плавное «эффектное» появление элементов при первом входе:
 * каскад с задержкой по индексу (ТЗ: анимации появления).
 */
@Composable
fun StaggeredAppear(
    index: Int,
    content: @Composable () -> Unit
) {
    val state = remember {
        MutableTransitionState(false).apply { targetState = true }
    }
    val delayMillis = (index * 70).coerceAtMost(560)
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(animationSpec = tween(500, delayMillis = delayMillis)) +
            slideInVertically(
                animationSpec = tween(500, delayMillis = delayMillis),
                initialOffsetY = { it / 5 }
            )
    ) {
        content()
    }
}

/** Тонкая разделительная линия в стеклянном стиле. */
@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(vertical = 10.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.08f))
    )
}
