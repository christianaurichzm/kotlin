/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.references.impl

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.FirImplementationDetail
import org.jetbrains.kotlin.fir.symbols.impl.FirEnumEntrySymbol
import org.jetbrains.kotlin.name.Name

class FirSimpleNamedReferenceForCompilerRequiredEnumInAnnotation @FirImplementationDetail constructor(
    source: KtSourceElement?,
    name: Name,
    val symbol: FirEnumEntrySymbol,
) : FirSimpleNamedReference(source, name)
