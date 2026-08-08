package com.ykatchou.ylauncher.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ykatchou.ylauncher.util.openPlayStoreListing

@Composable
fun RateFab(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    FloatingActionButton(
        onClick = { context.openPlayStoreListing() },
        containerColor = Color(0xFFFFC107),
        contentColor = Color.White,
        shape = CircleShape,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
        modifier = modifier.size(48.dp),
    ) {
        Text(
            text = "⭐",
            fontSize = 20.sp,
        )
    }
}
