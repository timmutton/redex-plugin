/*
 * Copyright (C) 2015-2016 KeepSafe Software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.com.timmutton.redexplugin

import com.android.dexdeps.DexData
import java.io.File
import java.io.RandomAccessFile
import java.util.*
import java.util.Collections.emptyList
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * A physical file and the {@link DexData} contained therein.
 *
 * A DexFile contains an open file, possibly a temp file.  When consumers are
 * finished with the DexFile, it should be cleaned up with
 * {@link DexFile#dispose()}.
 */
class DexFile(val file: File, val isTemp: Boolean, val isInstantRun: Boolean = false) {
    val data: DexData
    val raf: RandomAccessFile = RandomAccessFile(file, "r")

    init {
        data = DexData(raf)
        data.load()
    }

    fun dispose() {
        raf.close()
        if (isTemp) {
            file.delete()
        }
    }

    companion object {
        /**
         * Extracts a list of {@link DexFile} instances from the given file.
         *
         * DexFiles can be extracted either from an Android APK file, or from a raw
         * {@code classes.dex} file.
         *
         * @param file the APK or dex file.
         * @return a list of DexFile objects representing data in the given file.
         */
        fun extractDexData(file: File?): List<DexFile> {
            if (file == null || !file.exists()) {
                return emptyList()
            }

            try {
                return extractDexFromZip(file)
            } catch (e: ZipException) {
                // not a zip, no problem
            }

            return listOf(DexFile(file, false))
        }

        /**
         * Attempts to unzip the file and extract all dex files inside of it.
         *
         * It is assumed that {@code file} is an APK file resulting from an Android
         * build, containing one or more appropriately-named classes.dex files.
         *
         * @param file the APK file from which to extract dex data.
         * @return a list of contained dex files.
         * @throws ZipException if {@code file} is not a zip file.
         */
        fun extractDexFromZip(file: File): List<DexFile> = ZipFile(file).use { zipfile ->
            val entries = zipfile.entries().toList()

            val mainDexFiles = entries.filter { it.name.matches(Regex("classes.*\\.dex")) }.map { entry ->
                val temp = File.createTempFile("dexcount", ".dex")
                temp.deleteOnExit()

                zipfile.getInputStream(entry).use { input ->
                    IOUtil.drainToFile(input, temp)
                }

                DexFile(temp, true)
            }.toMutableList()

            mainDexFiles.addAll(extractIncrementalDexFiles(zipfile, entries))

            return mainDexFiles
        }

        /**
         * Attempts to extract dex files embedded in a nested instant-run.zip file
         * produced by Android Studio 2.0.  If present, such files are extracted to
         * temporary files on disk and returned as a list.  If not, an empty mutable
         * list is returned.
         *
         * @param apk the APK file from which to extract dex data.
         * @param zipEntries a list of ZipEntry objects inside of the APK.
         * @return a list, possibly empty, of instant-run dex data.
         */
        fun extractIncrementalDexFiles(apk: ZipFile, zipEntries: List<ZipEntry>): List<DexFile> {
            val incremental = zipEntries.filter { (it.name == "instant-run.zip") }
            if (incremental.size != 1) {
                return emptyList()
            }

            val instantRunFile = File.createTempFile("instant-run", ".zip")
            instantRunFile.deleteOnExit()

            apk.getInputStream(incremental[0]).use { input ->
                IOUtil.drainToFile(input, instantRunFile)
            }

            ZipFile(instantRunFile).use { instantRunZip ->
                val entries = Collections.list(instantRunZip.entries())
                val dexEntries = entries.filter { it.name.endsWith(".dex") }

                return dexEntries.map { entry ->
                    val temp = File.createTempFile("dexcount", ".dex")
                    temp.deleteOnExit()

                    instantRunZip.getInputStream(entry).use { input ->
                        IOUtil.drainToFile(input, temp)
                    }

                    DexFile(temp, true, true)
                }
            }
        }
    }
}
