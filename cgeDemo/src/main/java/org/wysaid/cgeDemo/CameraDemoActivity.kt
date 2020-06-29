package org.wysaid.cgeDemo

import android.content.Context
import android.content.Intent
import android.hardware.Camera
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import org.wysaid.camera.CameraInstance
import org.wysaid.cgeDemo.CameraDemoActivity
import org.wysaid.cgeDemo.CameraDemoActivity.Companion.instance
import org.wysaid.myUtils.FileUtil
import org.wysaid.myUtils.ImageUtil
import org.wysaid.myUtils.MsgUtil
import org.wysaid.nativePort.CGENativeLibrary
import org.wysaid.view.CameraGLSurfaceView.TakePictureCallback
import org.wysaid.view.CameraRecordGLSurfaceView

class CameraDemoActivity : AppCompatActivity() {
    private var mCurrentConfig: String? = null
    private var mCameraView: CameraRecordGLSurfaceView? = null
    private fun showText(s: String) {
        mCameraView!!.post { MsgUtil.toastMsg(this@CameraDemoActivity, s) }
    }

    class MyButtons(context: Context?, var filterConfig: String) : android.support.v7.widget.AppCompatButton(context)

    internal inner class RecordListener : View.OnClickListener {
        var isValid = true
        var recordFilename: String? = null
        override fun onClick(v: View) {
            val btn = v as Button
            if (!isValid) {
                Log.e(LOG_TAG, "Please wait for the call...")
                return
            }
            isValid = false
            if (!mCameraView!!.isRecording) {
                btn.text = "Recording"
                Log.i(LOG_TAG, "Start recording...")
                recordFilename = ImageUtil.getPath() + "/rec_" + System.currentTimeMillis() + ".mp4"
                //                recordFilename = ImageUtil.getPath(CameraDemoActivity.this, false) + "/rec_1.mp4";
                mCameraView!!.startRecording(recordFilename) { success ->
                    if (success) {
                        showText("Start recording OK")
                        FileUtil.saveTextContent(recordFilename, lastVideoPathFileName)
                    } else {
                        showText("Start recording failed")
                    }
                    isValid = true
                }
            } else {
                showText("Recorded as: $recordFilename")
                btn.text = "Recorded"
                Log.i(LOG_TAG, "End recording...")
                mCameraView!!.endRecording {
                    Log.i(LOG_TAG, "End recording OK")
                    isValid = true
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //Get rid of the Application name and stuff like that above the camera so it can be fullscreen
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        //Set it up!
        setContentView(R.layout.activity_camera_demo)

//        lastVideoPathFileName = FileUtil.getPathInPackage(CameraDemoActivity.this, true) + "/lastVideoPath.txt";
        val takePicBtn = findViewById(R.id.takePicBtn) as Button
        val takeShotBtn = findViewById(R.id.takeShotBtn) as Button
        val recordBtn = findViewById(R.id.recordBtn) as Button
        mCameraView = findViewById(R.id.myGLSurfaceView) as CameraRecordGLSurfaceView
        mCameraView!!.presetCameraForward(false)
        val seekBar = findViewById(R.id.globalRestoreSeekBar) as SeekBar

        //TAKE A PICTURE! :)
        takePicBtn.setOnClickListener {
            showText("Taking Picture...")
            mCameraView!!.takePicture(TakePictureCallback { bmp ->
                if (bmp != null) {
                    val s = ImageUtil.saveBitmap(bmp)
                    bmp.recycle()
                    showText("Take picture success!")
                    sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://$s")))
                } else showText("Take picture failed!")
            }, null, mCurrentConfig, 1.0f, true)
        }
        takeShotBtn.setOnClickListener {
            showText("Taking Shot...")
            mCameraView!!.takeShot { bmp ->
                if (bmp != null) {
                    val s = ImageUtil.saveBitmap(bmp)
                    bmp.recycle()
                    showText("Take Shot success!")
                    sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://$s")))
                } else showText("Take Shot failed!")
            }
        }
        recordBtn.setOnClickListener(RecordListener())
        val layout = findViewById(R.id.menuLinearLayout) as LinearLayout
        for (i in MainActivity.EFFECT_CONFIGS.indices) {
            val button = MyButtons(this, MainActivity.EFFECT_CONFIGS[i])
            button.setAllCaps(false)
            if (i == 0) button.text = "None" else button.text = "Filter$i"
            button.setOnClickListener(mFilterSwitchListener)
            layout.addView(button)
        }
        seekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val intensity = progress / 100.0f
                mCameraView!!.setFilterIntensity(intensity)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        instance = this
        val switchBtn = findViewById(R.id.switchCameraBtn) as Button
        switchBtn.setOnClickListener { mCameraView!!.switchCamera() }
        val flashBtn = findViewById(R.id.flashBtn) as Button
        flashBtn.setOnClickListener(object : View.OnClickListener {
            var flashIndex = 0
            var flashModes = arrayOf(
                    Camera.Parameters.FLASH_MODE_AUTO,
                    Camera.Parameters.FLASH_MODE_ON,
                    Camera.Parameters.FLASH_MODE_OFF,
                    Camera.Parameters.FLASH_MODE_TORCH,
                    Camera.Parameters.FLASH_MODE_RED_EYE)

            override fun onClick(v: View) {
                mCameraView!!.setFlashLightMode(flashModes[flashIndex])
                ++flashIndex
                flashIndex %= flashModes.size
            }
        })

        //Recording video size
        mCameraView!!.presetRecordingSize(480, 640)
        //        mCameraView.presetRecordingSize(720, 1280);

        //Taking picture size.
        mCameraView!!.setPictureSize(2048, 2048, true) // > 4MP
        mCameraView!!.setZOrderOnTop(false)
        mCameraView!!.setZOrderMediaOverlay(true)
        mCameraView!!.setOnCreateCallback { Log.i(LOG_TAG, "view onCreate") }
        val pauseBtn = findViewById(R.id.pauseBtn) as Button
        val resumeBtn = findViewById(R.id.resumeBtn) as Button
        pauseBtn.setOnClickListener { mCameraView!!.stopPreview() }
        resumeBtn.setOnClickListener { mCameraView!!.resumePreview() }
        val fitViewBtn = findViewById(R.id.fitViewBtn) as Button
        fitViewBtn.setOnClickListener(object : View.OnClickListener {
            var shouldFit = true
            override fun onClick(v: View) {
                shouldFit = !shouldFit
                mCameraView!!.setFitFullView(shouldFit)
            }
        })
        //NOTE!! The video's resolution/ratio isn't the same as the phone. So it zooms to fit in this case
        mCameraView!!.setFitFullView(true)
        mCameraView!!.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    Log.i(LOG_TAG, String.format("Tap to focus: %g, %g", event.x, event.y))
                    val focusX = event.x / mCameraView!!.width
                    val focusY = event.y / mCameraView!!.height
                    mCameraView!!.focusAtPoint(focusX, focusY) { success, camera ->
                        if (success) {
                            Log.e(LOG_TAG, String.format("Focus OK, pos: %g, %g", focusX, focusY))
                        } else {
                            Log.e(LOG_TAG, String.format("Focus failed, pos: %g, %g", focusX, focusY))
                            mCameraView!!.cameraInstance().setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)
                        }
                    }
                }
                else -> {
                }
            }
            true
        }
    }

    private val mFilterSwitchListener = View.OnClickListener { v ->
        val btn = v as MyButtons
        mCameraView!!.setFilterWithConfig(btn.filterConfig)
        mCurrentConfig = btn.filterConfig
    }
    var customFilterIndex = 0
    fun customFilterClicked(view: View?) {
        ++customFilterIndex
        customFilterIndex %= CGENativeLibrary.cgeGetCustomFilterNum()
        mCameraView!!.queueEvent {
            val customFilter = CGENativeLibrary.cgeCreateCustomNativeFilter(customFilterIndex, 1.0f, true)
            mCameraView!!.recorder.setNativeFilter(customFilter)
        }
    }

    fun dynamicFilterClicked(view: View?) {
        mCameraView!!.setFilterWithConfig("#unpack @dynamic mf 10 0")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_camera_demo, menu)
        return true
    }

    public override fun onDestroy() {
        super.onDestroy()
    }

    public override fun onPause() {
        super.onPause()
        CameraInstance.getInstance().stopCamera()
        Log.i(LOG_TAG, "activity onPause...")
        mCameraView!!.release(null)
        mCameraView!!.onPause()
    }

    public override fun onResume() {
        super.onResume()
        mCameraView!!.onResume()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId
        return if (id == R.id.action_settings) {
            true
        } else super.onOptionsItemSelected(item)
    }

    companion object {
        @JvmField
        var lastVideoPathFileName = FileUtil.getPath() + "/lastVideoPath.txt"
        const val LOG_TAG = CameraRecordGLSurfaceView.LOG_TAG
        var instance: CameraDemoActivity? = null
    }
}