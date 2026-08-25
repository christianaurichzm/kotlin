/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.decompiler.psi

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope

/**
 * Provides access to the Kotlin built-in declaration files (`.kotlin_builtins`) that the analysis is performed against.
 *
 * Built-ins are shipped with the analysis implementation itself rather than with the analyzed project, so a platform has to explain
 * where they live: in the IDE they are resources of the Kotlin plugin, in Standalone mode entries of a JAR on the classpath.
 *
 * A platform is expected to implement [BuiltinsVirtualFileProviderBaseImpl] instead of this class directly and register the result as
 * an application service.
 */
public abstract class BuiltinsVirtualFileProvider {
    /**
     * Returns the virtual files of all built-in declaration files.
     */
    public abstract fun getBuiltinVirtualFiles(): Set<VirtualFile>

    /**
     * Creates a search scope covering exactly the files of [getBuiltinVirtualFiles].
     *
     * The scope is not a part of any module content, as built-ins do not belong to the analyzed project.
     */
    public abstract fun createBuiltinsScope(project: Project): GlobalSearchScope

    public companion object {
        /**
         * Returns the [BuiltinsVirtualFileProvider] registered by the current platform.
         */
        public fun getInstance(): BuiltinsVirtualFileProvider = ApplicationManager.getApplication().service()
    }
}
