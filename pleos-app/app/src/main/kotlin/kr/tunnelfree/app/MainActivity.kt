package kr.tunnelfree.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kr.tunnelfree.app.ui.TunnelFreeViewModel
import kr.tunnelfree.app.ui.screens.TunnelFreeScreen
import kr.tunnelfree.app.ui.theme.TunnelFreeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: TunnelFreeViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        )

        lifecycleScope.launch {
            viewModel.dialRequests.collect { number -> dial(number) }
        }

        setContent {
            TunnelFreeTheme {
                TunnelFreeScreen(viewModel)
            }
        }
    }

    private fun dial(number: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "이 환경에서는 전화 앱을 열 수 없습니다. ($number)", Toast.LENGTH_LONG).show()
        }
    }
}
