package com.realitycompiler

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> cameraGranted = granted }
    private var cameraGranted by mutableStateOf(false)
    private var imageCapture: ImageCapture? = null
    private var captureCount by mutableStateOf(0)
    private val capturedFiles = mutableListOf<File>()
    private var lastMessage by mutableStateOf('Move around the motor and capture distinct views.')
    private val api = MotorApi('http://10.0.2.2:8000')

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!cameraGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
        setContent { RealityCompilerScreen() }
    }

    @Composable
    private fun RealityCompilerScreen() {
        val context = LocalContext.current
        var showResults by remember { mutableStateOf(false) }
        var voltage by remember { mutableFloatStateOf(12f) }
        var twinStatus by remember { mutableStateOf('No digital twin yet') }
        Surface(modifier = Modifier.fillMaxSize()) {
            if (showResults) SimulationScreen(voltage, twinStatus) { showResults = false }
            else Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (cameraGranted) AndroidView(factory={ PreviewView(it).also{bindCamera(it)} }, modifier=Modifier.fillMaxSize())
                    else Column(Modifier.align(Alignment.Center), horizontalAlignment=Alignment.CenterHorizontally) { Text('Camera permission is required to scan a motor.'); Button(onClick={permissionLauncher.launch(Manifest.permission.CAMERA)}){Text('Grant camera')} }
                    Card(Modifier.align(Alignment.TopCenter).padding(16.dp)) { Column(Modifier.padding(12.dp)) { Text('DC MOTOR SCAN', style=MaterialTheme.typography.titleMedium); Text("Views captured: $captureCount"); Text(lastMessage) } }
                }
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Button(onClick={captureView(context)}, enabled=cameraGranted, modifier=Modifier.weight(1f)){Text('Capture view')}
                    Button(onClick={ Thread{try{val result=api.analyze(capturedFiles.toList());runOnUiThread{twinStatus=result}}catch(e:Exception){runOnUiThread{twinStatus="Cloud processing unavailable: ${e.message ?: "network error"}"}}}.start() }, enabled=captureCount>=3, modifier=Modifier.weight(1f)){Text('Analyze')}
                }
                Button(onClick={showResults=true}, enabled=captureCount>=3, modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp)){Text('Open simulation')}
            }
        }
    }

    private fun bindCamera(previewView: PreviewView) {
        val future=ProcessCameraProvider.getInstance(this)
        future.addListener({ val provider=future.get(); val preview=androidx.camera.core.Preview.Builder().build().also{it.surfaceProvider=previewView.surfaceProvider}; imageCapture=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build(); provider.unbindAll(); provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture) }, ContextCompat.getMainExecutor(this))
    }

    private fun captureView(context: android.content.Context) {
        val capture=imageCapture ?: return
        val file=File(context.cacheDir, "scan_${System.currentTimeMillis()}.jpg")
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor, object:ImageCapture.OnImageSavedCallback{
            override fun onImageSaved(outputFileResults:ImageCapture.OutputFileResults){capturedFiles.add(file);captureCount+=1;lastMessage=if(captureCount<8)'Good. Rotate to a new side before the next capture.' else 'Capture set ready. Analyze to build the digital twin.'}
            override fun onError(exception:ImageCaptureException){lastMessage='Capture failed: ${exception.message ?: "unknown camera error"}'}
        })
    }

    @Composable
    private fun SimulationScreen(voltage:Float, twinStatus:String, onBack:()->Unit){
        var v by remember{mutableFloatStateOf(voltage)}; var speed by remember{mutableFloatStateOf(0f)}; var current by remember{mutableFloatStateOf(0f)}; var status by remember{mutableStateOf('Simulation not run')}
        Column(Modifier.fillMaxSize().padding(20.dp)){Text('DC MOTOR DIGITAL TWIN', style=MaterialTheme.typography.headlineSmall);Spacer(Modifier.height(12.dp));Text('Observed: housing + shaft');Text('Inferred: brushed DC motor (backend perception)');Text(twinStatus);Text('Unknown: winding configuration, magnet strength, exact inertia');Spacer(Modifier.height(20.dp));Text("Voltage: ${"%.1f".format(v)} V");Slider(value=v,onValueChange={v=it},valueRange=1f..24f)
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Button(onClick={Thread{try{val result=api.simulate(v);runOnUiThread{speed=result.speedRpm;current=result.currentA;status='Real backend simulation complete'}}catch(e:Exception){runOnUiThread{status="Cloud processing unavailable: ${e.message ?: "network error"}"}}}.start()}){Text('Run simulation')};Button(onClick=onBack){Text('Back')}}
            Spacer(Modifier.height(20.dp));Text(status);Text("Speed: ${"%.0f".format(speed)} rpm");Text("Current: ${"%.2f".format(current)} A")
            Button(onClick={Thread{try{val result=api.compare(v,24f);runOnUiThread{status="What-If: 24 V → Δspeed=${"%.0f".format(result.speedDelta)} rpm, Δcurrent=${"%.2f".format(result.currentDelta)} A"}}catch(e:Exception){runOnUiThread{status="What-If unavailable: ${e.message ?: "network error"}"}}}.start()}){Text('What If: 24 V')}
            Text('Results are simulation outputs, not safety certification.')
        }
    }
}
