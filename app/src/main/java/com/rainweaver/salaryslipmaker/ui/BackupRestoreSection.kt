package com.rainweaver.salaryslipmaker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rainweaver.salaryslipmaker.data.LocalBackupService
import com.rainweaver.salaryslipmaker.domain.LocalBackupPayload
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun BackupRestoreSection(onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<LocalBackupPayload?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                runCatching { LocalBackupService.export(context, uri) }
                    .onSuccess { onMessage("Local backup created") }
                    .onFailure { error = it.message ?: "Backup export failed" }
                busy = false
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                runCatching { LocalBackupService.readAndValidate(context, uri) }
                    .onSuccess { pendingRestore = it; error = null }
                    .onFailure { error = it.message ?: "Could not validate backup" }
                busy = false
            }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Local backup and restore", style = MaterialTheme.typography.titleMedium)
            Text("Backups are plain JSON and include employee, business, template, draft, and issued-slip data. Keep the file private. Restore replaces current local data; it does not merge or upload anything. A logo URI may need to be selected again after restoring on another device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = { exportLauncher.launch("salary-slip-maker-${LocalDate.now()}.rwbackup.json") }) {
                    if (busy) CircularProgressIndicator() else Text("Create backup")
                }
                OutlinedButton(enabled = !busy, onClick = { restoreLauncher.launch(arrayOf("application/json", "text/plain")) }) { Text("Choose backup") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    pendingRestore?.let { payload ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingRestore = null },
            title = { Text("Replace all local records?") },
            text = { Text("This validated backup will replace this device's business profile, ${payload.employees.size} employee(s), ${payload.templates.size} template(s), and ${payload.payslips.size} payslip(s). Existing records will be removed. This cannot be undone unless you have another backup.") },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching { LocalBackupService.restore(context, payload) }
                                .onSuccess { onMessage("Backup restored"); pendingRestore = null }
                                .onFailure { error = it.message ?: "Restore failed" }
                            busy = false
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { if (busy) CircularProgressIndicator() else Text("Replace records") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }
}
