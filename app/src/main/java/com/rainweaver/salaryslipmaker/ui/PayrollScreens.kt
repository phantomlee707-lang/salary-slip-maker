package com.rainweaver.salaryslipmaker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rainweaver.salaryslipmaker.data.EmployeeEntity
import com.rainweaver.salaryslipmaker.data.PayslipBundle
import com.rainweaver.salaryslipmaker.data.PayslipEntity
import com.rainweaver.salaryslipmaker.data.SalaryTemplateEntity
import com.rainweaver.salaryslipmaker.domain.PayslipDraftSnapshot
import com.rainweaver.salaryslipmaker.domain.PayslipIssueInput
import com.rainweaver.salaryslipmaker.domain.PayslipPolicy
import com.rainweaver.salaryslipmaker.domain.PayslipStatus
import com.rainweaver.salaryslipmaker.domain.SalaryLine
import com.rainweaver.salaryslipmaker.domain.SalaryLineKind
import com.rainweaver.salaryslipmaker.domain.SalaryMath
import com.rainweaver.salaryslipmaker.domain.SalaryMoneyFormatter
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch

private data class EditorLine(val key: String = UUID.randomUUID().toString(), val kind: SalaryLineKind, val label: String = "", val amount: String = "")
private data class ParsedLines(val lines: List<SalaryLine>, val error: String?)

