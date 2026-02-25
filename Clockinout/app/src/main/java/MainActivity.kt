package com.example.clocktracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// ---- Alpine Green palette ----
val AlpineGreenDark = Color(0xFF1B5E38)
val AlpineGreen = Color(0xFF2E7D52)
val AlpineGreenLight = Color(0xFF4CAF7D)
val AlpineGreenPale = Color(0xFFB9E4CC)
val AlpineGreenBg = Color(0xFFE8F5EC)
val AlpineWhite = Color(0xFFF5FFF8)
val WarmAmber = Color(0xFFFFA726)
val SoftRed = Color(0xFFEF5350)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClockTrackerApp()
        }
    }
}

@Composable
fun ClockTrackerApp(vm: ClockViewModel = viewModel()) {
    val isClockedIn by vm.isClockedIn.collectAsState()
    val isOnBreak by vm.isOnBreak.collectAsState()
    val sessionSeconds by vm.currentSessionSeconds.collectAsState()
    val weeklyTotals by vm.weeklyTotals.collectAsState()

    // Dialog state for manual entry
    var showDialog by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableStateOf<DayOfWeek?>(null) }
    var inputHours by remember { mutableStateOf("") }
    var inputMinutes by remember { mutableStateOf("") }

    val bgBrush = Brush.verticalGradient(
        colors = listOf(AlpineGreenDark, AlpineGreen, AlpineGreenLight)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ---- Header ----
        Text(
            text = "Clock Tracker",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = AlpineWhite,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )

        Text(
            text = weekLabel(),
            fontSize = 14.sp,
            color = AlpineGreenPale,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ---- Timer display ----
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = AlpineWhite.copy(alpha = 0.15f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp)
            ) {
                // Status label
                val statusText = when {
                    isOnBreak -> "ON BREAK"
                    isClockedIn -> "CLOCKED IN"
                    else -> "CLOCKED OUT"
                }
                val statusColor by animateColorAsState(
                    targetValue = when {
                        isOnBreak -> WarmAmber
                        isClockedIn -> AlpineGreenPale
                        else -> Color.White.copy(alpha = 0.6f)
                    },
                    animationSpec = tween(400),
                    label = "statusColor"
                )
                Text(
                    text = statusText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Big timer
                Text(
                    text = ClockViewModel.formatSeconds(sessionSeconds),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.White,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }

        // ---- Buttons row ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Clock In / Out button
            val clockButtonColor by animateColorAsState(
                targetValue = if (isClockedIn) SoftRed else AlpineGreenPale,
                animationSpec = tween(300),
                label = "clockBtnColor"
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = { vm.toggleClock() },
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = clockButtonColor),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = if (isClockedIn) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isClockedIn) "Clock Out" else "Clock In",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isClockedIn) "Clock Out" else "Clock In",
                    color = AlpineWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Break button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val breakBtnColor by animateColorAsState(
                    targetValue = if (isOnBreak) WarmAmber else AlpineWhite.copy(alpha = 0.25f),
                    animationSpec = tween(300),
                    label = "breakBtnColor"
                )
                Button(
                    onClick = { vm.toggleBreak() },
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    enabled = isClockedIn,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = breakBtnColor,
                        disabledContainerColor = Color.White.copy(alpha = 0.08f)
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Coffee,
                        contentDescription = "Break",
                        tint = if (isClockedIn) Color.White else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isOnBreak) "Resume" else "Break",
                    color = if (isClockedIn) AlpineWhite else AlpineWhite.copy(alpha = 0.4f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // ---- Weekly list ----
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = AlpineWhite),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header + Reset button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "This Week",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AlpineGreenDark
                    )

                    val canReset = !isClockedIn && !isOnBreak

                    TextButton(
                        onClick = { vm.resetWeekManually() },
                        enabled = canReset
                    ) {
                        Text(
                            text = "Reset",
                            color = if (canReset) SoftRed else SoftRed.copy(alpha = 0.3f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                val today = LocalDate.now().dayOfWeek

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(ClockViewModel.DAY_ORDER) { day ->
                        val seconds = weeklyTotals[day.name] ?: 0L
                        val isToday = day == today
                        DayRow(
                            day = day,
                            totalSeconds = seconds,
                            isToday = isToday,
                            onClick = {
                                selectedDay = day
                                inputHours = ""
                                inputMinutes = ""
                                showDialog = true
                            }
                        )
                    }

                    // Weekly total
                    item {
                        Divider(
                            color = AlpineGreenPale,
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Total",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = AlpineGreenDark
                            )
                            val total = weeklyTotals.values.sum()
                            Text(
                                text = ClockViewModel.formatHoursMinutes(total),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = AlpineGreenDark
                            )
                        }
                    }
                }
            }
        }

        // ---- Manual entry dialog ----
        if (showDialog && selectedDay != null) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = {
                    Text(
                        "Set hours for ${
                            selectedDay!!.getDisplayName(
                                TextStyle.FULL,
                                Locale.getDefault()
                            )
                        }"
                    )
                },
                text = {
                    Column {
                        OutlinedTextField(
                            value = inputHours,
                            onValueChange = { inputHours = it },
                            label = { Text("Hours") },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = inputMinutes,
                            onValueChange = { inputMinutes = it },
                            label = { Text("Minutes") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val h = inputHours.toIntOrNull() ?: 0
                        val m = inputMinutes.toIntOrNull() ?: 0
                        selectedDay?.let { vm.setManualTime(it, h, m) }
                        showDialog = false
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun DayRow(
    day: DayOfWeek,
    totalSeconds: Long,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val dayName = day.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val bgColor = if (isToday) AlpineGreenPale.copy(alpha = 0.3f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isToday) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(AlpineGreen)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = dayName,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                fontSize = 15.sp,
                color = if (isToday) AlpineGreenDark else Color.DarkGray
            )
        }
        Text(
            text = if (totalSeconds == 0L) "—" else ClockViewModel.formatHoursMinutes(totalSeconds),
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            fontSize = 15.sp,
            color = if (totalSeconds > 0) AlpineGreenDark else Color.Gray,
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun weekLabel(): String {
    val monday = LocalDate.now()
        .with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val sunday = monday.plusDays(6)
    val fmt = java.time.format.DateTimeFormatter.ofPattern("MMM d")
    return "${monday.format(fmt)} – ${sunday.format(fmt)}"
}
