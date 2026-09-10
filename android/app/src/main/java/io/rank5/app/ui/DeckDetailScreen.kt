package io.rank5.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import io.rank5.app.deck.Deck
import io.rank5.app.deck.DeckQuestion
import io.rank5.app.ui.components.DeckIconTile
import io.rank5.app.ui.components.GameScaffold
import io.rank5.app.ui.components.PrimaryCta
import io.rank5.app.ui.components.Rank5TopBar
import io.rank5.app.ui.components.ShimmerBlock
import io.rank5.app.ui.theme.Sizes
import io.rank5.app.ui.theme.Spacing

@Composable
fun DeckDetailScreen(
    deck: Deck?,
    loading: Boolean,
    saving: Boolean,
    canEdit: Boolean,
    signedIn: Boolean,
    isSaved: Boolean,
    savingSaved: Boolean,
    isDownloaded: Boolean = false,
    downloading: Boolean = false,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onUseDeck: (Deck) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPublish: () -> Unit,
    onUnpublish: () -> Unit,
    onReport: (String) -> Unit,
    onToggleSaved: () -> Unit,
    onToggleDownloaded: () -> Unit = {},
    onGoSignIn: () -> Unit,
    onGoSignInForSave: () -> Unit = onGoSignIn,
    resumeReportAfterSignIn: Boolean = false,
    onReportResumed: () -> Unit = {},
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var showReport by rememberSaveable { mutableStateOf(false) }
    var showReportSignIn by rememberSaveable { mutableStateOf(false) }
    var showSaveSignIn by rememberSaveable { mutableStateOf(false) }
    var reportReason by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(resumeReportAfterSignIn, signedIn) {
        if (resumeReportAfterSignIn && signedIn) {
            showReport = true
            onReportResumed()
        }
    }

    GameScaffold(
        snackbarHostState = snackbarHostState,
        scrollable = true,
        footer = deck?.let { selected ->
            {
                PrimaryCta(
                    text = "Play with this deck",
                    onClick = { onUseDeck(selected) },
                    enabled = !saving,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "You’ll choose the room settings on Play.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        },
    ) {
        Rank5TopBar(title = "Deck details", onBack = onBack)
        Spacer(Modifier.height(Spacing.md))

        if (loading && deck == null) {
            // Placeholder mirrors the hero card + first question cards.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Opening deck" },
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                ShimmerBlock(height = Sizes.deckHero * 3)
                repeat(3) { ShimmerBlock(height = Sizes.listRow * 2) }
            }
            return@GameScaffold
        }
        val current = deck ?: return@GameScaffold

        DeckHero(current, canEdit)
        Spacer(Modifier.height(Spacing.md))

        if (canEdit) {
            OwnerActions(
                deck = current,
                saving = saving,
                onEdit = onEdit,
                onPublish = onPublish,
                onUnpublish = onUnpublish,
            )
            Spacer(Modifier.height(Spacing.md))
        } else if (current.isBuiltin || current.isPublic) {
            OutlinedButton(
                onClick = { if (signedIn) onToggleSaved() else showSaveSignIn = true },
                enabled = !savingSaved,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        stateDescription = if (isSaved) "Saved" else "Not saved"
                    },
            ) {
                if (savingSaved) {
                    CircularProgressIndicator(
                        Modifier.size(Sizes.ctaSpinner),
                        strokeWidth = Sizes.ctaSpinnerStroke,
                    )
                } else {
                    Icon(
                        if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = null,
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                Text(if (isSaved) "Saved" else "Save")
            }
            Spacer(Modifier.height(Spacing.md))
        }

        OfflineAvailabilityAction(
            deck = current,
            downloaded = isDownloaded,
            loading = downloading,
            onToggle = onToggleDownloaded,
        )
        Spacer(Modifier.height(Spacing.md))

        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Inside this deck", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    deckContentsSummary(current.questions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${current.questions.size} total",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(Spacing.md))

        current.questions.forEachIndexed { index, question ->
            QuestionPreview(index + 1, question)
            if (index < current.questions.lastIndex) Spacer(Modifier.height(Spacing.md))
        }

        if (canEdit) {
            Spacer(Modifier.height(Spacing.md))
            HorizontalDivider()
            Spacer(Modifier.height(Spacing.md))
            Text("Danger area", style = MaterialTheme.typography.titleMedium)
            Text(
                "Deleting removes this deck permanently.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { confirmDelete = true },
                enabled = !saving,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete deck") }
        } else if (current.isPublic && !current.isBuiltin) {
            Spacer(Modifier.height(Spacing.md))
            TextButton(
                onClick = { if (signedIn) showReport = true else showReportSignIn = true },
                enabled = !saving,
            ) { Text("Report this deck") }
        }
        Spacer(Modifier.height(Spacing.md))
    }

    if (showReportSignIn) AlertDialog(
        onDismissRequest = { showReportSignIn = false },
        title = { Text("Sign in to report") },
        text = { Text("Reports are tied to an account to prevent abuse. You’ll return here after signing in.") },
        confirmButton = {
            TextButton(onClick = { showReportSignIn = false; onGoSignIn() }) { Text("Continue to sign in") }
        },
        dismissButton = { TextButton(onClick = { showReportSignIn = false }) { Text("Cancel") } },
    )

    if (showSaveSignIn) AlertDialog(
        onDismissRequest = { showSaveSignIn = false },
        title = { Text("Sign in to save decks") },
        text = { Text("Sign in to keep saved decks with your profile.") },
        confirmButton = {
            TextButton(onClick = { showSaveSignIn = false; onGoSignInForSave() }) { Text("Sign in") }
        },
        dismissButton = { TextButton(onClick = { showSaveSignIn = false }) { Text("Not now") } },
    )

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this deck?") },
        text = { Text("This permanently deletes the deck and can’t be undone.") },
        confirmButton = {
            TextButton(
                onClick = { confirmDelete = false; onDelete() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )

    if (showReport) AlertDialog(
        onDismissRequest = { showReport = false },
        title = { Text("Report this deck?") },
        text = {
            OutlinedTextField(
                value = reportReason,
                onValueChange = { reportReason = it.take(500) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Reason") },
                placeholder = { Text("Spam, offensive content, or another issue") },
                minLines = 2,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val reason = reportReason.trim()
                    if (reason.isNotEmpty()) {
                        showReport = false
                        reportReason = ""
                        onReport(reason)
                    }
                },
                enabled = reportReason.isNotBlank(),
            ) { Text("Submit report") }
        },
        dismissButton = { TextButton(onClick = { showReport = false }) { Text("Cancel") } },
    )
}

@Composable
private fun OfflineAvailabilityAction(
    deck: Deck,
    downloaded: Boolean,
    loading: Boolean,
    onToggle: () -> Unit,
) {
    if (deck.isBuiltin) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.DownloadDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(Spacing.md))
                Column {
                    Text("Available offline", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Included with Rank5",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        return
    }

    OutlinedButton(
        onClick = onToggle,
        enabled = !loading,
        modifier = Modifier.fillMaxWidth().semantics {
            stateDescription = if (downloaded) "Available offline" else "Not downloaded"
        },
    ) {
        if (loading) {
            CircularProgressIndicator(
                Modifier.size(Sizes.ctaSpinner),
                strokeWidth = Sizes.ctaSpinnerStroke,
            )
        } else {
            Icon(
                if (downloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                contentDescription = null,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(if (downloaded) "Remove download" else "Download for offline play")
    }
}

@Composable
private fun DeckHero(deck: Deck, canEdit: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeckIconTile(
                    emoji = deck.emoji,
                    deckName = deck.title,
                    size = Sizes.deckHero,
                    containerColor = MaterialTheme.colorScheme.surface,
                )
                Spacer(Modifier.width(Spacing.md))
                DeckStatus(deck, canEdit)
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                deck.title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(Spacing.md))
            MetadataLine(
                icon = { Icon(Icons.Rounded.FormatListNumbered, contentDescription = null) },
                text = "${deck.questions.size} ranking questions",
            )
            Spacer(Modifier.height(Spacing.sm))
            MetadataLine(
                icon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                text = "Created by ${deck.ownerName.ifBlank { if (deck.isBuiltin) "Rank5" else "Community creator" }}",
            )
        }
    }
}

@Composable
private fun DeckStatus(deck: Deck, canEdit: Boolean) {
    val (label, icon) = when {
        deck.isBuiltin -> "Official deck" to Icons.Rounded.CheckCircle
        deck.isRemoved -> "Removed" to Icons.Rounded.Lock
        deck.isPublic -> "Public deck" to Icons.Rounded.Public
        canEdit -> "Your private deck" to Icons.Rounded.Lock
        else -> "Private deck" to Icons.Rounded.Lock
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(Sizes.metadataIcon),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Spacing.sm))
            Text(label, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun MetadataLine(icon: @Composable () -> Unit, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            modifier = Modifier.size(Sizes.compactIcon),
        ) { Box(contentAlignment = Alignment.Center) { icon() } }
        Spacer(Modifier.width(Spacing.sm))
        Text(text, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun OwnerActions(
    deck: Deck,
    saving: Boolean,
    onEdit: () -> Unit,
    onPublish: () -> Unit,
    onUnpublish: () -> Unit,
) {
    Text("Manage deck", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(Spacing.sm))
    if (deck.isRemoved) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
            Text(
                "This deck was removed from the community library, but remains editable here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(Spacing.md),
            )
        }
        Spacer(Modifier.height(Spacing.sm))
    }
    OutlinedButton(onClick = onEdit, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Rounded.Edit, contentDescription = null)
        Spacer(Modifier.width(Spacing.sm))
        Text("Edit questions")
    }
    if (!deck.isRemoved) {
        Spacer(Modifier.height(Spacing.sm))
        OutlinedButton(
            onClick = if (deck.isPublic) onUnpublish else onPublish,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(if (deck.isPublic) Icons.Rounded.Lock else Icons.Rounded.Public, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text(if (deck.isPublic) "Make private" else "Publish to community")
        }
    }
}

/** Describes what a deck asks of a room, since not every question is a five-option list. */
fun deckContentsSummary(questions: List<DeckQuestion>): String {
    val playerQuestions = questions.count { it.usesPlayersAsOptions }
    return when {
        questions.isEmpty() -> "This deck has no questions yet."
        playerQuestions == questions.size -> "Players rank each other. Needs 3+ players."
        playerQuestions > 0 ->
            "Players rank five options, and $playerQuestions question" +
                "${if (playerQuestions == 1) "" else "s"} rank each other."
        else -> "Players rank the five options in each question."
    }
}

@Composable
private fun QuestionPreview(number: Int, question: DeckQuestion) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Sizes.hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    modifier = Modifier.size(Sizes.actionIcon),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(number.toString(), style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text("Question $number", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(question.prompt, style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(Modifier.height(Spacing.md))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(Spacing.sm))
            if (question.usesPlayersAsOptions) {
                Text(
                    "Everyone in the room is an option. Needs 3+ players.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            question.options.forEachIndexed { index, option ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        (index + 1).toString().padStart(2, '0'),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Text(option, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
