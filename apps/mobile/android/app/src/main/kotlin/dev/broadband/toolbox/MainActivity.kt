package dev.broadband.toolbox

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {
    private var controller: PlatformController? = null
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger
        controller = PlatformController(this, NetworkFlutterApi(messenger))
        NetworkHostApi.setUp(messenger, controller)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        controller?.onPermissionsResult(requestCode)
    }
    override fun onResume() { super.onResume(); controller?.onForeground() }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        controller?.onActivityResult(requestCode, resultCode, data)
    }
    override fun onStop() { controller?.onBackground(); super.onStop() }
    override fun onDestroy() { controller?.close(); controller = null; super.onDestroy() }
}
