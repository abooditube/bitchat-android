package com.bitchat.android.ui
// [Goose] TODO: Replace inline file attachment stub with FilePickerButton abstraction that dispatches via FileShareDispatcher


import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.R
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.withStyle
import com.bitchat.android.ui.theme.BASE_FONT_SIZE
import com.bitchat.android.features.voice.normalizeAmplitudeSample
import com.bitchat.android.features.voice.AudioWaveformExtractor
import com.bitchat.android.ui.media.RealtimeScrollingWaveform
import com.bitchat.android.ui.media.ImagePickerButton
import com.bitchat.android.ui.media.FilePickerButton

/**
 * Input components for ChatScreen
 * Extracted from ChatScreen.kt for better organization
 * 
 * Updated with WhatsApp-style design:
 * - Rounded container background
 * - Emoji button on left
 * - Text field in center
 * - Attachment and camera buttons
 * - Mic/Send button that transforms
 */

/**
 * VisualTransformation that styles slash commands with background and color
 * while preserving cursor positioning and click handling
 */
class SlashCommandVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val slashCommandRegex = Regex("(/\\w+)(?=\\s|$)")
        val annotatedString = buildAnnotatedString {
            var lastIndex = 0

            slashCommandRegex.findAll(text.text).forEach { match ->
                // Add text before the match
                if (match.range.first > lastIndex) {
                    append(text.text.substring(lastIndex, match.range.first))
                }

                // Add the styled slash command
                withStyle(
                    style = SpanStyle(
                        color = Color(0xFF00FF7F), // Bright green
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        background = Color(0xFF2D2D2D) // Dark gray background
                    )
                ) {
                    append(match.value)
                }

                lastIndex = match.range.last + 1
            }

            // Add remaining text
            if (lastIndex < text.text.length) {
                append(text.text.substring(lastIndex))
            }
        }

        return TransformedText(
            text = annotatedString,
            offsetMapping = OffsetMapping.Identity
        )
    }
}

/**
 * VisualTransformation that styles mentions with background and color
 * while preserving cursor positioning and click handling
 */
class MentionVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val mentionRegex = Regex("@([a-zA-Z0-9_]+)")
        val annotatedString = buildAnnotatedString {
            var lastIndex = 0
            
            mentionRegex.findAll(text.text).forEach { match ->
                // Add text before the match
                if (match.range.first > lastIndex) {
                    append(text.text.substring(lastIndex, match.range.first))
                }
                
                // Add the styled mention
                withStyle(
                    style = SpanStyle(
                        color = Color(0xFFFF9500), // Orange
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                ) {
                    append(match.value)
                }
                
                lastIndex = match.range.last + 1
            }
            
            // Add remaining text
            if (lastIndex < text.text.length) {
                append(text.text.substring(lastIndex))
            }
        }
        
        return TransformedText(
            text = annotatedString,
            offsetMapping = OffsetMapping.Identity
        )
    }
}

/**
 * VisualTransformation that combines multiple visual transformations
 */
class CombinedVisualTransformation(private val transformations: List<VisualTransformation>) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        var resultText = text
        
        // Apply each transformation in order
        transformations.forEach { transformation ->
            resultText = transformation.filter(resultText).text
        }
        
        return TransformedText(
            text = resultText,
            offsetMapping = OffsetMapping.Identity
        )
    }
}

/**
 * WhatsApp-style icon button with consistent styling
 */
