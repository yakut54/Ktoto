package ru.yakut54.ktoto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.yakut54.ktoto.ui.navigation.AppNavigation
import ru.yakut54.ktoto.ui.theme.KtotoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KtotoTheme {
                AppNavigation()
            }
        }
    }
}
