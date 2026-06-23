package io.rank5.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.rank5.app.ui.components.GameScaffold
import io.rank5.app.ui.components.InfoBanner
import io.rank5.app.ui.components.PrimaryCta
import io.rank5.app.ui.components.Rank5TopBar
import io.rank5.app.ui.components.SectionLabel
import io.rank5.app.ui.theme.Spacing

private val TopicExamples = listOf(
    "Weekend adventures",
    "Food around the world",
    "Movie night",
    "Dream vacations",
)

private val QuestionCounts = listOf(3, 6, 9, 12)

@Composable
fun DeckGeneratorScreen(
    topic: String,
    questionCount: Int,
    generating: Boolean,
    error: String?,
    snackbarHostState: SnackbarHostState,
    onTopic: (String) -> Unit,
    onQuestionCount: (Int) -> Unit,
    onGenerate: () -> Unit,
    onStartBlank: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    GameScaffold(
        snackbarHostState = snackbarHostState,
        scrollable = true,
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PrimaryCta(
                    text = if (generating) "Generating your draft…" else "Generate $questionCount questions",
                    onClick = onGenerate,
                    enabled = topic.trim().length >= 3 && !generating,
                    loading = generating,
                )
                TextButton(
                    onClick = onStartBlank,
                    enabled = !generating,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Start with a blank deck")
                }
            }
        },
    ) {
        Rank5TopBar("Create with AI", onBack = onBack)
        Spacer(Modifier.height(Spacing.lg))
        Text("What should the deck be about?", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Describe a theme your group will enjoy. We’ll create a private draft for you to review and edit.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.lg))
        OutlinedTextField(
            value = topic,
            onValueChange = onTopic,
            label = { Text("Topic") },
            placeholder = { Text("For example: weekend adventures") },
            supportingText = { Text("${topic.length} of 160") },
            enabled = !generating,
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.md))
        SectionLabel("TRY A TOPIC")
        Spacer(Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            TopicExamples.forEach { example ->
                AssistChip(
                    onClick = { onTopic(example) },
                    label = { Text(example) },
                    enabled = !generating,
                )
            }
        }
        Spacer(Modifier.height(Spacing.xl))
        SectionLabel("NUMBER OF QUESTIONS")
        Spacer(Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            QuestionCounts.forEach { count ->
                FilterChip(
                    selected = questionCount == count,
                    onClick = { onQuestionCount(count) },
                    label = { Text(count.toString()) },
                    enabled = !generating,
                )
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        InfoBanner("AI-generated content can be imperfect. Nothing is saved or published until you review the draft and tap Create deck.")
        if (error != null) {
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}
