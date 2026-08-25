/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.builder

import org.jetbrains.kotlin.KtSourceFile
import org.jetbrains.kotlin.KtSourceFileLinesMapping
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.scopes.FirScopeProvider
import org.jetbrains.kotlin.kmp.tree.LightNode

class MultiplatformParsingRawFirDeclarationBuilder(
    session: FirSession,
    internal val baseScopeProvider: FirScopeProvider,
    tree: KotlinLightTreeStructure,
    context: Context<KotlinLightAstNode> = Context(),
) : AbstractMultiplatformParsingRawFirBuilder(session, tree, context) {
    fun convertFile(file: LightNode, sourceFile: KtSourceFile, linesMapping: KtSourceFileLinesMapping): FirFile {


    }
}
