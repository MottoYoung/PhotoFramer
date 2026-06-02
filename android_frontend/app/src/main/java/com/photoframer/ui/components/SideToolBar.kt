package com.photoframer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.photoframer.ui.theme.AccentYellow
import com.photoframer.ui.theme.ControlBackground
import com.photoframer.ui.theme.ControlSelectedBackground
import com.photoframer.ui.theme.PillBackground

enum class AspectRatioOption(val label: String) {
    RATIO_4_3("3:4"),
    RATIO_1_1("1:1"),
    FULL("全屏")
}

enum class CaptureTimer(val seconds: Int, val label: String) {
    OFF(0, "关"),
    S3(3, "3s"),
    S5(5, "5s"),
    S10(10, "10s")
}

@Composable
fun SideToolBar(
    selectedRatio: AspectRatioOption,
    onRatioChange: (AspectRatioOption) -> Unit,
    selectedTimer: CaptureTimer,
    onTimerChange: (CaptureTimer) -> Unit,
    touchScreenPhotoEnabled: Boolean,
    onTouchScreenPhotoToggle: () -> Unit,
    arExperimentEnabled: Boolean,
    onArExperimentToggle: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = 24.dp)
) {
    var ratioMenuExpanded by remember { mutableStateOf(false) }
    var timerMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.padding(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.End
    ) {
        Box(contentAlignment = Alignment.CenterEnd) {
            SideCircleButton(
                icon = Icons.Default.Crop,
                label = selectedRatio.label,
                onClick = {
                    ratioMenuExpanded = !ratioMenuExpanded
                    if (ratioMenuExpanded) {
                        timerMenuExpanded = false
                    }
                }
            )

            DropdownMenu(
                expanded = ratioMenuExpanded,
                onDismissRequest = { ratioMenuExpanded = false },
                modifier = Modifier
                    .offset(x = (-6).dp)
                    .background(PillBackground.copy(alpha = 0.96f), RoundedCornerShape(14.dp))
            ) {
                AspectRatioOption.values().forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.width(88.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = option.label,
                                    color = if (option == selectedRatio) AccentYellow else Color.White
                                )
                                if (option == selectedRatio) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "当前",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AccentYellow.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onRatioChange(option)
                            ratioMenuExpanded = false
                        }
                    )
                }
            }
        }

        Box(contentAlignment = Alignment.CenterEnd) {
            SideCircleButton(
                icon = Icons.Default.Timer,
                label = selectedTimer.label,
                onClick = {
                    timerMenuExpanded = !timerMenuExpanded
                    if (timerMenuExpanded) {
                        ratioMenuExpanded = false
                    }
                }
            )

            DropdownMenu(
                expanded = timerMenuExpanded,
                onDismissRequest = { timerMenuExpanded = false },
                modifier = Modifier
                    .offset(x = (-6).dp)
                    .background(PillBackground.copy(alpha = 0.96f), RoundedCornerShape(14.dp))
            ) {
                CaptureTimer.values().forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.width(88.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = option.label,
                                    color = if (option == selectedTimer) AccentYellow else Color.White
                                )
                                if (option == selectedTimer) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "当前",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AccentYellow.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onTimerChange(option)
                            timerMenuExpanded = false
                        }
                    )
                }
            }
        }

        SideToggleCircleButton(
            icon = Icons.Default.TouchApp,
            label = "触屏拍照",
            isSelected = touchScreenPhotoEnabled,
            onClick = onTouchScreenPhotoToggle
        )

        SideToggleCircleButton(
            icon = Icons.Default.CenterFocusStrong,
            label = "AR",
            isSelected = arExperimentEnabled,
            onClick = onArExperimentToggle
        )
    }
}

@Composable
private fun SideCircleButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = ControlBackground.copy(alpha = 0.82f),
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = AccentYellow.copy(alpha = 0.95f),
                modifier = Modifier.alpha(0.98f)
            )
        }
    }
}

@Composable
private fun SideToggleCircleButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = if (isSelected) {
            ControlSelectedBackground.copy(alpha = 0.82f)
        } else {
            ControlBackground.copy(alpha = 0.82f)
        },
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) AccentYellow else Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) AccentYellow else Color.White.copy(alpha = 0.84f),
                modifier = Modifier.alpha(0.98f)
            )
        }
    }
}
