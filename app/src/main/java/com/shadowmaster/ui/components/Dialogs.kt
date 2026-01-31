package com.shadowmaster.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Reusable dialog components for the ShadowMaster app.
 * These components extract common dialog patterns for consistency and maintainability.
 */

/**
 * A confirmation dialog with Yes/No or customizable actions.
 *
 * @param title The dialog title
 * @param message The confirmation message to display
 * @param confirmText Text for the confirm button (default: "Confirm")
 * @param dismissText Text for the dismiss button (default: "Cancel")
 * @param confirmColor Color for the confirm button text (default: primary)
 * @param onConfirm Callback when user confirms
 * @param onDismiss Callback when user dismisses
 */
@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmText: String = "Confirm",
    dismissText: String = "Cancel",
    confirmColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = confirmColor)
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
 * A dialog with a single text input field and validation support.
 *
 * @param title The dialog title
 * @param label Label for the text field
 * @param initialValue Initial value for the text field
 * @param placeholder Placeholder text for the text field
 * @param confirmText Text for the confirm button (default: "Save")
 * @param dismissText Text for the dismiss button (default: "Cancel")
 * @param validator Optional validation function that returns an error message or null if valid
 * @param onConfirm Callback with the entered text when user confirms
 * @param onDismiss Callback when user dismisses
 */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initialValue: String = "",
    placeholder: String = "",
    confirmText: String = "Save",
    dismissText: String = "Cancel",
    validator: ((String) -> String?)? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Validate on each text change
    LaunchedEffect(text) {
        errorMessage = validator?.invoke(text)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth()
                )
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank() && errorMessage == null
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
 * A dialog for selecting from a list of options.
 *
 * @param title The dialog title
 * @param items List of items to select from
 * @param selectedItem Currently selected item (null if none)
 * @param itemLabel Function to get display label for each item
 * @param itemSubtitle Optional function to get subtitle for each item
 * @param confirmText Text for the confirm button (default: "Select")
 * @param dismissText Text for the dismiss button (default: "Cancel")
 * @param onConfirm Callback with the selected item when user confirms
 * @param onDismiss Callback when user dismisses
 */
@Composable
fun <T> SelectionDialog(
    title: String,
    items: List<T>,
    selectedItem: T?,
    itemLabel: (T) -> String,
    itemSubtitle: ((T) -> String)? = null,
    confirmText: String = "Select",
    dismissText: String = "Cancel",
    onConfirm: (T) -> Unit,
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { currentSelection = item }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = item == currentSelection,
                            onClick = { currentSelection = item }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = itemLabel(item),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            itemSubtitle?.let { getSubtitle ->
                                Text(
                                    text = getSubtitle(item),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { currentSelection?.let { onConfirm(it) } },
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
 * A dialog showing progress with an optional cancel action.
 *
 * @param title The dialog title
 * @param message Current status message
 * @param progress Progress value from 0f to 1f, or null for indeterminate
 * @param showPercentage Whether to show percentage text (only when progress is not null)
 * @param cancelText Text for cancel button, or null to hide cancel button
 * @param onCancel Callback when user cancels, or null if not cancellable
 * @param onDismiss Callback when dialog is dismissed (only called when complete or failed)
 * @param isComplete Whether the operation is complete
 * @param isError Whether the operation failed
 * @param completeText Text for the dismiss button when complete (default: "OK")
 */
@Composable
fun ProgressDialog(
    title: String,
    message: String,
    progress: Float? = null,
    showPercentage: Boolean = true,
    cancelText: String? = "Cancel",
    onCancel: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    isComplete: Boolean = false,
    isError: Boolean = false,
    completeText: String = "OK"
) {
    AlertDialog(
        onDismissRequest = {
            if (isComplete || isError) {
                onDismiss()
            }
            // Don't allow dismissing while in progress
        },
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                if (!isComplete && !isError) {
                    Spacer(modifier = Modifier.height(16.dp))
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (showPercentage) {
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .padding(top = 4.dp)
                            )
                        }
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            if (isComplete || isError) {
                TextButton(onClick = onDismiss) {
                    Text(completeText)
                }
            }
        },
        dismissButton = {
            if (!isComplete && !isError && cancelText != null && onCancel != null) {
                TextButton(onClick = onCancel) {
                    Text(cancelText)
                }
            }
        }
    )
}
