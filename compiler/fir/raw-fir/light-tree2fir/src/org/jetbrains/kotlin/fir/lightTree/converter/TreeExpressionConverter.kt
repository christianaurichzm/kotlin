/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.lightTree.converter

import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.isStatementLikeExpression

interface TreeExpressionConverter<Node : Any> {
    fun getAsFirStatement(node: Node, errorReason: String = ""): FirStatement

    fun <R : FirExpression> getAsFirExpression(
        expression: Node,
        errorReason: String = "",
        isValidExpression: (R) -> Boolean = { !it.isStatementLikeExpression },
    ): R

    fun <R : FirExpression> getAsFirExpression(
        expression: Node?,
        errorReason: String = "",
        sourceWhenInvalidExpression: Node,
        isValidExpression: (R) -> Boolean = { !it.isStatementLikeExpression },
    ): R

    fun convertValueArguments(valueArguments: Node): List<FirExpression>
}
