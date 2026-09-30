package com.cueback.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cueback.app.ui.theme.Backdrop
import com.cueback.app.ui.theme.Ember
import com.cueback.app.ui.theme.EmberBackdrop

private val GlassStroke = Brush.verticalGradient(listOf(Ember.GlassStrokeTop, Ember.GlassStrokeBottom))
private val FlameFill = Brush.verticalGradient(listOf(Ember.Glow, Ember.Flame, Color(0xFFF2560D)))

/** Smoked glass: a translucent fill with a hairline that catches light along the top edge. */
fun Modifier.glass(shape: Shape, fill: Color = Ember.Glass): Modifier =
    this.background(fill, shape).border(1.dp, GlassStroke, shape).clip(shape)

@Composable
private fun pressScale(source: InteractionSource): Float {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.55f, stiffness = 700f), label = "press")
    return scale
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    fill: Color = Ember.Glass,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    padding: PaddingValues = PaddingValues(18.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .glass(shape, fill)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/** The one primary action on a screen: a flame pill with dark ink, lit from underneath. */
@Composable
fun EmberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    compact: Boolean = false,
) {
    val source = remember { MutableInteractionSource() }
    val scale = pressScale(source)
    Row(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.38f
            }
            .drawBehind {
                if (!enabled || compact) return@drawBehind
                // Light spilling onto the ground under the pill: a circle squashed into an ellipse, so it fades out on every side.
                val pool = Offset(size.width / 2f, size.height * 0.92f)
                val reach = size.width * 0.52f
                scale(scaleX = 1f, scaleY = (size.height * 1.15f) / reach, pivot = pool) {
                    drawCircle(
                        Brush.radialGradient(listOf(Color(0x66FF6A1A), Color(0x00FF6A1A)), center = pool, radius = reach),
                        radius = reach,
                        center = pool,
                    )
                }
            }
            .defaultMinSize(minHeight = if (compact) 36.dp else 54.dp)
            .background(FlameFill, CircleShape)
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x8CFFE8C4), Color(0x00FFE8C4))), CircleShape)
            .clip(CircleShape)
            .clickable(interactionSource = source, indication = ripple(color = Ember.Ink), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (compact) 16.dp else 26.dp, vertical = if (compact) 8.dp else 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Ember.Ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            color = Ember.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Secondary action: a glass pill. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = Ember.Cream,
    icon: ImageVector? = null,
) {
    val source = remember { MutableInteractionSource() }
    val scale = pressScale(source)
    Row(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.38f
            }
            .defaultMinSize(minHeight = 54.dp)
            .glass(CircleShape)
            .clickable(interactionSource = source, indication = ripple(color = Ember.Cream), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A choice pill. [single] marks it as one of a set where only one can be chosen. */
@Composable
fun EmberChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, single: Boolean = false) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) Ember.Ink else Ember.Cream,
        maxLines = 1,
        modifier = modifier
            .then(if (selected) Modifier.background(FlameFill, CircleShape).clip(CircleShape) else Modifier.glass(CircleShape))
            .then(
                if (single) Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                else Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() }),
            )
            .padding(horizontal = 16.dp, vertical = 11.dp),
    )
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    fill: Color = Ember.Glass,
) {
    Box(
        modifier.size(size).glass(CircleShape, fill).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Ember.Cream, modifier = Modifier.size(20.dp))
    }
}

/** The glowing round button: the screen's hot spot. */
@Composable
fun OrbButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val source = remember { MutableInteractionSource() }
    val scale = pressScale(source)
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                val r = this.size.minDimension
                drawCircle(Brush.radialGradient(listOf(Color(0x80FF6A1A), Color(0x00FF6A1A)), radius = r * 1.05f), radius = r * 1.05f)
                drawCircle(
                    Brush.radialGradient(
                        0f to Ember.Core,
                        0.4f to Ember.Glow,
                        0.8f to Ember.Flame,
                        1f to Color(0xFFE04E0C),
                        center = Offset(r * 0.36f, r * 0.28f),
                        radius = r * 0.85f,
                    ),
                )
            }
            .border(1.dp, Color(0x66FFF3E8), CircleShape)
            .clip(CircleShape)
            .clickable(interactionSource = source, indication = ripple(color = Ember.Ink), role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Ember.Ink, modifier = Modifier.size(size * 0.42f))
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    TextField(
        value = value,
        onValueChange = onValueChange,
        // The hairline would wrap the supporting text too, so it steps aside while a message shows.
        modifier = if (supportingText == null) modifier.border(1.dp, GlassStroke, shape) else modifier,
        label = { Text(label) },
        singleLine = singleLine,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        shape = shape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Ember.GlassStrong,
            unfocusedContainerColor = Ember.Glass,
            disabledContainerColor = Ember.Glass,
            errorContainerColor = Ember.Glass,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            cursorColor = Ember.Glow,
            focusedLabelColor = Ember.Glow,
            unfocusedLabelColor = Ember.Ash,
            focusedTextColor = Ember.Cream,
            unfocusedTextColor = Ember.Cream,
            focusedTrailingIconColor = Ember.Cream,
            unfocusedTrailingIconColor = Ember.Ash,
        ),
    )
}

/** Frame for every screen below home: the backdrop, a glass back button and the title. */
@Composable
fun EmberScaffold(
    title: String,
    onBack: () -> Unit,
    backdrop: Backdrop = Backdrop.Dusk,
    snackbarHost: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    EmberBackdrop(backdrop) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = Ember.Cream,
            snackbarHost = snackbarHost,
            topBar = {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                    Text(title, style = MaterialTheme.typography.titleLarge, color = Ember.Cream, modifier = Modifier.weight(1f).semantics { heading() })
                    actions()
                }
            },
            content = content,
        )
    }
}
