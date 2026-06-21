package io.rank5.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import io.rank5.app.deck.DeckQuestion
import io.rank5.app.deck.MaxDeckQuestions
import io.rank5.app.deck.MinDeckQuestions
import io.rank5.app.ui.components.GameScaffold
import io.rank5.app.ui.components.InfoBanner
import io.rank5.app.ui.components.PrimaryCta
import io.rank5.app.ui.components.Rank5TopBar
import io.rank5.app.ui.components.SectionLabel
import io.rank5.app.ui.theme.Sizes
import io.rank5.app.ui.theme.Spacing

private val EmojiChoices = listOf("🎯", "🍕", "🎬", "🎭", "🤔", "🔥", "💡", "🧠", "🎮", "🎵", "🏀", "✈️", "🐶", "🌈", "🃏")

@Composable
fun DeckEditorScreen(
    title: String,
    emoji: String,
    questions: List<DeckQuestion>,
    saving: Boolean,
    isNew: Boolean,
    dirty: Boolean,
    generatedDraft: Boolean,
    snackbarHostState: SnackbarHostState,
    onTitle: (String) -> Unit,
    onEmoji: (String) -> Unit,
    onPrompt: (Int, String) -> Unit,
    onOption: (Int, Int, String) -> Unit,
    onAddQuestion: () -> Unit,
    onRemoveQuestion: (Int) -> Unit,
    onMoveQuestion: (Int, Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val leave = { if (dirty) confirmDiscard = true else onCancel() }
    BackHandler(onBack = leave)

    GameScaffold(
        snackbarHostState = snackbarHostState,
        scrollable = true,
        footer = {
            PrimaryCta(
                text = if (saving) "Saving…" else if (isNew) "Create deck" else "Save changes",
                onClick = { showErrors = true; onSave() },
                enabled = !saving,
                loading = saving,
            )
        },
    ) {
        Rank5TopBar(if (isNew) "New deck" else "Edit deck", onBack = leave)
        Spacer(Modifier.height(Spacing.lg))
        if (generatedDraft) {
            InfoBanner("AI-generated draft — review the title, questions, and every option before saving.")
            Spacer(Modifier.height(Spacing.lg))
        }
        Text("Deck identity", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Spacing.sm))
        OutlinedTextField(
            value = title,
            onValueChange = { if (it.length <= 60) onTitle(it) },
            label = { Text("Deck title") },
            supportingText = { Text(if (showErrors && title.isBlank()) "Add a title" else "${title.length} of 60") },
            isError = showErrors && title.isBlank(),
            singleLine = true,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.lg))
        SectionLabel("DECK ICON")
        Spacer(Modifier.height(Spacing.sm))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            EmojiChoices.forEach { choice ->
                Surface(
                    onClick = { onEmoji(choice) },
                    selected = emoji == choice,
                    enabled = !saving,
                    modifier = Modifier.size(Sizes.iconTile),
                    shape = MaterialTheme.shapes.small,
                    color = if (emoji == choice) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, if (emoji == choice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                ) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(choice, fontSize = 23.sp) } }
            }
        }
        Spacer(Modifier.height(Spacing.xl))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Questions", style = MaterialTheme.typography.titleLarge)
                Text("At least $MinDeckQuestions complete questions", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${questions.size}/$MaxDeckQuestions", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(Spacing.md))

        questions.forEachIndexed { qi, q ->
            QuestionEditor(
                index = qi,
                total = questions.size,
                question = q,
                enabled = !saving,
                showErrors = showErrors,
                onPrompt = { onPrompt(qi, it) },
                onOption = { oi, value -> onOption(qi, oi, value) },
                onRemove = { onRemoveQuestion(qi) },
                onMove = { to -> onMoveQuestion(qi, to) },
            )
            Spacer(Modifier.height(Spacing.md))
        }
        if (showErrors && questions.size < MinDeckQuestions) {
            Text("Add ${MinDeckQuestions - questions.size} more question${if (MinDeckQuestions - questions.size == 1) "" else "s"} to play.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(Spacing.sm))
        }
        if (questions.size < MaxDeckQuestions) OutlinedButton(
            onClick = onAddQuestion,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.size(Spacing.sm))
            Text("Add question")
        }
        Spacer(Modifier.height(Spacing.lg))
    }

    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text("Discard unsaved changes?") },
        text = { Text("Your edits to this deck haven’t been saved.") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; onCancel() }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
    )
}

@Composable
private fun QuestionEditor(
    index: Int,
    total: Int,
    question: DeckQuestion,
    enabled: Boolean,
    showErrors: Boolean,
    onPrompt: (String) -> Unit,
    onOption: (Int, String) -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().semantics {
            customActions = buildList {
                if (index > 0) add(CustomAccessibilityAction("Move question up") { onMove(index - 1); true })
                if (index < total - 1) add(CustomAccessibilityAction("Move question down") { onMove(index + 1); true })
            }
        },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Question ${index + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { onMove(index - 1) }, enabled = enabled && index > 0) {
                    Icon(Icons.Rounded.ArrowUpward, "Move question up")
                }
                IconButton(onClick = { onMove(index + 1) }, enabled = enabled && index < total - 1) {
                    Icon(Icons.Rounded.ArrowDownward, "Move question down")
                }
                IconButton(onClick = onRemove, enabled = enabled && total > 1) {
                    Icon(Icons.Filled.Delete, "Remove question", tint = MaterialTheme.colorScheme.error)
                }
            }
            OutlinedTextField(
                value = question.prompt,
                onValueChange = { if (it.length <= 200) onPrompt(it) },
                label = { Text("Prompt") },
                isError = showErrors && question.prompt.isBlank(),
                supportingText = { if (showErrors && question.prompt.isBlank()) Text("Add the question players will answer") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.sm))
            question.options.forEachIndexed { oi, value ->
                OutlinedTextField(
                    value = value,
                    onValueChange = { if (it.length <= 80) onOption(oi, it) },
                    label = { Text("Option ${oi + 1}") },
                    isError = showErrors && value.isBlank(),
                    supportingText = { if (showErrors && value.isBlank()) Text("Required") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (oi < 4) Spacer(Modifier.height(Spacing.xs))
            }
        }
    }
}
