package com.gufont.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gufont.app.data.model.Step
import com.gufont.app.data.model.StepState

/**
 * 步骤直达菜单。
 * 未完成的前置步骤不可点（灰掉）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HamburgerMenu(
    currentStep: Int,
    stepStates: Map<Int, StepState>,
    onDismiss: () -> Unit,
    onStepSelected: (Int) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 32.dp)) {
            Text(
                "步骤",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            Step.values().forEach { s ->
                val st = stepStates[s.id] ?: StepState()
                val isCurrent = s.id == currentStep
                val unlocked = st.completed || s.id == Step.IMPORT.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = unlocked || isCurrent) {
                            onStepSelected(s.id)
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val mark = when {
                        isCurrent -> "●"
                        st.completed -> "✓"
                        unlocked -> "○"
                        else -> "·"
                    }
                    Text(mark, fontSize = 16.sp, modifier = Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            s.label,
                            fontSize = 15.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (unlocked || isCurrent)
                                MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.outline
                        )
                        if (st.total > 0) {
                            Text(
                                "${st.done} / ${st.total}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
