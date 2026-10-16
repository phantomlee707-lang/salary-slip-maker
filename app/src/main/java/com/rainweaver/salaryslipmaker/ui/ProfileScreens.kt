package com.rainweaver.salaryslipmaker.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rainweaver.salaryslipmaker.data.BusinessProfileEntity
import com.rainweaver.salaryslipmaker.data.EmployeeEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalarySlipApp(viewModel: ProfileViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.message) {
        state.message?.let {
            scope.launch { snackbar.showSnackbar(it) }
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("Salary Slip Maker", fontWeight = FontWeight.SemiBold)
                    Text("Rainweaver Studio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Business, contentDescription = "Business profile") },
                    label = { Text("Business") },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Archive, contentDescription = "Employees") },
                    label = { Text("Employees") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (selectedTab) {
            0 -> BusinessProfileScreen(viewModel, state.business, Modifier.padding(padding))
            else -> EmployeeDirectoryScreen(viewModel, state.employees, Modifier.padding(padding))
        }
    }
}

@Composable
private fun BusinessProfileScreen(
    viewModel: ProfileViewModel,
    current: BusinessProfileEntity,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var profile by remember(current) { mutableStateOf(current) }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            profile = profile.copy(logoUri = uri.toString())
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Business profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("These details are used on new payslips. Previously issued documents are intended to retain their original details.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedTextField(
            value = profile.businessName,
            onValueChange = { profile = profile.copy(businessName = it) },
            label = { Text("Business name *") },
            singleLine = true,
            isError = profile.businessName.isBlank(),
            supportingText = { if (profile.businessName.isBlank()) Text("Business name is required") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(profile.address, { profile = profile.copy(address = it) }, label = { Text("Address (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(profile.phone, { profile = profile.copy(phone = it) }, label = { Text("Phone") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.weight(1f))
            OutlinedTextField(profile.email, { profile = profile.copy(email = it) }, label = { Text("Email") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.weight(1f))
        }
        OutlinedTextField(profile.registrationOrTaxId, { profile = profile.copy(registrationOrTaxId = it) }, label = { Text("Registration / tax ID (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(profile.accentColorHex, { profile = profile.copy(accentColorHex = it) }, label = { Text("Accent color (hex, optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(profile.additionalInfo, { profile = profile.copy(additionalInfo = it) }, label = { Text("Additional business information") }, minLines = 2, modifier = Modifier.fillMaxWidth())

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Logo", fontWeight = FontWeight.Medium)
                Text(if (profile.logoUri.isNullOrBlank()) "Optional — a logo is not required." else "Logo selected. The file remains on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { logoPicker.launch(arrayOf("image/*")) }) { Text("Choose logo") }
                    if (!profile.logoUri.isNullOrBlank()) OutlinedButton(onClick = { profile = profile.copy(logoUri = null) }) { Text("Remove") }
                }
            }
        }
        Button(onClick = { viewModel.saveBusiness(profile) }, enabled = profile.businessName.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Save business profile")
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun EmployeeDirectoryScreen(
    viewModel: ProfileViewModel,
    employees: List<EmployeeEntity>,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EmployeeEntity?>(null) }
    var dialogOpen by remember { mutableStateOf(false) }

    val visible = remember(employees, query, showArchived) {
        employees.filter { employee ->
            employee.archived == showArchived &&
                (query.isBlank() || listOf(employee.fullName, employee.employeeIdentifier, employee.jobTitle, employee.department)
                    .any { it.contains(query.trim(), ignoreCase = true) })
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Employees", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("${employees.count { !it.archived }} active · ${employees.count { it.archived }} archived", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { editing = null; dialogOpen = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Add")
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(query, { query = it }, label = { Text("Search name, ID, role, or department") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { showArchived = false }) { Text(if (!showArchived) "• Active" else "Active") }
            TextButton(onClick = { showArchived = true }) { Text(if (showArchived) "• Archived" else "Archived") }
        }
        if (visible.isEmpty()) {
            Text(if (showArchived) "No archived employees." else "No employees yet. Add a profile to get started.", Modifier.padding(top = 18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(visible, key = { it.id }) { employee ->
                    Card(Modifier.fillMaxWidth()) {
                        Column {
                            ListItem(
                                headlineContent = { Text(employee.fullName, fontWeight = FontWeight.Medium) },
                                supportingContent = {
                                    val details = listOf(employee.employeeIdentifier, employee.jobTitle, employee.department)
                                        .filter(String::isNotBlank).joinToString(" · ")
                                    Text(details.ifBlank { "No optional details added" })
                                },
                            )
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { editing = employee; dialogOpen = true }) { Text("Edit") }
                                TextButton(onClick = { viewModel.setArchived(employee.id, !employee.archived) }) {
                                    Icon(if (employee.archived) Icons.Default.Restore else Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Text(if (employee.archived) "Restore" else "Archive")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (dialogOpen) {
        EmployeeEditorDialog(
            initial = editing,
            onDismiss = { dialogOpen = false },
            onSave = { employee -> viewModel.saveEmployee(employee); dialogOpen = false },
        )
    }
}

@Composable
private fun EmployeeEditorDialog(
    initial: EmployeeEntity?,
    onDismiss: () -> Unit,
    onSave: (EmployeeEntity) -> Unit,
) {
    var fullName by remember(initial) { mutableStateOf(initial?.fullName.orEmpty()) }
    var employeeIdentifier by remember(initial) { mutableStateOf(initial?.employeeIdentifier.orEmpty()) }
    var jobTitle by remember(initial) { mutableStateOf(initial?.jobTitle.orEmpty()) }
    var department by remember(initial) { mutableStateOf(initial?.department.orEmpty()) }
    var contactInfo by remember(initial) { mutableStateOf(initial?.contactInfo.orEmpty()) }
    var notes by remember(initial) { mutableStateOf(initial?.notes.orEmpty()) }
    val valid = fullName.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add employee" else "Edit employee") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(fullName, { fullName = it }, label = { Text("Full name *") }, singleLine = true, isError = !valid, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(employeeIdentifier, { employeeIdentifier = it }, label = { Text("Employee / payroll ID (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(jobTitle, { jobTitle = it }, label = { Text("Job title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(department, { department = it }, label = { Text("Department") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(contactInfo, { contactInfo = it }, label = { Text("Contact information") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Text("Bank details and sensitive identifiers are not required.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = {
                val employee = (initial ?: EmployeeEntity(fullName = fullName)).copy(
                    fullName = fullName,
                    employeeIdentifier = employeeIdentifier,
                    jobTitle = jobTitle,
                    department = department,
                    contactInfo = contactInfo,
                    notes = notes,
                )
                onSave(employee)
            }, enabled = valid) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
