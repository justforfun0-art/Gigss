package com.example.gigs.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gigs.data.model.ApplicationStatus
import com.example.gigs.data.model.ApplicationWithJob
import com.example.gigs.data.model.getDisplayText  // Import the extension function
import com.example.gigs.data.model.getDisplayTextWithJob
import com.example.gigs.data.model.shouldShowInStepper
import com.example.gigs.utils.DateUtils
import java.text.SimpleDateFormat
import java.util.*

data class ApplicationStep(
    val title: String,
    val stepNumber: Int,
    val icon: ImageVector? = null,
    val description: String,
    val detailedDescription: String = "",
    val isCompleted: Boolean,
    val isCurrent: Boolean,
    val isError: Boolean = false,
    val timestamp: String? = null,
    val estimatedDuration: String? = null,
    val actionRequired: String? = null
)

@Composable
fun ApplicationStatusStepper(
    applicationStatus: ApplicationStatus,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
    isCompact: Boolean = false,
    isVertical: Boolean = false,
    applicationWithJob: ApplicationWithJob? = null  // Pass real application data for timestamps
) {

    // 🚀 CRITICAL: Don't show stepper for NOT_INTERESTED status
    if (!applicationStatus.shouldShowInStepper()) {
        // Show a simple status card instead of stepper for NOT_INTERESTED
        NotInterestedStatusCard(
            applicationStatus = applicationStatus,
            applicationWithJob = applicationWithJob,
            modifier = modifier
        )
        return
    }

    val steps = getApplicationSteps(applicationStatus, applicationWithJob)

    if (isCompact) {
        CompactStatusStepper(
            steps = steps,
            modifier = modifier
        )
    } else if (isVertical) {
        VerticalStatusStepper(
            steps = steps,
            showLabels = showLabels,
            applicationStatus = applicationStatus,
            modifier = modifier
        )
    } else {
        CleanStatusStepper(
            steps = steps,
            showLabels = showLabels,
            applicationStatus = applicationStatus,
            modifier = modifier
        )
    }
}
/**
* 🚀 NEW: Special component for NOT_INTERESTED status (no stepper)
*/
@Composable
private fun NotInterestedStatusCard(
    applicationStatus: ApplicationStatus,
    applicationWithJob: ApplicationWithJob?,
    modifier: Modifier = Modifier
) {
    val jobName = applicationWithJob?.job?.title ?: "this job"

    // Make it match the regular ApplicationStatusStepper card size
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp) // Reduced padding to match other cards
        ) {
            // Header row with icon and title (matching other status cards)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ThumbDown,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp), // Smaller icon to match
                    tint = MaterialTheme.colorScheme.error
                )

                Text(
                    text = "Application Progress",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Status indicator row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Status circle (matching stepper design)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ThumbDown,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Status text
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Not Interested",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )

                    Text(
                        text = applicationStatus.getDisplayTextWithJob(jobName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Show timestamp if available
                    applicationWithJob?.updatedAt?.let { timestamp ->
                        Text(
                            text = "Marked on: ${DateUtils.formatDate(timestamp)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Status message card (compact)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            ) {
                Text(
                    text = "You can reconsider this job from the rejected jobs section",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}


@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun VerticalStatusStepper(
    steps: List<ApplicationStep>,
    showLabels: Boolean,
    applicationStatus: ApplicationStatus,
    modifier: Modifier = Modifier
) {
    var expandedStepIndex by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium
            )
            .padding(20.dp)
    ) {
        // Title
        Text(
            text = "Application Progress",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        steps.forEachIndexed { index, step ->
            VerticalStepItem(
                step = step,
                isLast = index == steps.size - 1,
                isExpanded = expandedStepIndex == index,
                onToggleExpand = {
                    expandedStepIndex = if (expandedStepIndex == index) null else index
                },
                showLabels = showLabels
            )
        }

        // Overall status message
        Spacer(modifier = Modifier.height(16.dp))
        StatusMessageCard(applicationStatus = applicationStatus)
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun VerticalStepItem(
    step: ApplicationStep,
    isLast: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    showLabels: Boolean
) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(300),
        label = "expansion_rotation"
    )

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Step indicator column
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Step circle with animation
                AnimatedStepCircle(
                    step = step,
                    modifier = Modifier.size(48.dp)
                )

                // Connecting line (except for last step)
                if (!isLast) {
                    VerticalConnectingLine(
                        isCompleted = step.isCompleted && !step.isError,
                        isError = step.isError,
                        modifier = Modifier
                            .width(3.dp)
                            .height(if (isExpanded) 120.dp else 60.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Content column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onToggleExpand() }
            ) {
                // Main step content
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = step.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (step.isCurrent) FontWeight.Bold else FontWeight.Medium,
                            color = getStepTextColor(step),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (showLabels) {
                            Text(
                                text = step.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Timestamp
                            step.timestamp?.let { timestamp ->
                                Text(
                                    text = timestamp,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }

                    // Expand/collapse icon
                    if (step.detailedDescription.isNotEmpty() || step.actionRequired != null) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(rotationAngle),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Expandable content
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(
                        animationSpec = tween(300),
                        expandFrom = Alignment.Top
                    ) + fadeIn(animationSpec = tween(300)),
                    exit = shrinkVertically(
                        animationSpec = tween(300),
                        shrinkTowards = Alignment.Top
                    ) + fadeOut(animationSpec = tween(300))
                ) {
                    Column(
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        // Detailed description
                        if (step.detailedDescription.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Text(
                                    text = step.detailedDescription,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        // Estimated duration
                        step.estimatedDuration?.let { duration ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Duration: $duration",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Action required
                        step.actionRequired?.let { action ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = action,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!isLast) {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AnimatedStepCircle(
    step: ApplicationStep,
    modifier: Modifier = Modifier
) {
    val animatedSize by animateFloatAsState(
        targetValue = if (step.isCurrent) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "circle_size"
    )

    val backgroundColor by animateColorAsState(
        targetValue = when {
            step.isError -> Color(0xFFE53E3E)
            step.isCurrent -> Color(0xFFD69E2E)
            step.isCompleted -> Color(0xFF38A169)
            else -> Color(0xFFE5E7EB)
        },
        animationSpec = tween(300),
        label = "background_color"
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .then(
                if (step.isCurrent) {
                    Modifier.animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy
                        )
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            step.isError -> {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = step.title,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            step.isCompleted && !step.isCurrent -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = step.title,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            step.isCurrent -> {
                step.icon?.let { icon ->
                    Icon(
                        imageVector = icon,
                        contentDescription = step.title,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                } ?: Text(
                    text = "${step.stepNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            else -> {
                Text(
                    text = "${step.stepNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF9CA3AF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun VerticalConnectingLine(
    isCompleted: Boolean,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedHeight by animateDpAsState(
        targetValue = if (isCompleted || isError) 60.dp else 40.dp,
        animationSpec = tween(300),
        label = "line_height"
    )

    Canvas(
        modifier = modifier.height(animatedHeight)
    ) {
        drawLine(
            color = when {
                isError -> Color(0xFFE53E3E)
                isCompleted -> Color(0xFF38A169)
                else -> Color(0xFFE5E7EB)
            },
            start = Offset(size.width / 2, 0f),
            end = Offset(size.width / 2, size.height),
            strokeWidth = size.width,
            cap = StrokeCap.Round
        )
    }
}



private fun getStepTextColor(step: ApplicationStep): Color {
    return when {
        step.isError -> Color(0xFFE53E3E) // Red for errors
        step.isCurrent -> Color(0xFFD69E2E) // Orange for current
        step.isCompleted -> Color(0xFF38A169) // Green for completed
        else -> Color(0xFF9CA3AF) // Gray for pending
    }
}

// Rest of the existing code (CompactStatusStepper, CleanStatusStepper, etc.)
@Composable
private fun CompactStatusStepper(
    steps: List<ApplicationStep>,
    modifier: Modifier = Modifier
) {
    val completedSteps = steps.count { it.isCompleted && !it.isError }
    val totalSteps = steps.size
    val currentStep = steps.find { it.isCurrent }
    val hasError = steps.any { it.isError }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.small
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    when {
                        hasError -> Color(0xFFE53E3E)
                        currentStep != null -> Color(0xFFD69E2E)
                        else -> Color(0xFF9CA3AF)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (hasError) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            } else if (currentStep?.isCompleted == true) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Text(
                    text = "${(currentStep?.stepNumber ?: 1)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = currentStep?.title ?: "Unknown",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (hasError) Color(0xFFE53E3E) else MaterialTheme.colorScheme.onSurface
            )

            if (currentStep?.description?.isNotEmpty() == true) {
                Text(
                    text = currentStep.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B7280),
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "$completedSteps/$totalSteps",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B7280),
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .width(50.dp)
                    .height(3.dp)
                    .background(
                        color = Color(0xFFE5E7EB),
                        shape = CircleShape
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(completedSteps.toFloat() / totalSteps.toFloat())
                        .background(
                            color = if (hasError) Color(0xFFE53E3E) else Color(0xFF38A169),
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

@Composable
private fun CleanStatusStepper(
    steps: List<ApplicationStep>,
    showLabels: Boolean,
    applicationStatus: ApplicationStatus,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium
            )
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            steps.forEachIndexed { index, step ->
                CleanStepCircle(
                    step = step,
                    modifier = Modifier.size(40.dp)
                )

                if (index < steps.size - 1) {
                    CleanConnectingLine(
                        isCompleted = step.isCompleted && !step.isError,
                        isError = step.isError,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (showLabels) {
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                steps.forEach { step ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = step.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (step.isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = getStepTextColor(step),
                            fontSize = 12.sp
                        )

                        if (step.description.isNotEmpty() && (step.isCurrent || step.isError)) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = step.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6B7280),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontSize = 10.sp,
                                lineHeight = 12.sp
                            )
                        }
                    }
                }
            }
        }

        val currentStep = steps.find { it.isCurrent }
        val errorStep = steps.find { it.isError }

        if (currentStep != null || errorStep != null) {
            Spacer(modifier = Modifier.height(16.dp))

            val (step, bgColor, textColor) = when {
                errorStep != null -> Triple(
                    errorStep,
                    Color(0xFFFEF2F2),
                    Color(0xFFDC2626)
                )
                currentStep != null -> Triple(
                    currentStep,
                    Color(0xFFF0F9FF),
                    Color(0xFF1D4ED8)
                )
                else -> Triple(null, Color.Transparent, Color.Transparent)
            }

            step?.let {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = bgColor,
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = getStatusMessage(applicationStatus),
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun CleanStepCircle(
    step: ApplicationStep,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(getStepTextColor(step)),
        contentAlignment = Alignment.Center
    ) {
        when {
            step.isError -> {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = step.title,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            step.isCompleted && !step.isCurrent -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = step.title,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            else -> {
                Text(
                    text = "${step.stepNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (step.isCompleted || step.isCurrent) Color.White else Color(0xFF9CA3AF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun CleanConnectingLine(
    isCompleted: Boolean,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .height(3.dp)
            .padding(horizontal = 8.dp)
    ) {
        drawLine(
            color = when {
                isError -> Color(0xFFE53E3E)
                isCompleted -> Color(0xFF38A169)
                else -> Color(0xFFE5E7EB)
            },
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = size.height,
            cap = StrokeCap.Round
        )
    }
}

// ApplicationStatusStepper.kt - UPDATED with COMPLETION_PENDING status

// Add this to your existing ApplicationStatusStepper.kt file

private fun getApplicationSteps(status: ApplicationStatus, applicationWithJob: ApplicationWithJob? = null): List<ApplicationStep> {
    // 🚀 SAFEGUARD: Return empty list for NOT_INTERESTED (shouldn't reach here, but just in case)
    if (!status.shouldShowInStepper()) {
        return emptyList()
    }

    val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())

    // Get job name for display
    val jobName = applicationWithJob?.job?.title ?: "this job"

    // Use real timestamps from application data
    val appliedTimestamp = applicationWithJob?.appliedAt?.let {
        try {
            val timestamp = if (it.toLongOrNull() != null) it.toLong() else {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(it)?.time ?: System.currentTimeMillis()
            }
            dateFormat.format(Date(timestamp))
        } catch (e: Exception) {
            it
        }
    }

    val updatedTimestamp = applicationWithJob?.updatedAt?.let {
        try {
            val timestamp = if (it.toLongOrNull() != null) it.toLong() else {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(it)?.time ?: System.currentTimeMillis()
            }
            dateFormat.format(Date(timestamp))
        } catch (e: Exception) {
            it
        }
    }

    // Work-related timestamps
    val workStartTimestamp = applicationWithJob?.workSession?.workStartTime?.let {
        try {
            val timestamp = if (it.toLongOrNull() != null) it.toLong() else {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(it)?.time ?: System.currentTimeMillis()
            }
            dateFormat.format(Date(timestamp))
        } catch (e: Exception) {
            it
        }
    }

    val workEndTimestamp = applicationWithJob?.workEndTime?.let {
        try {
            val timestamp = if (it.toLongOrNull() != null) it.toLong() else {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(it)?.time ?: System.currentTimeMillis()
            }
            dateFormat.format(Date(timestamp))
        } catch (e: Exception) {
            it
        }
    }

    // 🚀 UPDATED: Create steps dynamically based on current status - Added COMPLETION_PENDING
    return when (status) {
        ApplicationStatus.APPLIED -> {
            listOf(
                ApplicationStep(
                    title = "Applied",
                    stepNumber = 1,
                    icon = Icons.Default.Send,
                    description = "You have applied for $jobName",
                    detailedDescription = "Your application was submitted and is being reviewed by the employer.",
                    isCompleted = true,
                    isCurrent = true,
                    timestamp = appliedTimestamp,
                    estimatedDuration = "1-2 days",
                    actionRequired = null
                )
            )
        }

        ApplicationStatus.SELECTED -> {
            listOf(
                ApplicationStep(
                    title = "Applied",
                    stepNumber = 1,
                    icon = Icons.Default.Send,
                    description = "You have applied for $jobName",
                    detailedDescription = "Your application was submitted and reviewed by the employer.",
                    isCompleted = true,
                    isCurrent = false,
                    timestamp = appliedTimestamp,
                    estimatedDuration = null,
                    actionRequired = null
                ),
                ApplicationStep(
                    title = "Selected",
                    stepNumber = 2,
                    icon = Icons.Default.CheckCircle,
                    description = "You are selected for $jobName",
                    detailedDescription = "Great news! The employer has selected you for this job. You can now choose to accept or decline this opportunity.",
                    isCompleted = true,
                    isCurrent = true,
                    timestamp = updatedTimestamp,
                    estimatedDuration = null,
                    actionRequired = "Accept or decline this job offer"
                )
            )
        }

        ApplicationStatus.ACCEPTED -> {
            listOf(
                ApplicationStep(
                    title = "Applied",
                    stepNumber = 1,
                    icon = Icons.Default.Send,
                    description = "You have applied for $jobName",
                    detailedDescription = "Your application was submitted and reviewed by the employer.",
                    isCompleted = true,
                    isCurrent = false,
                    timestamp = appliedTimestamp,
                    estimatedDuration = null,
                    actionRequired = null
                ),
                ApplicationStep(
                    title = "Selected",
                    stepNumber = 2,
                    icon = Icons.Default.CheckCircle,
                    description = "You are selected for $jobName",
                    detailedDescription = "The employer selected you for this job.",
                    isCompleted = true,
                    isCurrent = false,
                    timestamp = null,
                    estimatedDuration = null,
                    actionRequired = null
                ),
                ApplicationStep(
                    title = "Accepted",
                    stepNumber = 3,
                    icon = Icons.Default.ThumbUp,
                    description = "You accepted $jobName and committed to show up on time",
                    detailedDescription = "You have accepted the job offer. The employer will provide you with an OTP when you arrive at the work location to start your work.",
                    isCompleted = true,
                    isCurrent = true,
                    timestamp = updatedTimestamp,
                    estimatedDuration = "Work start time: ${applicationWithJob?.job?.workDuration ?: "As scheduled"}",
                    actionRequired = "Get OTP from employer to start work"
                )
            )
        }

        ApplicationStatus.WORK_IN_PROGRESS -> {
            listOf(
                ApplicationStep(
                    title = "Applied",
                    stepNumber = 1,
                    icon = Icons.Default.Send,
                    description = "You have applied for $jobName",
                    detailedDescription = "Your application was submitted and reviewed by the employer.",
                    isCompleted = true,
                    isCurrent = false,
                    timestamp = appliedTimestamp,
                    estimatedDuration = null,
                    actionRequired = null
                ),
                ApplicationStep(
                    title = "Selected",
                    stepNumber = 2,
                    icon = Icons.Default.CheckCircle,
                    description = "You are selected for $jobName",
                    detailedDescription = "The employer selected you for this job.",
                    isCompleted = true,
                    isCurrent = false,
                    timestamp = null,
                    estimatedDuration = null,
                    actionRequired = null
                ),
                ApplicationStep(
                    title = "Accepted",
                    stepNumber = 3,
                    icon = Icons.Default.ThumbUp,
                    description = "You accepted $jobName and committed to show up on time",
                    detailedDescription = "You have accepted the job offer.",
                    isCompleted = true,
                    isCurrent = false,
                    timestamp = null,
                    estimatedDuration = null,
                    actionRequired = null
                ),
                ApplicationStep(
                    title = "Work In Progress",
                    stepNumber = 4,
                    icon = Icons.Default.Timer,
                    description = "You are currently doing the assigned work: $jobName",
                    detailedDescription = "You have started working on this job. Complete your work as per the job requirements and let the employer know when finished.",
                    isCompleted = true,
                    isCurrent = true,
                    timestamp = workStartTimestamp ?: updatedTimestamp,
                    estimatedDuration = applicationWithJob?.job?.workDuration,
                    actionRequired = "Complete the assigned work"
                )
            )
        }

        // 🚀 NEW: COMPLETION_PENDING status
        ApplicationStatus.COMPLETION_PENDING -> {
            listOf(
                ApplicationStep(
                    title = "Applied",
                    stepNumber = 1,
                    icon = Icons.Default.Send,
                    description = "You have applied for $jobName",
                    detailedDescription = "Your application was submitted and reviewed by the employer.",
                    isCompleted = true,
                    isCurrent = false,
                    timestamp = appliedTimestamp,
                    estimatedDuration = null,
                    actionRequired = null
                ),
                ApplicationStep(
                    title = "Selected",
                    stepNumber = 2,
                    icon = Icons.Default.CheckCircle,
                    description = "You are selected for $jobName",
                    detailedDescription = "The employer selected you for this job.",
                    isCompleted = true,
                    isCurrent = false,
                    timestamp = null,
                    estimatedDuration = null,
                    actionRequired = null
                ),
                ApplicationStep(
                    title = "Accepted",
                    stepNumber = 3,
                    icon = Icons.Default.ThumbUp,
                    description = "You accepted $jobName and committed to show up on time",
                    detailedDescription = "You have accepted the job offer.",
                    isCompleted = true,
                    isCurrent = false,
                    timestamp = null,
                    estimatedDuration = null,
                    actionRequired = null
                ),
                ApplicationStep(
                    title = "Work In Progress",
                    stepNumber = 4,
                    icon = Icons.Default.Timer,
                    description = "You completed the assigned work: $jobName",
                    detailedDescription = "You finished working on this job and initiated completion.",
                    isCompleted = true,
                    isCurrent = false,
                    timestamp = workStartTimestamp,
                    estimatedDuration = null,
                    actionRequired = null
                ),
                ApplicationStep(
                    title = "Completion Pending",
                    stepNumber = 5,
                    icon = Icons.Default.HourglassTop,
                    description = "Waiting for employer verification of work completion",
                    detailedDescription = "You have completed the work and generated a completion code. Share this code with your employer for verification and final payment calculation.",
                    isCompleted = true,
                    isCurrent = true,
                    timestamp = workEndTimestamp ?: updatedTimestamp,
                    estimatedDuration = null,
                    actionRequired = "Share completion code with employer"
                )
            )
        }

        ApplicationStatus.COMPLETED -> {
            listOf(
                ApplicationStep(
                    title = "Applied",
                    stepNumber = 1,
                    icon = Icons.Default.Send,
                    description = "You have applied for $jobName",
                    detailedDescription = "Your application was submitted and reviewed by the employer.",
                    isCompleted = true,
                    isCurrent = false,
                    timestamp = appliedTimestamp,
                    estimatedDuration = null,
                    actionRequired = null
                ),
                ApplicationStep(
                    title = "Selected",
                    stepNumber = 2,
                    icon = Icons.Default.CheckCircle,
                    description = "You are selected for $jobName",
                    detailedDescription = "The employer selected you for this job.",
                    isCompleted = true,
                    isCurrent = false,
                    timestamp = null,
                    estimatedDuration = null,
                    actionRequired = null
                ),
                ApplicationStep(
                    title = "Accepted",
                    stepNumber = 3,
                    icon = Icons.Default.ThumbUp,
                    description = "You accepted $jobName and committed to show up on time",
                    detailedDescription = "You have accepted the job offer.",
                    isCompleted = true,
                    isCurrent = false,
                    timestamp = null,
                    estimatedDuration = null,
                    actionRequired = null
                ),
                ApplicationStep(
                    title = "Work In Progress",
                    stepNumber = 4,
                    icon = Icons.Default.Timer,
                    description = "You completed the assigned work: $jobName",
                    detailedDescription = "You finished working on this job.",
                    isCompleted = true,
                    isCurrent = false,
                    timestamp = workStartTimestamp,
                    estimatedDuration = null,
                    actionRequired = null
                ),
                ApplicationStep(
                    title = "Completed",
                    stepNumber = 5,
                    icon = Icons.Default.CheckCircle,
                    description = "You have successfully completed and verified the job: $jobName",
                    detailedDescription = "Congratulations! You have successfully completed this job. Payment has been calculated and processed as per the agreement.",
                    isCompleted = true,
                    isCurrent = true,
                    timestamp = workEndTimestamp ?: updatedTimestamp,
                    estimatedDuration = null,
                    actionRequired = null
                )
            )
        }

        ApplicationStatus.REJECTED -> {
            listOf(
                ApplicationStep(
                    title = "Applied",
                    stepNumber = 1,
                    icon = Icons.Default.Send,
                    description = "You have applied for $jobName",
                    detailedDescription = "Your application was submitted and reviewed by the employer.",
                    isCompleted = true,
                    isCurrent = false,
                    timestamp = appliedTimestamp,
                    estimatedDuration = null,
                    actionRequired = null
                ),
                ApplicationStep(
                    title = "Rejected",
                    stepNumber = 2,
                    icon = Icons.Default.Cancel,
                    description = "You are rejected for the job: $jobName",
                    detailedDescription = "Unfortunately, the employer has decided not to proceed with your application for this position. Don't be discouraged - keep applying to other opportunities!",
                    isCompleted = false,
                    isCurrent = true,
                    isError = true,
                    timestamp = updatedTimestamp,
                    estimatedDuration = null,
                    actionRequired = null
                )
            )
        }

        ApplicationStatus.DECLINED -> {
            listOf(
                ApplicationStep(
                    title = "Applied",
                    stepNumber = 1,
                    icon = Icons.Default.Send,
                    description = "You have applied for $jobName",
                    detailedDescription = "Your application was submitted and reviewed by the employer.",
                    isCompleted = true,
                    isCurrent = false,
                    timestamp = appliedTimestamp,
                    estimatedDuration = null,
                    actionRequired = null
                ),
                ApplicationStep(
                    title = "Selected",
                    stepNumber = 2,
                    icon = Icons.Default.CheckCircle,
                    description = "You are selected for $jobName",
                    detailedDescription = "The employer selected you for this job.",
                    isCompleted = true,
                    isCurrent = false,
                    timestamp = null,
                    estimatedDuration = null,
                    actionRequired = null
                ),
                ApplicationStep(
                    title = "Declined",
                    stepNumber = 3,
                    icon = Icons.Default.ThumbDown,
                    description = "You declined the job: $jobName",
                    detailedDescription = "You have declined the job offer. The employer has been notified of your decision.",
                    isCompleted = false,
                    isCurrent = true,
                    isError = true,
                    timestamp = updatedTimestamp,
                    estimatedDuration = null,
                    actionRequired = null
                )
            )
        }

        else -> emptyList()
    }
}

// 🚀 UPDATE: Status message function to include COMPLETION_PENDING
private fun getStatusMessage(status: ApplicationStatus): String {
    return when (status) {
        ApplicationStatus.APPLIED -> "Your application has been submitted and is waiting for review"
        ApplicationStatus.SELECTED -> "Great news! You've been selected for this position"
        ApplicationStatus.ACCEPTED -> "You have accepted this job and committed to show up on time"
        ApplicationStatus.WORK_IN_PROGRESS -> "You are currently working on this job. Remember to complete it as per requirements"
        ApplicationStatus.COMPLETION_PENDING -> "Work completion is pending employer verification"  // 🚀 Added
        ApplicationStatus.COMPLETED -> "Great job! You have successfully completed this work assignment"
        ApplicationStatus.REJECTED -> "Unfortunately, your application was not selected for this position"
        ApplicationStatus.DECLINED -> "You have declined the offer for this position"
        ApplicationStatus.NOT_INTERESTED -> "You are not interested in this position"
    }
}

// 🚀 UPDATE: StatusMessageCard to handle COMPLETION_PENDING
@Composable
private fun StatusMessageCard(
    applicationStatus: ApplicationStatus
) {
    val (bgColor, textColor, message) = when (applicationStatus) {
        ApplicationStatus.REJECTED, ApplicationStatus.NOT_INTERESTED, ApplicationStatus.DECLINED -> Triple(
            Color(0xFFFEF2F2), // Light red background
            Color(0xFFDC2626), // Red text
            getStatusMessage(applicationStatus)
        )
        ApplicationStatus.COMPLETED -> Triple(
            Color(0xFFF0FDF4), // Light green background
            Color(0xFF16A34A), // Green text
            getStatusMessage(applicationStatus)
        )
        ApplicationStatus.WORK_IN_PROGRESS -> Triple(
            Color(0xFFFFF7ED), // Light orange background
            Color(0xFFEA580C), // Orange text
            getStatusMessage(applicationStatus)
        )
        ApplicationStatus.COMPLETION_PENDING -> Triple(  // 🚀 Added
            Color(0xFFFEF3C7), // Light yellow background
            Color(0xFFD97706), // Amber text
            getStatusMessage(applicationStatus)
        )
        else -> Triple(
            Color(0xFFF0F9FF), // Light blue background
            Color(0xFF1D4ED8), // Blue text
            getStatusMessage(applicationStatus)
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Add appropriate icon for each status
            Icon(
                imageVector = when (applicationStatus) {
                    ApplicationStatus.APPLIED -> Icons.Default.Send
                    ApplicationStatus.SELECTED -> Icons.Default.CheckCircle
                    ApplicationStatus.ACCEPTED -> Icons.Default.Schedule
                    ApplicationStatus.WORK_IN_PROGRESS -> Icons.Default.Timer
                    ApplicationStatus.COMPLETION_PENDING -> Icons.Default.HourglassTop  // 🚀 Added
                    ApplicationStatus.COMPLETED -> Icons.Default.CheckCircle
                    ApplicationStatus.REJECTED -> Icons.Default.Cancel
                    ApplicationStatus.DECLINED -> Icons.Default.ThumbDown
                    ApplicationStatus.NOT_INTERESTED -> Icons.Default.ThumbDown
                },
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}