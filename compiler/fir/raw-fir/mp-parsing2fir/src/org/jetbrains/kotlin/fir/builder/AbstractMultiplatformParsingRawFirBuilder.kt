/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.builder

import org.jetbrains.kotlin.fir.FirSession

abstract class AbstractMultiplatformParsingRawFirBuilder(
    baseSession: FirSession,
    val tree: KotlinLightTreeStructure,
    context: Context<KotlinLightAstNode> = Context()
) : AbstractRawFirBuilder<KotlinLightAstNode>(baseSession, context) {
}
