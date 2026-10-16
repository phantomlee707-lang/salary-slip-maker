package com.rainweaver.salaryslipmaker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rainweaver.salaryslipmaker.ui.ProfileViewModel
import com.rainweaver.salaryslipmaker.ui.SalarySlipApp
import com.rainweaver.salaryslipmaker.ui.SalarySlipTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SalarySlipTheme {
                val profileViewModel: ProfileViewModel = viewModel()
                SalarySlipApp(profileViewModel)
            }
        }
    }
}
