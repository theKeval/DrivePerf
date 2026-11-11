@file:Suppress("TooManyFunctions") // small building blocks shared by every page, kept in one file

package com.driveperf.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The frame every page shares: a top bar (Back, step progress, optional Skip), scrolling content and a docked set of
 * buttons that stays in view at the largest text size.
 */
@Composable
fun OnboardingPage(
    state: OnboardingUiState,
    actions: OnboardingActions,
    style: PageStyle = PageStyle(),
    dock: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val (showSkip, centered) = style
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 8.dp)) {
        TopBar(state, actions, showSkip)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
            content = content,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp),
            content = dock,
        )
    }
}

@Composable
private fun TopBar(state: OnboardingUiState, actions: OnboardingActions, showSkip: Boolean) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.canGoBack) {
                TextButton(onClick = actions.onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.onb_back)) }
            }
            Box(modifier = Modifier.weight(1f))
            state.setup?.let { setup ->
                val description = stringResource(R.string.onb_setup_step, setup.number, setup.total)
                Text(
                    text = stringResource(R.string.onb_setup_step_short, setup.number, setup.total),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { contentDescription = description },
                )
            }
            if (showSkip) {
                TextButton(onClick = actions.onSkipIntro, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.onb_skip))
                }
            }
        }
        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
    }
}

/** A page title (the heading for screen readers) and one lead paragraph. */
@Composable
fun Heading(title: String, lead: String?, centered: Boolean = false) {
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLargeEmphasized,
            textAlign = align,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        if (lead != null) {
            Text(
                text = lead,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = align,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The filled primary button (56 dp, full width). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = ButtonDefaults.shape,
    ) { Text(text) }
}

/** The quiet secondary action under the primary one. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(text) }
}

@Composable
fun TonalButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) { Text(text) }
}

/** A line in a checklist: a glyph disc and its text, with an optional supporting line. */
@Composable
fun ChecklistRow(mark: Mark, title: String, modifier: Modifier = Modifier, detail: String? = null) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        GlyphDisc(mark.glyph, positive = mark != Mark.No)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) {
                Text(text = detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun GlyphDisc(glyph: String, modifier: Modifier = Modifier, positive: Boolean = true, size: Int = 28) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (positive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (positive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(size.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().clearAndSetSemantics { }) {
            Text(glyph, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The big shaped tile that stands in for each page's illustration: a cookie shape holding a short mark. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtTile(mark: String, modifier: Modifier = Modifier, size: Int = 112, muted: Boolean = false) {
    Surface(
        shape = MaterialShapes.Cookie9Sided.toShape(),
        color = if (muted) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.size(size.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().clearAndSetSemantics { }) {
            Text(mark, style = MaterialTheme.typography.displaySmallEmphasized)
        }
    }
}

/** A card holding a group of rows. */
@Composable
fun GroupCard(modifier: Modifier = Modifier, highlighted: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val body: @Composable ColumnScope.() -> Unit = {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
    if (highlighted) {
        OutlinedCard(
            modifier = modifier.fillMaxWidth(),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
            content = body,
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            content = body,
        )
    }
}

/** A numbered step with its settings path and an optional deep-link button. */
@Composable
fun NumberedStep(
    number: Int,
    title: String,
    modifier: Modifier = Modifier,
    path: String? = null,
    button: (@Composable () -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        GlyphDisc(number.toString())
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (path != null) {
                Text(text = path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            button?.invoke()
        }
    }
}

/** What / Why / Where in the background-location disclosure. */
@Composable
fun DefinitionRow(term: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(term, style = MaterialTheme.typography.labelLarge, modifier = Modifier.widthIn(min = 56.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
