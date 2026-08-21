/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.declarations

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.ConstantValueKind

data class FirCompilerRequiredParameterDescription(
    val name: Name,
    val annotationClassId: ClassId,
    val kind: FirCraParameterKind,
    val position: Int?,
) {
    init {
        require((position != null) xor (kind is FirCraParameterKind.EnumParameter && kind.isVararg))
    }
}

sealed class FirCraParameterKind {
    data class LiteralParameter(val constKind: ConstantValueKind) : FirCraParameterKind()
    data class EnumParameter(val enumClassId: ClassId, val isVararg: Boolean) : FirCraParameterKind()
}
