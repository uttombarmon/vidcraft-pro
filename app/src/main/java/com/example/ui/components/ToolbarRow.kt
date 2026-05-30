package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.ToolType

data class ToolbarItem(
    val type: ToolType,
    val title: String,
    val icon: ImageVector,
    val instantAction: Boolean = false
)

@Composable
fun ToolbarRow(
    activeTool: ToolType,
    hasSelectedClip: Boolean,
    onToolClicked: (ToolType) -> Unit,
    onInstantSplit: () -> Unit,
    onInstantDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        ToolbarItem(ToolType.NONE, "Split", Icons.Default.ContentCut, instantAction = true),
        ToolbarItem(ToolType.CANVAS, "Canvas", Icons.Default.AspectRatio),
        ToolbarItem(ToolType.SPEED, "Speed", Icons.Default.Speed),
        ToolbarItem(ToolType.CROP_ROTATE, "Transform", Icons.Default.Transform),
        ToolbarItem(ToolType.ADJUST, "Adjust", Icons.Default.Tune),
        ToolbarItem(ToolType.FILTER, "Filters", Icons.Default.FilterBAndW),
        ToolbarItem(ToolType.TEXT, "Text", Icons.Default.TextFields),
        ToolbarItem(ToolType.STICKER, "Stickers", Icons.Default.StickyNote2),
        ToolbarItem(ToolType.AUDIO, "Music", Icons.Default.MusicNote)
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(Color(0xFF141414))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            items(items) { item ->
                val isSelected = activeTool == item.type
                val activeTintColor = if (isSelected) Color(0xFFFF6B35) else Color.White
                val activeBgColor = if (isSelected) Color(0xFFFF6B35).copy(alpha = 0.15f) else Color(0xFF1F1F1F)

                Surface(
                    onClick = {
                        if (item.instantAction) {
                            if (item.type == ToolType.NONE) {
                                onInstantSplit()
                            }
                        } else {
                            if (activeTool == item.type) {
                                onToolClicked(ToolType.NONE) // Deselect/Toggle
                            } else {
                                onToolClicked(item.type)
                            }
                        }
                    },
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = activeBgColor,
                    contentColor = activeTintColor,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) Color(0xFFFF6B35) else Color(0xFF2E2E2E)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 14.dp)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = activeTintColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.title,
                            fontSize = 11.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            color = activeTintColor
                        )
                    }
                }
            }
        }

        // Dedicated delete chip helper if clip or audio is selected
        if (hasSelectedClip) {
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onInstantDelete,
                modifier = Modifier
                    .size(52.dp)
                    .background(Color(0xFFFF3B30).copy(0.15f), RoundedCornerShape(8.dp)),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Clip",
                    tint = Color(0xFFFF3B30)
                )
            }
        }
    }
}
