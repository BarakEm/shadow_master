# Reusable Dialog Components

This package contains reusable dialog components extracted from various screens in the app. These components follow Material 3 design guidelines and provide consistent dialog patterns across the application.

## Components

### ConfirmationDialog

A simple Yes/No confirmation dialog for destructive or important actions.

**Use cases:**
- Delete confirmations
- Discard changes warnings
- Action confirmations

**Example:**
```kotlin
ConfirmationDialog(
    title = "Delete Playlist",
    message = "Delete \"My Playlist\" and all its items?",
    confirmText = "Delete",
    dismissText = "Cancel",
    isDestructive = true,
    onConfirm = { /* handle confirm */ },
    onDismiss = { /* handle dismiss */ }
)
```

### TextInputDialog

A dialog with a single text input field and validation support.

**Use cases:**
- Rename operations
- URL/link input
- Single-field forms

**Example:**
```kotlin
TextInputDialog(
    title = "Rename Playlist",
    label = "Name",
    initialValue = "Current Name",
    placeholder = "Enter new name",
    confirmText = "Rename",
    dismissText = "Cancel",
    singleLine = true,
    validator = { it.isNotBlank() },
    onConfirm = { newName -> /* handle confirm */ },
    onDismiss = { /* handle dismiss */ }
)
```

### SelectionDialog

A dialog for selecting an item from a list with radio buttons.

**Use cases:**
- Preset selection
- Option selection
- Single-choice lists

**Example:**
```kotlin
SelectionDialog(
    title = "Choose Preset",
    items = presetList,
    selectedItem = currentPreset,
    itemLabel = { it.name },
    itemDescription = { it.description },
    confirmText = "Select",
    dismissText = "Cancel",
    onItemSelected = { /* handle selection change */ },
    onConfirm = { selected -> /* handle confirm */ },
    onDismiss = { /* handle dismiss */ }
)
```

### ProgressDialog

A dialog for showing operation progress with optional additional content.

**Use cases:**
- File exports/imports
- Long-running operations
- Background tasks

**Example:**
```kotlin
ProgressDialog(
    title = "Exporting Audio",
    message = "Exporting segment 5/10",
    progress = 0.5f, // 0.0 to 1.0, or null for indeterminate
    showProgress = true,
    additionalContent = {
        Text("Additional info here")
    },
    dismissible = false,
    confirmText = "OK",
    cancelText = "Cancel",
    onConfirm = { /* handle confirm */ },
    onCancel = { /* handle cancel */ },
    onDismiss = { /* handle dismiss */ }
)
```

## Design Principles

1. **Consistency**: All dialogs follow Material 3 design guidelines
2. **Flexibility**: Support for custom content and behavior
3. **Validation**: Built-in validation support for text inputs
4. **Accessibility**: Proper labeling and keyboard navigation
5. **Simplicity**: Minimal API surface with sensible defaults

## Migration Guide

### Before (Custom Dialog):
```kotlin
AlertDialog(
    onDismissRequest = { showDialog = false },
    title = { Text("Delete Item") },
    text = { Text("Are you sure?") },
    confirmButton = {
        TextButton(onClick = { 
            deleteItem()
            showDialog = false 
        }) {
            Text("Delete", color = MaterialTheme.colorScheme.error)
        }
    },
    dismissButton = {
        TextButton(onClick = { showDialog = false }) {
            Text("Cancel")
        }
    }
)
```

### After (ConfirmationDialog):
```kotlin
ConfirmationDialog(
    title = "Delete Item",
    message = "Are you sure?",
    confirmText = "Delete",
    isDestructive = true,
    onConfirm = {
        deleteItem()
        showDialog = false
    },
    onDismiss = { showDialog = false }
)
```

## Benefits

- **Reduced Code Duplication**: Common dialog patterns in one place
- **Easier Maintenance**: Changes to dialog behavior update all usages
- **Consistent UX**: Same look and feel across the app
- **Type Safety**: Generic support for selection dialogs
- **Testability**: Isolated components are easier to test

## Future Enhancements

Potential additions to this component library:
- Multi-selection dialog
- Date/time picker dialogs
- Color picker dialog
- Custom button layouts
- Dialog theming support
