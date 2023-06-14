package com.adafruit.glider.ui.components

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.adafruit.glider.ui.theme.GliderTheme

@Composable
fun InputTextActionDialog(
    alertText: String,
    alertMessage: String,
    placeholderText: String,
    currentText: String = "",
    actionText: String,
    onAction: (String?) -> Unit,            // returns the text or null if cancel was pressed
) {
    var inputText by remember { mutableStateOf(currentText) }

    AlertDialog(
        onDismissRequest = { onAction(null) },
        title = { Text(alertText) },
        containerColor = Color.White,
        titleContentColor = Color.Black,
        textContentColor = Color.Black,
        text = {
            Column(verticalArrangement = Arrangement.Absolute.spacedBy(16.dp)) {
                Text(alertMessage)
                TextField(
                    colors = TextFieldDefaults.colors(
                        unfocusedTextColor = Color.Black,
                        focusedTextColor = Color.Black,
                        unfocusedPlaceholderColor = Color.Gray,
                        focusedPlaceholderColor = Color.Gray,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Color.Black,
                    ),
                    placeholder = { Text(placeholderText) },
                    singleLine = true,
                    value = inputText,
                    onValueChange = { inputText = it },
                )
            }
        },
        confirmButton = {
            OutlinedButton(enabled = inputText.isNotBlank(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.Black,
                    disabledContentColor = Color.Gray,
                ),
                border = BorderStroke(1.dp, Color.Black),
                onClick = {
                    onAction(inputText)
                }) {
                Text(actionText)
            }
        },
        dismissButton = {
            OutlinedButton(
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.Black
                ),
                border = BorderStroke(1.dp, Color.Black),
                onClick = {
                    onAction(null)
                }) {
                Text("Cancel")
            }
        })
}

@Preview(showSystemUi = true)
@Composable
private fun InputTextActionDialogPreview() {
    GliderTheme {
        InputTextActionDialog(
            alertText = "New Directory",
            alertMessage = "Enter name for the new directory",
            placeholderText = "Directory name",
            actionText = "Create",
        ) {}
    }
}

//endregion