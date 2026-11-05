package com.kongye.reader.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CatSpeechBubble(text: String, modifier: Modifier = Modifier) {
    ToastBubble(text, modifier.padding(horizontal = 32.dp))
}
