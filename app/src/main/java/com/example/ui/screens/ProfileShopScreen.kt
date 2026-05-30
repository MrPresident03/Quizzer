package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserStats
import com.example.ui.theme.GoldYellow
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileAndShopScreen(
    userStats: UserStats,
    onUpdateProfile: (name: String, avatarId: String) -> Unit,
    onBuyLifeline: (type: String, cost: Int) -> Unit,
    onBuyTheme: (themeId: String, cost: Int) -> Unit,
    onSelectTheme: (themeId: String) -> Unit
) {
    var activeTab by remember { mutableStateOf(0) } // 0 = Profile Editor, 1 = XP Shop

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile & Shop", fontWeight = FontWeight.ExtraBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Profile / Shop Toggle
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("My Profile", fontWeight = FontWeight.Bold) },
                    icon = { Icon(imageVector = Icons.Default.Face, contentDescription = "Profile") },
                    modifier = Modifier.testTag("profile_tab_button")
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("XP Shop", fontWeight = FontWeight.Bold) },
                    icon = { Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = "Shop") },
                    modifier = Modifier.testTag("shop_tab_button")
                )
            }

            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { width -> width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> -width } + fadeOut()
                    } else {
                        slideInHorizontally { width -> -width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> width } + fadeOut()
                    }
                },
                label = "ProfileTabContent",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { targetTab ->
                when (targetTab) {
                    0 -> ActiveProfileTab(userStats, onUpdateProfile)
                    1 -> XpStoreTab(userStats, onBuyLifeline, onBuyTheme, onSelectTheme)
                }
            }
        }
    }
}

// --- TAB A: PROFILE CUSTOMIZATION ---
@Composable
fun ActiveProfileTab(userStats: UserStats, onUpdateProfile: (String, String) -> Unit) {
    var nameInput by remember { mutableStateOf(userStats.displayName) }
    var selectedAvatar by remember { mutableStateOf(userStats.avatarId) }

    val avatars = listOf(
        "avatar_1" to Icons.Default.Face,
        "avatar_2" to Icons.Default.Psychology,
        "avatar_3" to Icons.Default.Stars,
        "avatar_4" to Icons.Default.SportsEsports
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(bottom = 95.dp)
    ) {
        // Main Avatar Header Display
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val matchingIcon = avatars.find { it.first == selectedAvatar }?.second ?: Icons.Default.Face
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = matchingIcon,
                        contentDescription = "Selected Avatar",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                }
                Text("Tap on an avatar below to choose", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            }
        }

        // Available Avatars Row List
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                avatars.forEach { (id, icon) ->
                    val isSelected = selectedAvatar == id
                    Surface(
                        onClick = { selectedAvatar = id },
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier.size(60.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = id,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }

        // Input forms
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Display Nickname", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                TextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    placeholder = { Text("Enter nickname...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_nickname_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }
        }

        // Save Button
        item {
            Button(
                onClick = { onUpdateProfile(nameInput, selectedAvatar) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_profile_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Save Profile Customizations", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// --- TAB B: XP STORE TAB ---
@Composable
fun XpStoreTab(
    userStats: UserStats,
    onBuyLifeline: (type: String, cost: Int) -> Unit,
    onBuyTheme: (themeId: String, cost: Int) -> Unit,
    onSelectTheme: (themeId: String) -> Unit
) {
    val moshi = remember { Moshi.Builder().build() }
    val unlockedThemes = remember(userStats.unlockedThemesJson) {
        val listType = Types.newParameterizedType(List::class.java, String::class.java)
        moshi.adapter<List<String>>(listType).fromJson(userStats.unlockedThemesJson) ?: listOf("midnight_blue")
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(bottom = 95.dp)
    ) {
        // Coin indicator
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Savings, tint = GoldYellow, contentDescription = "Bank", modifier = Modifier.size(32.dp))
                        Column {
                            Text("Your Balance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f))
                            Text("${userStats.xpPoints} XP Coins", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = GoldYellow)
                        }
                    }
                    Text("Spent to unlock Items", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f))
                }
            }
        }

        // Section A: BUY LIFELINES COUNTS
        item {
            Text("Power Ups (Spend XP)", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }

        val powerups = listOf(
            Triple("50/50 Eliminator", "Removes 2 wrong alternatives in any question.", "5050" to 100),
            Triple("Skip Question Ticket", "Bypasses the current prompt with no penalty.", "skip" to 150),
            Triple("Extra Time +15s", "Add 15 seconds to the depletion question timer.", "time" to 80),
            Triple("Ask the Audience Poll", "Triggers a smart visual poll chart simulation.", "audience" to 120),
            Triple("Streak Freeze Grace", "Converts automatic streak reset penalty to active safe.", "freeze" to 250)
        )

        items(powerups) { (name, desc, pair) ->
            val (type, cost) = pair
            val canAfford = userStats.xpPoints >= cost

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("💵 Cost: $cost XP", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = GoldYellow)
                    }
                    Button(
                        onClick = { onBuyLifeline(type, cost) },
                        enabled = canAfford,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("buy_lifeline_$type")
                    ) {
                        Text("Buy", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Section B: COSMETIC COLOR THEMES UNLOCK
        item {
            Text("Cosmetic App Themes", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }

        val themesList = listOf(
            Triple("Midnight Blue", "Strong corporate look with navy core accents.", "midnight_blue"),
            Triple("Forest Green", "Calming shades of emerald leaves.", "forest_green"),
            Triple("Sunset Orange", "Vibrant, high energy fiery canvas.", "sunset_orange"),
            Triple("Classic Purple", "Standard native violet violet hues.", "classic")
        )

        items(themesList) { (title, label, themeId) ->
            val isUnlocked = unlockedThemes.contains(themeId)
            val isActive = userStats.activeThemeId == themeId
            val cost = 500
            val canAfford = userStats.xpPoints >= cost

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(4.dp))
                        if (isUnlocked) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = if (isActive) "✓ ACTIVE THEME" else "UNLOCKED",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Text("💵 Cost: $cost XP", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = GoldYellow)
                        }
                    }

                    if (isUnlocked) {
                        Button(
                            onClick = { onSelectTheme(themeId) },
                            enabled = !isActive,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("select_theme_$themeId")
                        ) {
                            Text(if (isActive) "Active" else "Apply", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { onBuyTheme(themeId, cost) },
                            enabled = canAfford,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("buy_theme_$themeId")
                        ) {
                            Text("Unlock", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
