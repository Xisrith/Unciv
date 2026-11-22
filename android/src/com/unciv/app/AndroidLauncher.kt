package com.unciv.app

import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.work.WorkManager
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidGraphics
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.unciv.logic.IdChecker
import com.unciv.logic.files.SAVE_FILES_FOLDER
import com.unciv.logic.files.UncivFiles
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.gamepad.GamepadState
import com.unciv.ui.screens.multiplayerscreens.AddFriendScreen
import com.unciv.utils.Concurrency.runOnGLThread
import com.unciv.utils.Dispatcher
import com.unciv.utils.Display
import com.unciv.utils.Log
import com.unciv.utils.launchOnGLThread
import java.io.File
import java.lang.Exception
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

open class AndroidLauncher : AndroidApplication() {

    private var game: AndroidGame? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup Android logging
        Log.backend = AndroidLogBackend(this)

        // Setup Android display
        val displayImpl = AndroidDisplay(this)
        Display.platform = displayImpl

        // Setup Android fonts
        Fonts.fontImplementation = AndroidFont()

        // Setup Android custom saver-loader
        UncivFiles.saverLoader = AndroidSaverLoader(this)
        UncivFiles.preferExternalStorage = true

        val settings = UncivFiles.getSettingsForPlatformLaunchers(filesDir.path)
        val config = AndroidApplicationConfiguration().apply { useImmersiveMode = settings.androidHideSystemUi }

        // Setup orientation
        displayImpl.setOrientation(settings.displayOrientation)

        // Create notification channels for Multiplayer notificator
        MultiplayerTurnCheckWorker.createNotificationChannels(applicationContext)

        CoroutineScope(Dispatchers.IO).launch {
            createSaveFolder()
            copyMods()
        }

        game = AndroidGame(this)
        initialize(game, config)

        // Setup display cutout AFTER libGDX initialize() — libGDX window setup resets layoutInDisplayCutoutMode
        displayImpl.setCutoutFromUiThread(settings.androidCutout)

        ViewCompat.setOnApplyWindowInsetsListener(window.decorView, ::insetsListener)
        // Force immediate insets dispatch — libGDX init triggers insets before listener is registered
        window.decorView.requestApplyInsets()

        // can be triggered via `adb shell am start -a android.intent.action.VIEW -d https://unciv.app/g/G-ef0f5e5a-f1db-4a54-9d94-92ca986afe8a-9 com.unciv.app`
        // or whatever your game id is
        game!!.setDeepLinkedGame(intent)
        game!!.addScreenObscuredListener()
        processPossibleFriendDeepLink(intent)