@Composable
fun WhatsAppIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    tint: Color = Color.Gray,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(40.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * WhatsApp-style message input with rounded container
 * Features:
 * - Rounded pill-shaped container background
 * - Emoji icon on left (decorative)
 * - Text input in center
 * - Attachment and camera buttons on right (when empty)
 * - Mic button that transforms to send button
 */
@Composable
fun MessageInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onSendVoiceNote: (String?, String?, String) -> Unit,
    onSendImageNote: (String?, String?, String) -> Unit,
    onSendFileNote: (String?, String?, String) -> Unit,
    selectedPrivatePeer: String?,
    currentChannel: String?,
    nickname: String,
    showMediaButtons: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.background == Color.Black
    val isFocused = remember { mutableStateOf(false) }
    val hasText = value.text.isNotBlank()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var isRecording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableStateOf(0L) }
    var amplitude by remember { mutableStateOf(0) }
    var showAttachmentMenu by remember { mutableStateOf(false) }

    // WhatsApp-style colors
    val inputContainerColor = if (isDarkTheme) {
        Color(0xFF2A2A2A) // Dark gray container
    } else {
        Color(0xFFF0F0F0) // Light gray container
    }
    
    val textColor = if (isDarkTheme) {
        Color.White
    } else {
        Color(0xFF1F1F1F)
    }
    
    val hintColor = if (isDarkTheme) {
        Color(0xFF8E8E93)
    } else {
        Color(0xFF8E8E93)
    }
    
    val iconColor = if (isDarkTheme) {
        Color(0xFF8E8E93)
    } else {
        Color(0xFF606060)
    }
    
    val sendButtonColor = if (selectedPrivatePeer != null || currentChannel != null) {
        Color(0xFFFF9500) // Orange for private/channel
    } else {
        Color(0xFF25D366) // WhatsApp green
    }

    // Main container with WhatsApp-style padding
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        // Recording indicator above input (WhatsApp style)
        if (isRecording) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RealtimeScrollingWaveform(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp),
                    amplitudeNorm = normalizeAmplitudeSample(amplitude)
                )
                Spacer(Modifier.width(12.dp))
                val secs = (elapsedMs / 1000).toInt()
                val mm = secs / 60
                val ss = secs % 60
                val maxSecs = 10
                val maxMm = maxSecs / 60
                val maxSs = maxSecs % 60
                Text(
                    text = String.format("%02d:%02d / %02d:%02d", mm, ss, maxMm, maxSs),
                    fontFamily = FontFamily.Monospace,
                    color = colorScheme.primary,
                    fontSize = (BASE_FONT_SIZE - 4).sp
                )
            }
        }

        // WhatsApp-style input row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Rounded input container (WhatsApp style)
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                color = inputContainerColor
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Emoji icon (left side) - decorative
                    Icon(
                        imageVector = Icons.Outlined.EmojiEmotions,
                        contentDescription = stringResource(R.string.emoji),
                        tint = iconColor,
                        modifier = Modifier
                            .size(28.dp)
                            .padding(2.dp)
                    )

                    // Text input field
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        BasicTextField(
                            value = value,
                            onValueChange = onValueChange,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = textColor,
                                fontFamily = FontFamily.Default
                            ),
                            cursorBrush = SolidColor(if (isRecording) Color.Transparent else colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (hasText) onSend()
                            }),
                            visualTransformation = CombinedVisualTransformation(
                                listOf(SlashCommandVisualTransformation(), MentionVisualTransformation())
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onFocusChanged { focusState ->
                                    isFocused.value = focusState.isFocused
                                }
                        )

                        // Placeholder text
                        if (value.text.isEmpty() && !isRecording) {
                            Text(
                                text = stringResource(R.string.type_a_message_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = hintColor,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Attachment and camera buttons (right side, inside container)
                    if (value.text.isEmpty() && showMediaButtons && !isRecording) {
                        // Attachment button
                        Icon(
                            imageVector = Icons.Outlined.AttachFile,
                            contentDescription = stringResource(R.string.attach_file),
                            tint = iconColor,
                            modifier = Modifier
                                .size(28.dp)
                                .padding(2.dp)
                                .clickable {
                                    // Show image picker
                                    onSendImageNote(selectedPrivatePeer, currentChannel, "")
                                }
                        )
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        // Camera button
                        Icon(
                            imageVector = Icons.Outlined.CameraAlt,
                            contentDescription = stringResource(R.string.camera),
                            tint = iconColor,
                            modifier = Modifier
                                .size(28.dp)
                                .padding(2.dp)
                                .clickable {
                                    // Show image picker
                                    onSendImageNote(selectedPrivatePeer, currentChannel, "")
                                }
                        )
                    }
                }
            }

            // Send/Mic button (outside container, WhatsApp style)
            Spacer(modifier = Modifier.width(4.dp))

            if (hasText) {
                // Send button (WhatsApp green circular button)
                FloatingActionButton(
                    onClick = { onSend() },
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    containerColor = sendButtonColor,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = stringResource(R.string.send_message),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else if (showMediaButtons) {
                // Mic button (WhatsApp style)
                val bg = if (isDarkTheme) {
                    Color(0xFF25D366).copy(alpha = 0.9f)
                } else {
                    Color(0xFF25D366).copy(alpha = 0.9f)
                }

                // Ensure latest values are used when finishing recording
                val latestSelectedPeer = rememberUpdatedState(selectedPrivatePeer)
                val latestChannel = rememberUpdatedState(currentChannel)
                val latestOnSendVoiceNote = rememberUpdatedState(onSendVoiceNote)

                VoiceRecordButton(
                    backgroundColor = bg,
                    onStart = {
                        isRecording = true
                        elapsedMs = 0L
                        if (isFocused.value) {
                            try { focusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    },
                    onAmplitude = { amp, ms ->
                        amplitude = amp
                        elapsedMs = ms
                    },
                    onFinish = { path ->
                        isRecording = false
                        AudioWaveformExtractor.extractAsync(path, sampleCount = 120) { arr ->
                            if (arr != null) {
                                try { com.bitchat.android.features.voice.VoiceWaveformCache.put(path, arr) } catch (_: Exception) {}
                            }
                        }
                        latestOnSendVoiceNote.value(
                            latestSelectedPeer.value,
                            latestChannel.value,
                            path
                        )
                    }
                )
            }
        }
    }

    // Auto-stop handled inside VoiceRecordButton
}

@Composable
fun CommandSuggestionsBox(
    suggestions: List<CommandSuggestion>,
    onSuggestionClick: (CommandSuggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .background(colorScheme.surface)
            .border(1.dp, colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(vertical = 8.dp)
    ) {
        suggestions.forEach { suggestion: CommandSuggestion ->
            CommandSuggestionItem(
                suggestion = suggestion,
                onClick = { onSuggestionClick(suggestion) }
            )
        }
    }
}

@Composable
fun CommandSuggestionItem(
    suggestion: CommandSuggestion,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .background(Color.Gray.copy(alpha = 0.1f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Show all aliases together
        val allCommands = if (suggestion.aliases.isNotEmpty()) {
            listOf(suggestion.command) + suggestion.aliases
        } else {
            listOf(suggestion.command)
        }

        Text(
            text = allCommands.joinToString(", "),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            ),
            color = colorScheme.primary,
            fontSize = (BASE_FONT_SIZE - 4).sp
        )

        // Show syntax if any
        suggestion.syntax?.let { syntax ->
            Text(
                text = syntax,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = colorScheme.onSurface.copy(alpha = 0.8f),
                fontSize = (BASE_FONT_SIZE - 5).sp
            )
        }

        // Show description
        Text(
            text = suggestion.description,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace
            ),
            color = colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = (BASE_FONT_SIZE - 5).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MentionSuggestionsBox(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    
    Column(
        modifier = modifier
            .background(colorScheme.surface)
            .border(1.dp, colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(vertical = 8.dp)
    ) {
        suggestions.forEach { suggestion: String ->
            MentionSuggestionItem(
                suggestion = suggestion,
                onClick = { onSuggestionClick(suggestion) }
            )
        }
    }
}

@Composable
fun MentionSuggestionItem(
    suggestion: String,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .background(Color.Gray.copy(alpha = 0.1f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.mention_suggestion_at, suggestion),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            ),
            color = Color(0xFFFF9500), // Orange like mentions
            fontSize = (BASE_FONT_SIZE - 4).sp
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            text = stringResource(R.string.mention),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace
            ),
            color = colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = (BASE_FONT_SIZE - 5).sp
        )
    }
}