@Composable
fun PayrollHomeScreen(payslips: List<PayslipEntity>, employees: List<EmployeeEntity>, modifier: Modifier = Modifier, onNavigate: (Int) -> Unit) {
    val active = employees.count { !it.archived }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Payroll overview", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("Professional payslips stored on this device. No account, cloud sync, or network access.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryTile("Employees", active.toString(), Modifier.weight(1f))
            SummaryTile("Drafts", payslips.count { it.status == PayslipStatus.DRAFT }.toString(), Modifier.weight(1f))
            SummaryTile("Issued", payslips.count { it.status == PayslipStatus.ISSUED }.toString(), Modifier.weight(1f))
        }
        Button(onClick = { onNavigate(2) }, enabled = active > 0, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("  Create payslip") }
        if (active == 0) OutlinedButton(onClick = { onNavigate(1) }, modifier = Modifier.fillMaxWidth()) { Text("Add an employee first") }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Recent payslips", fontWeight = FontWeight.SemiBold)
                if (payslips.isEmpty()) Text("Your payslip history will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                payslips.take(5).forEach { slip ->
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(slip.employeeName, fontWeight = FontWeight.Medium)
                            Text("${slip.number} · ${slip.status.name.lowercase().replaceFirstChar(Char::uppercase)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(money(slip.netPay, slip.currencyCode), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Text("Statutory tax, attendance, and payroll deductions are not calculated automatically; enter applicable amounts manually.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SummaryTile(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) { Column(Modifier.padding(12.dp)) { Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(title, style = MaterialTheme.typography.labelMedium) } }
}

@Composable
fun PayslipDirectoryScreen(viewModel: PayrollViewModel, employees: List<EmployeeEntity>, payslips: List<PayslipEntity>, modifier: Modifier = Modifier, onExport: (PayslipBundle) -> Unit) {
    var query by remember { mutableStateOf("") }
    var periodFilter by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var newDialog by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var deleting by remember { mutableStateOf<PayslipEntity?>(null) }
    val scope = rememberCoroutineScope()
    val selectedMonth = remember(periodFilter) {
        if (periodFilter.isBlank()) null else runCatching { YearMonth.parse(periodFilter.trim()) }.getOrNull()
    }
    val visible = remember(payslips, query, filter, periodFilter, selectedMonth) {
        payslips.filter { s -> (filter == "All" || s.status.name.equals(filter, true)) &&
            (query.isBlank() || listOf(s.number, s.employeeName, s.employeeIdentifier).any { it.contains(query.trim(), true) }) &&
            (periodFilter.isBlank() || selectedMonth?.let { PayslipPolicy.periodMatches(s.periodStartDay, s.periodEndDay, it) } == true) }
    }
    Column(modifier.fillMaxSize().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Payslips", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("${payslips.count { it.status == PayslipStatus.ISSUED }} issued · ${payslips.count { it.status == PayslipStatus.DRAFT }} drafts", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { newDialog = true }, enabled = employees.any { !it.archived }) { Icon(Icons.Default.Add, null); Text("New") }
        }
        OutlinedTextField(query, { query = it }, label = { Text("Search employee or payslip number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(periodFilter, { periodFilter = it }, label = { Text("Filter period (YYYY-MM)") }, isError = periodFilter.isNotBlank() && selectedMonth == null, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (periodFilter.isNotBlank() && selectedMonth == null) Text("Enter a valid month, for example 2026-09", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("All", "Draft", "Issued").forEach { FilterChip(filter == it, { filter = it }, label = { Text(it) }) } }
        if (visible.isEmpty()) Text(if (payslips.isEmpty()) "No payslips yet. Create one from an active employee." else "No matches.", Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(visible, key = { it.id }) { slip ->
                val replacement = payslips.firstOrNull { it.supersedesPayslipId == slip.id && it.status == PayslipStatus.ISSUED }
                val source = slip.supersedesPayslipId?.let { id -> payslips.firstOrNull { it.id == id } }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(slip.employeeName, fontWeight = FontWeight.SemiBold); Text("${slip.number} · ${slip.status.name.lowercase().replaceFirstChar(Char::uppercase)}", style = MaterialTheme.typography.bodySmall) }
                            Text(money(slip.netPay, slip.currencyCode), fontWeight = FontWeight.Bold)
                        }
                        Text(period(slip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (replacement != null) Text("Superseded by ${replacement.number}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        if (source != null) Text("Correction of ${source.number}", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            if (slip.status == PayslipStatus.DRAFT) TextButton(onClick = { deleting = slip }) { Icon(Icons.Default.Delete, null); Text("Delete") }
                            TextButton(onClick = { editingId = slip.id }) { Text(if (slip.status == PayslipStatus.DRAFT) "Continue" else "View") }
                        }
                    }
                }
            }
        }
    }
    if (newDialog) NewSlipDialog(employees, { newDialog = false }) { employeeId ->
        editingId = viewModel.createDraft(employeeId)
        newDialog = false
    }
    editingId?.let { PayslipEditorDialog(it, viewModel, payslips, onExport) { editingId = null } }
    deleting?.let { slip -> AlertDialog(
        onDismissRequest = { deleting = null }, title = { Text("Delete draft?") },
        text = { Text("${slip.number} will be permanently removed. Issued records cannot be deleted.") },
        confirmButton = { Button(onClick = { scope.launch { runCatching { viewModel.deleteDraft(slip.id) }.onSuccess { viewModel.message(if (it) "Draft deleted" else "Only drafts can be deleted") }.onFailure { viewModel.message(it.message ?: "Delete failed") } }; deleting = null }) { Text("Delete draft") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
    ) }
}

@Composable
private fun NewSlipDialog(employees: List<EmployeeEntity>, onDismiss: () -> Unit, onCreate: suspend (Long) -> Unit) {
    val active = employees.filter { !it.archived }
    var selected by remember(active) { mutableStateOf(active.firstOrNull()?.id) }
    var menu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("New payslip") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (active.isEmpty()) Text("Add an active employee first.") else Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text(active.firstOrNull { it.id == selected }?.fullName ?: "Select employee") }
                    DropdownMenu(menu, { menu = false }) { active.forEach { e -> DropdownMenuItem(text = { Text(e.fullName) }, onClick = { selected = e.id; menu = false }) } }
                }
                Text("A draft captures current business and employee details. Add salary lines before issuing.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(enabled = selected != null && !busy, onClick = { val id = selected ?: return@Button; busy = true; scope.launch { runCatching { onCreate(id) }.onFailure { error = it.message ?: "Could not create draft" }; busy = false } }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Create draft") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PayslipEditorDialog(id: Long, viewModel: PayrollViewModel, payslips: List<PayslipEntity>, onExport: (PayslipBundle) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bundle by remember(id) { mutableStateOf<PayslipBundle?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var message by remember(id) { mutableStateOf<String?>(null) }
    var loading by remember(id) { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var confirmIssue by remember { mutableStateOf(false) }
    var start by remember(id) { mutableStateOf("") }
    var end by remember(id) { mutableStateOf("") }
    var payment by remember(id) { mutableStateOf("") }
    var currency by remember(id) { mutableStateOf("IDR") }
    var method by remember(id) { mutableStateOf("") }
    var reference by remember(id) { mutableStateOf("") }
    var notes by remember(id) { mutableStateOf("") }
    val lines = remember(id) { mutableStateListOf<EditorLine>() }
    LaunchedEffect(id) {
        runCatching { viewModel.loadPayslip(id) }.onSuccess { loaded ->
            if (loaded == null) error = "Payslip not found" else {
                bundle = loaded; val p = loaded.payslip
                start = date(p.periodStartDay); end = date(p.periodEndDay); payment = date(p.paymentDay)
                currency = p.currencyCode; method = p.paymentMethod; reference = p.paymentReference; notes = p.notes
                lines.clear(); lines.addAll(loaded.lines.map { EditorLine(kind = it.kind, label = it.label, amount = it.amount.toPlainString()) })
                if (loaded.invalidLineCount > 0) message = "${loaded.invalidLineCount} invalid stored line(s) omitted; review and replace them."
            }
        }.onFailure { error = it.message ?: "Could not load payslip" }
        loading = false
    }
    val current = bundle?.payslip
    val draft = current?.status == PayslipStatus.DRAFT
    val parsed = parseEditorLines(lines)
    val totals = if (parsed.error == null) runCatching { SalaryMath.calculate(parsed.lines) }.getOrNull() else null
    val startDay = parseDay(start); val endDay = parseDay(end); val paymentDay = parseDay(payment)
    val dateInputsValid = (start.isBlank() || startDay != null) && (end.isBlank() || endDay != null) && (payment.isBlank() || paymentDay != null)
    val edited = current?.copy(
        periodStartDay = startDay, periodEndDay = endDay, paymentDay = paymentDay,
        currencyCode = currency.trim().uppercase(Locale.ROOT).ifBlank { "IDR" },
        paymentMethod = method.trim(), paymentReference = reference.trim(), notes = notes.trim(),
    )
    val savedSnapshot = current?.let { p -> PayslipDraftSnapshot(p.periodStartDay, p.periodEndDay, p.paymentDay, p.currencyCode, p.paymentMethod, p.paymentReference, p.notes, bundle?.lines.orEmpty()) }
    val editedSnapshot = edited?.let { p -> PayslipDraftSnapshot(p.periodStartDay, p.periodEndDay, p.paymentDay, p.currencyCode, p.paymentMethod, p.paymentReference, p.notes, parsed.lines) }
    val unsavedIssueChanges = draft && (savedSnapshot == null || editedSnapshot == null || !PayslipPolicy.isSnapshotCurrent(savedSnapshot, editedSnapshot) || (bundle?.invalidLineCount ?: 0) > 0)
    val issueErrors = if (edited != null && parsed.error == null) PayslipPolicy.issueErrors(
        PayslipIssueInput(
            edited.businessName, edited.employeeName, edited.periodStartDay, edited.periodEndDay, edited.paymentDay, edited.currencyCode, parsed.lines,
            savedGrossEarnings = current?.grossEarnings, savedTotalDeductions = current?.totalDeductions, savedNetPay = current?.netPay,
        ),
    ) else listOf("Complete each salary line and date")
    val issueMessages = (issueErrors + if (unsavedIssueChanges) listOf(PayslipPolicy.ERROR_SAVED_CHANGES) else emptyList()).distinct()
    val replacement = current?.let { p -> payslips.firstOrNull { it.supersedesPayslipId == p.id && it.status == PayslipStatus.ISSUED } }
    val source = current?.supersedesPayslipId?.let { sourceId -> payslips.firstOrNull { it.id == sourceId } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Column { Text(if (current?.status == PayslipStatus.ISSUED) "Issued payslip" else "Payslip draft"); Text(current?.number.orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) } },
        text = {
            if (current == null) Column(Modifier.heightIn(max = 500.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (loading) CircularProgressIndicator() else Text(error ?: "Payslip unavailable", color = MaterialTheme.colorScheme.error)
            } else Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(current.businessName.ifBlank { "Business name not set" }, fontWeight = FontWeight.SemiBold)
                listOf(current.businessAddress, current.businessPhone, current.businessEmail, current.businessRegistrationId).filter(String::isNotBlank).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (draft) {
                    Text("Drafts keep the company details captured when they were created. Apply the latest saved profile to this draft here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                runCatching { viewModel.applyCurrentBusinessProfileToDraft(id) }
                                    .onSuccess { bundle = viewModel.loadPayslip(id); message = "Current company profile applied to this draft" }
                                    .onFailure { message = it.message ?: "Could not apply company profile" }
                                busy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Apply current company profile") }
                }
                HorizontalDivider()
                Text("Employee: ${current.employeeName}", fontWeight = FontWeight.Medium)
                listOf(current.employeeIdentifier, current.employeeJobTitle, current.employeeDepartment).filter(String::isNotBlank).joinToString(" · ").takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (source != null) Text("Correction of ${source.number}", color = MaterialTheme.colorScheme.secondary)
                if (replacement != null) Text("Superseded by ${replacement.number}", color = MaterialTheme.colorScheme.error)
                if (draft) {
                    OutlinedTextField(start, { start = it }, label = { Text("Period start YYYY-MM-DD") }, isError = start.isNotBlank() && startDay == null, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(end, { end = it }, label = { Text("Period end YYYY-MM-DD") }, isError = end.isNotBlank() && endDay == null, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(payment, { payment = it }, label = { Text("Payment date YYYY-MM-DD") }, isError = payment.isNotBlank() && paymentDay == null, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(currency, { currency = it }, label = { Text("Currency code or symbol") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(method, { method = it }, label = { Text("Payment method (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(reference, { reference = it }, label = { Text("Payment reference (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    SalaryLineEditor(lines.toList(), { lines.clear(); lines.addAll(it) }, true)
                } else {
                    Text("Period: ${period(current)} · Payment: ${date(current.paymentDay).ifBlank { "—" }}")
                    Text("Currency: ${current.currencyCode}")
                    if (current.paymentMethod.isNotBlank()) Text("Payment method: ${current.paymentMethod}")
                    if (current.paymentReference.isNotBlank()) Text("Reference: ${current.paymentReference}")
                    if (current.notes.isNotBlank()) Text("Notes: ${current.notes}")
                    SalaryLineEditor(lines.toList(), {}, false)
                }
                if ((bundle?.invalidLineCount ?: 0) > 0) Text("Stored lines contain invalid data; totals below exclude them.", color = MaterialTheme.colorScheme.error)
                HorizontalDivider()
                Text("Gross earnings   ${totals?.let { SalaryMoneyFormatter.format(it.grossEarnings, currency) } ?: money(current.grossEarnings, current.currencyCode)}")
                Text("Deductions   ${totals?.let { SalaryMoneyFormatter.format(it.totalDeductions, currency) } ?: money(current.totalDeductions, current.currencyCode)}")
                Text("Net pay   ${totals?.let { SalaryMoneyFormatter.format(it.netPay, currency) } ?: money(current.netPay, current.currencyCode)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (totals?.negativeNet == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                if (draft && issueMessages.isNotEmpty()) Text(issueMessages.joinToString(" · "), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Text("Statutory calculations are not included; enter applicable amounts manually.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                message?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
            }
        },
        confirmButton = {
            when {
                draft && edited != null -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                    Button(enabled = !busy && parsed.error == null && dateInputsValid, onClick = {
                        busy = true; scope.launch {
                            runCatching { viewModel.saveDraft(edited, parsed.lines) }
                                .onSuccess { message = "Draft saved"; bundle = viewModel.loadPayslip(id) }
                                .onFailure { message = it.message ?: "Could not save draft" }
                            busy = false
                        }
                    }) { if (busy) CircularProgressIndicator(Modifier.size(16.dp)) else Text("Save") }
                    Button(enabled = !busy && issueErrors.isEmpty() && !unsavedIssueChanges, onClick = { confirmIssue = true }) { Text("Issue") }
                }
                current?.status == PayslipStatus.ISSUED -> Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                    TextButton(enabled = !busy, onClick = { busy = true; scope.launch { runCatching { viewModel.copyDraft(id) }.onSuccess { message = "Draft copy created: $it" }.onFailure { message = it.message ?: "Duplicate failed" }; busy = false } }) { Text("Duplicate") }
                    TextButton(enabled = !busy, onClick = { busy = true; scope.launch { runCatching { viewModel.createCorrection(id) }.onSuccess { message = "Correction draft created: $it" }.onFailure { message = it.message ?: "Correction failed" }; busy = false } }) { Text("Correct") }
                }
                else -> TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (current?.status == PayslipStatus.ISSUED) Column(horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    TextButton(enabled = !busy, onClick = { bundle?.let { loaded -> busy = true; scope.launch { runCatching { sharePayslipPdf(context, loaded) }.onFailure { message = it.message ?: "PDF share failed" }; busy = false } } }) { Text("Share") }
                    TextButton(enabled = !busy, onClick = { bundle?.let(onExport) }) { Text("Save PDF") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    TextButton(enabled = !busy, onClick = { bundle?.let { loaded -> busy = true; scope.launch { runCatching { printPayslipPdf(context, loaded) }.onFailure { message = it.message ?: "Print failed" }; busy = false } } }) { Text("Print") }
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
            }
        },
    )
    if (confirmIssue && current != null) AlertDialog(
        onDismissRequest = { confirmIssue = false }, title = { Text("Issue and lock payslip?") },
        text = { Text("This freezes the business and employee details, dates, currency, salary lines, and totals. Changes later require a linked correction.") },
        confirmButton = { Button(enabled = !busy, onClick = { busy = true; scope.launch {
            runCatching { viewModel.issue(id) }.onSuccess { errors ->
                if (errors.isEmpty()) { message = "Payslip issued and locked"; bundle = viewModel.loadPayslip(id); confirmIssue = false }
                else message = errors.joinToString(" · ")
            }.onFailure { message = it.message ?: "Could not issue payslip" }
            busy = false
        } }) { Text("Issue") } },
        dismissButton = { TextButton(onClick = { confirmIssue = false }) { Text("Cancel") } },
    )
}

@Composable
fun SalaryTemplateScreen(viewModel: PayrollViewModel, employees: List<EmployeeEntity>, templates: List<SalaryTemplateEntity>, payslips: List<PayslipEntity>, modifier: Modifier = Modifier, onExport: (PayslipBundle) -> Unit) {
    var archived by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SalaryTemplateEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var using by remember { mutableStateOf<SalaryTemplateEntity?>(null) }
    var newSlipId by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    val visible = templates.filter { it.archived == archived }
    Column(modifier.fillMaxSize().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Salary templates", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold); Text("Applied values copy to a draft; template edits do not rewrite payslips.", style = MaterialTheme.typography.bodySmall) }
            Button(onClick = { editing = null; creating = true }) { Icon(Icons.Default.Add, null); Text("New") }
        }
        Row { FilterChip(!archived, { archived = false }, label = { Text("Active") }); Spacer(Modifier.width(6.dp)); FilterChip(archived, { archived = true }, label = { Text("Archived") }) }
        if (visible.isEmpty()) Text(if (archived) "No archived templates." else "Create a template to reuse recurring salary components.", Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(visible, key = { it.id }) { template ->
                val employee = template.employeeId?.let { id -> employees.firstOrNull { it.id == id }?.fullName } ?: "Any employee"
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text(template.name, fontWeight = FontWeight.SemiBold); Text("${template.currencyCode} · $employee", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (template.archived) TextButton(onClick = { scope.launch { runCatching { viewModel.restoreTemplate(template.id) }.onFailure { viewModel.message(it.message ?: "Restore failed") } } }) { Icon(Icons.Default.Restore, null); Text("Restore") }
                        else {
                            TextButton(onClick = { using = template }) { Text("Use") }
                            TextButton(onClick = { editing = template }) { Icon(Icons.Default.Edit, null); Text("Edit") }
                            TextButton(onClick = { scope.launch { runCatching { viewModel.archiveTemplate(template.id) }.onFailure { viewModel.message(it.message ?: "Archive failed") } } }) { Icon(Icons.Default.Archive, null); Text("Archive") }
                        }
                    }
                } }
            }
        }
    }
    if (creating || editing != null) TemplateDialog(editing, viewModel, employees, { creating = false; editing = null })
    using?.let { template -> NewSlipDialog(employees, { using = null }) { employeeId -> newSlipId = viewModel.createFromTemplate(template.id, employeeId); using = null } }
    newSlipId?.let { PayslipEditorDialog(it, viewModel, payslips, onExport) { newSlipId = null } }
}

@Composable
private fun TemplateDialog(initial: SalaryTemplateEntity?, viewModel: PayrollViewModel, employees: List<EmployeeEntity>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var currency by remember(initial) { mutableStateOf(initial?.currencyCode ?: "IDR") }
    var employeeId by remember(initial) { mutableStateOf(initial?.employeeId) }
    var employeeMenu by remember { mutableStateOf(false) }
    var lines by remember(initial) { mutableStateOf<List<EditorLine>>(emptyList()) }
    var loaded by remember(initial) { mutableStateOf(initial == null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(initial?.id) { if (initial != null) runCatching { viewModel.loadTemplate(initial.id) }.onSuccess { b ->
        if (b == null) error = "Template not found" else { lines = b.lines.map { EditorLine(kind = it.kind, label = it.label, amount = it.amount.toPlainString()) }; if (b.invalidLineCount > 0) error = "Invalid stored lines; replace them before saving." }; loaded = true
    }.onFailure { error = it.message ?: "Could not load template"; loaded = true } }
    val parsed = parseEditorLines(lines)
    val valid = loaded && name.isNotBlank() && currency.isNotBlank() && parsed.error == null && parsed.lines.any { it.kind == SalaryLineKind.EARNING && it.amount.signum() > 0 }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if (initial == null) "New template" else "Edit template") },
        text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!loaded) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            OutlinedTextField(name, { name = it }, label = { Text("Template name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(currency, { currency = it }, label = { Text("Currency code or symbol") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Box { OutlinedButton(onClick = { employeeMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(employeeId?.let { id -> employees.firstOrNull { it.id == id }?.fullName } ?: "Available to any employee") }
                DropdownMenu(employeeMenu, { employeeMenu = false }) {
                    DropdownMenuItem(text = { Text("Any employee") }, onClick = { employeeId = null; employeeMenu = false })
                    employees.filter { !it.archived }.forEach { e -> DropdownMenuItem(text = { Text(e.fullName) }, onClick = { employeeId = e.id; employeeMenu = false }) }
                }
            }
            SalaryLineEditor(lines, { lines = it }, loaded)
            parsed.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }; error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { Button(enabled = valid && !busy, onClick = { busy = true; scope.launch {
            val template = (initial ?: SalaryTemplateEntity(name = name)).copy(name = name, currencyCode = currency, employeeId = employeeId)
            runCatching { viewModel.saveTemplate(template, parsed.lines) }.onSuccess { viewModel.message("Template saved"); onDismiss() }.onFailure { error = it.message ?: "Template save failed" }
            busy = false
        } }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SalaryLineEditor(lines: List<EditorLine>, onChange: (List<EditorLine>) -> Unit, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Salary components", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (enabled) { TextButton(onClick = { onChange(lines + EditorLine(kind = SalaryLineKind.EARNING)) }) { Text("+ Earn") }; TextButton(onClick = { onChange(lines + EditorLine(kind = SalaryLineKind.DEDUCTION)) }) { Text("+ Deduct") } }
        }
        if (lines.isEmpty()) Text("No components added.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        lines.forEach { line -> key(line.key) {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(line.label, { v -> onChange(lines.map { if (it.key == line.key) it.copy(label = v) else it }) }, label = { Text("Description") }, singleLine = true, enabled = enabled, modifier = Modifier.weight(1f))
                    OutlinedTextField(line.amount, { v -> onChange(lines.map { if (it.key == line.key) it.copy(amount = v) else it }) }, label = { Text("Amount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = enabled, modifier = Modifier.weight(0.8f))
                    if (enabled) IconButton(onClick = { onChange(lines.filterNot { it.key == line.key }) }) { Icon(Icons.Default.Delete, "Remove line") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = line.kind == SalaryLineKind.EARNING, onClick = { if (enabled) onChange(lines.map { if (it.key == line.key) it.copy(kind = SalaryLineKind.EARNING) else it }) }, enabled = enabled, label = { Text("Earning") })
                    FilterChip(selected = line.kind == SalaryLineKind.DEDUCTION, onClick = { if (enabled) onChange(lines.map { if (it.key == line.key) it.copy(kind = SalaryLineKind.DEDUCTION) else it }) }, enabled = enabled, label = { Text("Deduction") })
                }
            } }
        } }
    }
}

private fun parseEditorLines(lines: List<EditorLine>): ParsedLines {
    val parsed = lines.map { line ->
        val amount = SalaryMath.parseAmount(line.amount)
        if (line.label.isBlank() || amount == null) null else SalaryLine(line.label.trim(), line.kind, amount)
    }
    if (parsed.any { it == null }) return ParsedLines(emptyList(), "Each component needs a description and valid amount. Use a period for decimals and commas only as thousands separators.")
    return ParsedLines(parsed.filterNotNull(), null)
}

private fun money(value: String, currency: String): String = value.toBigDecimalOrNull()?.let { SalaryMoneyFormatter.format(it, currency) } ?: "Invalid amount"
private fun date(day: Long?): String = day?.let { runCatching { LocalDate.ofEpochDay(it).toString() }.getOrNull() }.orEmpty()
private fun parseDay(value: String): Long? = value.trim().takeIf(String::isNotEmpty)?.let { runCatching { LocalDate.parse(it).toEpochDay() }.getOrNull() }
private fun period(p: PayslipEntity): String = "${date(p.periodStartDay).ifBlank { "—" }} – ${date(p.periodEndDay).ifBlank { "—" }}"
