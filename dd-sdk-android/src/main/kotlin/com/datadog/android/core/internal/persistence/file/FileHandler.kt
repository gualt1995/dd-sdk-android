/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file

import java.io.File

/**
 * A class used for direct file manipulation on disk.
 */
internal interface FileHandler {

    /**
     * Writes data as a [ByteArray] into a file.
     * @param file the file to write to
     * @param data the data to write
     * @param append whether to append data at the end of the file or overwrite
     * @param separator an optional [ByteArray] used when appending to a non empty file
     * @return whether the write operation was successful
     */
    fun writeData(
        file: File,
        data: ByteArray,
        append: Boolean,
        separator: ByteArray?
    ): Boolean

    /**
     * Reads data from the given file.
     *  @param file the file to read from
     *  @param prefix an (optional) prefix to embed before the file content
     *  @param suffix an (optional) suffix to embed after the file content
     *  @param separator an (optional) separator used to write the data in append mode, is needed
     *  only in the case of using encryption for data storage
     *  @return the [ByteArray] data or an empty array if the file can't be read (e.g.: exception)
     */
    fun readData(
        file: File,
        prefix: ByteArray?,
        suffix: ByteArray?,
        separator: ByteArray?
    ): ByteArray

    /**
     * Deletes the file or directory (recursively if needed).
     * @param target the target [File] to delete
     * @return whether the delete was successful
     */
    fun delete(target: File): Boolean

    /**
     * Move the children files from `srcDir` to the `destDir`.
     */
    fun moveFiles(srcDir: File, destDir: File): Boolean
}
