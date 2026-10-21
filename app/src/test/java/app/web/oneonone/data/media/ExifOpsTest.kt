package app.web.oneonone.data.media

import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Test

class ExifOpsTest {
    @Test fun mapsOrientationsToRotateThenMirror() {
        assertEquals(0 to false, exifOps(ExifInterface.ORIENTATION_NORMAL))
        assertEquals(180 to false, exifOps(ExifInterface.ORIENTATION_ROTATE_180))
        assertEquals(90 to false, exifOps(ExifInterface.ORIENTATION_ROTATE_90))
        assertEquals(270 to false, exifOps(ExifInterface.ORIENTATION_ROTATE_270))
        assertEquals(0 to true, exifOps(ExifInterface.ORIENTATION_FLIP_HORIZONTAL))
        assertEquals(180 to true, exifOps(ExifInterface.ORIENTATION_FLIP_VERTICAL))
        assertEquals(90 to true, exifOps(ExifInterface.ORIENTATION_TRANSPOSE))
        assertEquals(270 to true, exifOps(ExifInterface.ORIENTATION_TRANSVERSE))
        assertEquals(0 to false, exifOps(0))
    }
}
