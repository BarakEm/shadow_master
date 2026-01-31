package com.shadowmaster.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A reusable confirmation dialog for Yes/No confirmations.
 *
 * @param title The title of the dialog
 * @param message The confirmation message to display
 * @param confirmText The text for the confirm button (default: "Confirm")
 * @param dismissText The text for the dismiss button (default: "Cancel")
 * @param isDestructive Whether the action is destructive (shows confirm button in error color)
 * @param onConfirm Callback when the confirm button is clicked
 * @param onDismiss Callback when the dialog is dismissed
 */
@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmText: String = "Confirm",
    dismissText: String = "Cancel",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmText,
                    color = if (isDestructive) MaterialTheme.colorScheme.error else LocalContentColor.current
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        }
    )
}

/**
 * A reusable text input dialog with validation.
 *
 * @param title The title of the dialog
 * @param label The label for the text field
 * @param initialValue The initial value for the text field
 * @param placeholder Optional placeholder text
 * @param confirmText The text for the confirm button (default: "OK")
 * @param dismissText The text for the dismiss button (default: "Cancel")
 * @param singleLine Whether the text field is single-line (default: true)
 * @param validator Optional validation function that returns true if input is valid
 * @param onConfirm Callback when the confirm button is clicked with the entered text (automatically trimmed)
 * @param onDismiss Callback when the dialog is dismissed
 */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initialValue: String = "",
    placeholder: String? = null,
    confirmText: String = "OK",
    dismissText: String = "Cancel",
    singleLine: Boolean = true,
    validator: (String) -> Boolean = { it.isNotBlank() },
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    val isValid = validator(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                placeholder = if (placeholder != null) {
                    { Text(placeholder) }
                } else null,
                singleLine = singleLine,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = isValid
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        }
    )
}

/**
 * A reusable selection dialog for choosing from a list of items.
 *
 * @param T The type of items in the selection list
 * @param title The title of the dialog
 * @param items The list of items to choose from
 * @param selectedItem The currently selected item (if any)
 * @param itemLabel Function to get the display label for an item
 * @param itemDescription Optional function to get a description for an item
 * @param confirmText The text for the confirm button (default: "OK")
 * @param dismissText The text for the dismiss button (default: "Cancel")
 * @param onItemSelected Callback when an item is selected
 * @param onConfirm Callback when the confirm button is clicked with the selected item
 * @param onDismiss Callback when the dialog is dismissed
 */
@Composable
fun <T> SelectionDialog(
    title: String,
    items: List<T>,
    selectedItem: T? = null,
    itemLabel: (T) -> String,
    itemDescription: ((T) -> String)? = null,
    confirmText: String = "OK",
    dismissText: String = "Cancel",
    onItemSelected: (T) -> Unit = {},
    onConfirm: (T?) -> Unit,
    onDismiss: () -> Unit
) {
    var currentSelection by remember { mutableStateOf(selectedItem) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(items) { item ->
                    val isSelected = item == currentSelection
                    ListItem(
                        headlineContent = {
                            Text(
                                text = itemLabel(item),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = itemDescription?.let { desc ->
                            {
                                Text(
                                    text = desc(item),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        leadingContent = {
                            RadioButton(
                                selected = isSelected,
                                onClick = null
                            )
                        },
                        modifier = Modifier.clickable {
                            currentSelection = item
                            onItemSelected(item)
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(currentSelection) },
                enabled = currentSelection != null
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        }
    )
}

/**
 * A reusable progress dialog for showing operation progress.
 *
 * @param title The title of the dialog
 * @param message The message describing the current operation
 * @param progress Optional progress value (0.0 to 1.0) for determinate progress
 * @param showProgress Whether to show a progress indicator
 * @param additionalContent Optional composable for additional content (e.g., result info)
 * @param dismissible Whether the dialog can be dismissed
 * @param confirmText Optional text for a confirm button (shown when dismissible)
 * @param cancelText Optional text for a cancel button
 * @param onConfirm Optional callback when confirm button is clicked
 * @param onCancel Optional callback when cancel button is clicked
 * @param onDismiss Callback when the dialog is dismissed
 */
@Composable
fun ProgressDialog(
    title: String,
    message: String,
    progress: Float? = null,
    showProgress: Boolean = true,
    additionalContent: (@Composable () -> Unit)? = null,
    dismissible: Boolean = false,
    confirmText: String? = null,
    cancelText: String? = null,
    onConfirm: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onDismiss: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = if (dismissible) onDismiss else {},
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (showProgress) {
                    Spacer(modifier = Modifier.height(16.dp))
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                additionalContent?.invoke()
            }
        },
        confirmButton = {
            if (confirmText != null && onConfirm != null) {
                TextButton(onClick = onConfirm) {
                    Text(confirmText)
                }
            }
        },
        dismissButton = {
            if (cancelText != null && onCancel != null) {
                TextButton(onClick = onCancel) {
                    Text(cancelText)
                }
            }
        }
    )
}
