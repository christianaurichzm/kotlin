/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.builder

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.lexer.performLexing
import com.intellij.platform.syntax.parser.SyntaxTreeBuilderFactory
import com.intellij.platform.syntax.parser.prepareProduction
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.scopes.FirScopeProvider
import org.jetbrains.kotlin.kmp.lexer.KotlinLexer
import org.jetbrains.kotlin.kmp.parser.AbstractParser
import org.jetbrains.kotlin.kmp.parser.KotlinParser
import org.jetbrains.kotlin.kmp.tree.MultiplatformBasedLightTree
import org.jetbrains.kotlin.kmp.tree.buildLanguageSpecificLightTree
import org.jetbrains.kotlin.readSourceFileWithMapping
import java.io.File
import java.nio.file.Path

class MultiplatformParsing2Fir(
    val session: FirSession,
    val scopeProvider: FirScopeProvider,
) {
    fun isScript(fileName: String): Boolean = FileUtilRt.getExtension(fileName) == "kts"

    fun buildFirFile(path: Path): FirFile {
        return buildFirFile(path.toFile())
    }

    fun buildFirFile(file: File): FirFile {
        val parser = KotlinParser(isScript(file.name), isLazy = false)
        val [text, linesMapping] = file.inputStream().reader(Charsets.UTF_8).use {
            it.readSourceFileWithMapping()
        }
        val lightTree = parseToTestParseElement(text, 0, KotlinLexer(), parser)

    }

    private fun parseToTestParseElement(
        charSequence: CharSequence,
        start: Int,
        lexer: Lexer,
        parser: AbstractParser,
    ): MultiplatformBasedLightTree {
        val syntaxTreeBuilder = SyntaxTreeBuilderFactory.builder(
            charSequence,
            performLexing(charSequence, lexer, cancellationProvider = null, logger = null),
            whitespaces = parser.whitespaces,
            comments = parser.comments,
        ).withStartOffset(start)
            .withWhitespaceOrCommentBindingPolicy(parser.whitespaceOrCommentBindingPolicy)
            .build()

        parser.parse(syntaxTreeBuilder)
        return buildLanguageSpecificLightTree(syntaxTreeBuilder, charSequence) {
            KotlinLightTreeStructure(it)
        }
    }

}
