import android.app.AppOpsManager
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.annotation.Nullable
import androidx.annotation.RawRes
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import java.io.*


class ImageKeyboard : InputMethodService() {
    @RequiresApi(Build.VERSION_CODES.M)
    private fun isCommitContentSupported(
        @Nullable editorInfo: EditorInfo?, mimeType: String
    ): Boolean {
        if (editorInfo == null) {
            return false
        }
        val ic = currentInputConnection ?: return false
        if (!validatePackageName(editorInfo)) {
            return false
        }
        val supportedMimeTypes = EditorInfoCompat.getContentMimeTypes(editorInfo)
        println(editorInfo)
        for (supportedMimeType in supportedMimeTypes) {
            if (ClipDescription.compareMimeTypes(mimeType, supportedMimeType)) {
                return true
            }
        }
        return false
    }

    private fun doCommitContent(
        description: String, mimeType: String,
        file: File
    ) {
        val editorInfo = currentInputEditorInfo
        val contentUri: Uri = FileProvider.getUriForFile(this, AUTHORITY, file)
        val flag: Int
        if (Build.VERSION.SDK_INT >= 25) {
            // On API 25 and later devices, as an analogy of Intent.FLAG_GRANT_READ_URI_PERMISSION,
            // you can specify InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION to give
            // a temporary read access to the recipient application without exporting your content
            // provider.
            flag = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
        } else {
            // On API 24 and prior devices, we cannot rely on
            // InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION. You as an IME author
            // need to decide what access control is needed (or not needed) for content URIs that
            // you are going to expose. This sample uses Context.grantUriPermission(), but you can
            // implement your own mechanism that satisfies your own requirements.
            flag = 0
            try {
                grantUriPermission(
                    editorInfo.packageName, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.e(
                    TAG, "grantUriPermission failed packageName=" + editorInfo.packageName
                            + " contentUri=" + contentUri, e
                )
            }
        }
        val inputContentInfoCompat = InputContentInfoCompat(
            contentUri,
            ClipDescription(description, arrayOf(mimeType)),
            null
        )
        InputConnectionCompat.commitContent(
            currentInputConnection, currentInputEditorInfo, inputContentInfoCompat,
            flag, null
        )
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun validatePackageName(@Nullable editorInfo: EditorInfo?): Boolean {
        if (editorInfo == null) {
            return false
        }
        val packageName = editorInfo.packageName ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return true
        }
        val inputBinding = currentInputBinding
        if (inputBinding == null) {
            // Due to b.android.com/225029, it is possible that getCurrentInputBinding() returns
            // null even after onStartInputView() is called.
            /* TOD: Come up with a way to work around this bug....*/
            Log.e(
                TAG, "inputBinding should not be null here. "
                        + "You are likely to be hitting b.android.com/225029"
            )
            return false
        }
        val packageUid = inputBinding.uid
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val appOpsManager = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            try {
                appOpsManager.checkPackage(packageUid, packageName)
            } catch (e: Exception) {
                return false
            }
            return true
        }
        val packageManager = packageManager
        val possiblePackageNames = packageManager.getPackagesForUid(packageUid)
        for (possiblePackageName in possiblePackageNames!!) {
            if (packageName == possiblePackageName) {
                return true
            }
        }
        return false
    }

    override fun onCreate() {
        super.onCreate()
    }

    override fun onCreateInputView(): View {
        // On create code goes here
        return super.onCreateInputView()
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        // In full-screen mode the inserted content is likely to be hidden by the IME. Hence in this
        // sample we simply disable full-screen mode.
        return false
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        // Start code goes here
    }

    companion object {
        private const val TAG = "ImageKeyboard"
        private const val AUTHORITY = "Add you authoritiy here"
        private const val MIME_TYPE_PNG = "image/png"
        private fun getFileForResource(
            context: Context, @RawRes res: Int, outputDir: File,
            filename: String
        ): File? {
            val outputFile = File(outputDir, filename)
            val buffer = ByteArray(4096)
            var resourceReader: InputStream? = null
            return try {
                try {
                    resourceReader = context.getResources().openRawResource(res)
                    var dataWriter: OutputStream? = null
                    try {
                        dataWriter = FileOutputStream(outputFile)
                        while (true) {
                            val numRead: Int = resourceReader.read(buffer)
                            if (numRead <= 0) {
                                break
                            }
                            dataWriter.write(buffer, 0, numRead)
                        }
                        outputFile
                    } finally {
                        if (dataWriter != null) {
                            dataWriter.flush()
                            dataWriter.close()
                        }
                    }
                } finally {
                    if (resourceReader != null) {
                        resourceReader.close()
                    }
                }
            } catch (e: IOException) {
                null
            }
        }
    }
}