        GamepadState.controllerMode = hasGamepad()
    }

    // region Gamepad

    private fun hasGamepad(): Boolean = InputDevice.getDeviceIds().any { id ->
        val sources = InputDevice.getDevice(id)?.sources ?: 0
        (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
    }

    /** Gamepad buttons are delivered to libGDX as normal key events. We always report them as handled
     *  so Android doesn't additionally generate its "fallback" keys (B -> Back, A -> DPAD_CENTER, ...). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handled = super.dispatchKeyEvent(event)
        if (KeyEvent.isGamepadButton(event.keyCode)) {
            GamepadState.controllerMode = true
            return true
        }
        return handled
    }

    private var lastHatX = 0
    private var lastHatY = 0

    /** Analog sticks and triggers: libGDX core ignores them, so we store them in [GamepadState] and
     *  consume the event - that also stops Android from synthesizing coarse D-pad presses from the stick. */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val isJoystick = (event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        if (!isJoystick || event.action != MotionEvent.ACTION_MOVE)
            return super.dispatchGenericMotionEvent(event)

        GamepadState.controllerMode = true
        GamepadState.leftX = event.getAxisValue(MotionEvent.AXIS_X)
        GamepadState.leftY = event.getAxisValue(MotionEvent.AXIS_Y)
        GamepadState.rightX = event.getAxisValue(MotionEvent.AXIS_Z)
        GamepadState.rightY = event.getAxisValue(MotionEvent.AXIS_RZ)
        GamepadState.leftTrigger = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
        GamepadState.rightTrigger = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))

        // Some controllers report the D-pad as a "hat" axis instead of key events - translate it to D-pad keys
        val hatX = Math.round(event.getAxisValue(MotionEvent.AXIS_HAT_X))
        val hatY = Math.round(event.getAxisValue(MotionEvent.AXIS_HAT_Y))
        if (hatX != lastHatX) {
            if (lastHatX != 0) sendKey(KeyEvent.ACTION_UP, if (lastHatX < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
            if (hatX != 0) sendKey(KeyEvent.ACTION_DOWN, if (hatX < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
            lastHatX = hatX
        }
        if (hatY != lastHatY) {
            if (lastHatY != 0) sendKey(KeyEvent.ACTION_UP, if (lastHatY < 0) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN)
            if (hatY != 0) sendKey(KeyEvent.ACTION_DOWN, if (hatY < 0) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN)
            lastHatY = hatY
        }

        Gdx.graphics?.requestRendering()
        return true
    }

    private fun sendKey(action: Int, keyCode: Int) {
        val view = (Gdx.graphics as? AndroidGraphics)?.view ?: return
        (Gdx.input as? View.OnKeyListener)?.onKey(view, keyCode, KeyEvent(action, keyCode))
    }

    // endregion

    private fun insetsListener(view: View, insets: WindowInsetsCompat): WindowInsetsCompat {
        val settings = try {
            game!!.settings // settings is a lateinit, and this listener *will* be called before it's done
        } catch (_: Throwable) {
            UncivFiles.getSettingsForPlatformLaunchers(filesDir.path)
        }

        // If settings.androidCutout is false, padding is applied
        if (!settings.androidCutout) {
            val cutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            view.setPadding(cutoutInsets.left, cutoutInsets.top, cutoutInsets.right, cutoutInsets.bottom)
        } else {
            view.setPadding(0, 0, 0, 0)
        }

        return insets
    }

    /**
     * Copies mods from external data directory (where users can access) to the private one (where
     * libGDX reads from). Note: deletes all files currently in the private mod directory and
     * replaces them with the ones in the external folder!)
     */
    private fun copyMods() {
        // Mod directory in the internal app data (where Gdx.files.local looks)
        val internalModsDir = File("${filesDir.path}/mods")

        // Mod directory in the shared app data (where the user can see and modify)
        val externalPath = getExternalFilesDir(null)?.path ?: return
        val externalModsDir = File("$externalPath/mods")

        try { // Rarely we get a kotlin.io.AccessDeniedException, if so - no biggie
            // Copy external mod directory (with data user put in it) to internal (where it can be read)
            if (!externalModsDir.exists()) externalModsDir.mkdirs() // this can fail sometimes, which is why we check if it exists again in the next line
            if (externalModsDir.exists()) externalModsDir.copyRecursively(internalModsDir, true)
        } catch (ex: Exception) {}
    }

    // Blind attempt to prevent issues like #9604, #9847, #10302, #13113, #15029, #15219
    private fun createSaveFolder() {
        val parent = getExternalFilesDir(null) ?: filesDir
        val newFolder = File(parent, SAVE_FILES_FOLDER)
        if (!newFolder.mkdirs()) return
        // Force MediaStore update
        MediaScannerConnection.scanFile(context, arrayOf(newFolder.absolutePath), null, null)
    }

    override fun onPause() {
        val game = this.game!!
        if (game.isInitializedProxy()
                && game.gameInfo != null
                && game.settings.multiplayer.turnCheckerEnabled
                && game.files.getMultiplayerSaves().any()
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                MultiplayerTurnCheckWorker.startTurnChecker(
                    applicationContext,
                    game.files,
                    game.gameInfo!!,
                    game.settings.multiplayer
                )
            }
        }
        super.onPause()
    }

    override fun onResume() {
        try {
            WorkManager.getInstance(applicationContext).cancelAllWorkByTag(MultiplayerTurnCheckWorker.WORK_TAG)
            with(NotificationManagerCompat.from(this)) {
                cancel(MultiplayerTurnCheckWorker.NOTIFICATION_ID_INFO)
                cancel(MultiplayerTurnCheckWorker.NOTIFICATION_ID_SERVICE)
            }
        } catch (ignore: Exception) {
            /* Sometimes this fails for no apparent reason - the multiplayer checker failing to
               cancel should not be enough of a reason for the game to crash! */
        }
        super.onResume()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null)
            return
        game?.setDeepLinkedGame(intent)
        processPossibleFriendDeepLink(intent)
    }
   
    private fun processPossibleFriendDeepLink(intent: Intent) {
        // can be triggered via
        // `adb shell am start -a android.intent.action.VIEW -d https://unciv.app/p/P-63971008-a533-47f6-ad6a-57b616626138-9?name=Yairm210 com.unciv.app`
        // or whatever your friend id and name are
        if (intent.data != null && IdChecker.isFriendDeepLink(intent.data.toString())) {
            val newFriend = IdChecker.checkAndReturnPlayerUuid(intent.data.toString())
            if (newFriend != null) runOnGLThread {
                game!!.pushScreen { AddFriendScreen(newFriend.name, newFriend.playerID) }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val saverLoader = UncivFiles.saverLoader as AndroidSaverLoader
        saverLoader.onActivityResult(requestCode, data)
        super.onActivityResult(requestCode, resultCode, data)
    }
}
