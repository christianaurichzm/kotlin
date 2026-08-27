/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.lightTree.converter

import com.intellij.lang.LighterASTNode
import com.intellij.openapi.util.Ref
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.util.diff.FlyweightCapableTreeStructure
import org.jetbrains.kotlin.*
import org.jetbrains.kotlin.ElementTypeUtils.isExpression
import org.jetbrains.kotlin.KtNodeTypes.BOOLEAN_CONSTANT
import org.jetbrains.kotlin.KtNodeTypes.NULL
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.builder.Context
import org.jetbrains.kotlin.kmp.utils.kmpId
import org.jetbrains.kotlin.lexer.KtTokens.*
import org.jetbrains.kotlin.lexer.KtTokens.VARARG_KEYWORD

abstract class AbstractLightTreeRawFirBuilder(
    baseSession: FirSession,
    val tree: FlyweightCapableTreeStructure<LighterASTNode>,
    context: Context<LighterASTNode> = Context()
) : AbstractTreeRawFirBuilder<LighterASTNode, IElementType>(baseSession, context) {
    companion object {
        protected val ignoredTokens: TokenSet = TokenSet.orSet(
            COMMENTS,
            TokenSet.create(WHITE_SPACE, SEMICOLON, TokenType.ERROR_ELEMENT, TokenType.BAD_CHARACTER),
        )
    }

    override fun LighterASTNode.toFirSourceElement(kind: KtFakeSourceElementKind?): KtLightSourceElement {
        val startOffset = tree.getStartOffset(this)
        val endOffset = tree.getEndOffset(this)
        return toKtLightSourceElement(tree, kind ?: KtRealSourceElementKind, startOffset, endOffset)
    }

    override val LighterASTNode.elementType: IElementType
        get() = this.tokenType

    override fun IElementType.toTokenId(): Int = kmpId()

    override fun IElementType.isPlus(): Boolean = this == PLUS
    override fun IElementType.isMinus(): Boolean = this == MINUS

    override fun IElementType.isIntegerConstant(): Boolean = this == INTEGER_LITERAL
    override fun IElementType.isFloatConstant(): Boolean = this == FLOAT_LITERAL
    override fun IElementType.isBooleanConstant(): Boolean = this == BOOLEAN_CONSTANT
    override fun IElementType.isCharacterConstant(): Boolean = this == CHARACTER_LITERAL
    override fun IElementType.isNullConstant(): Boolean = this == NULL

    override fun IElementType.isStringInterpolationPrefix(): Boolean = this == KtNodeTypes.STRING_INTERPOLATION_PREFIX
    override fun IElementType.isOpenQuote(): Boolean = this == OPEN_QUOTE
    override fun IElementType.isClosingQuote(): Boolean = this == CLOSING_QUOTE
    override fun IElementType.isLiteralStringTemplateEntry(): Boolean = this == KtNodeTypes.LITERAL_STRING_TEMPLATE_ENTRY
    override fun IElementType.isEscapeStringTemplateEntry(): Boolean = this == KtNodeTypes.ESCAPE_STRING_TEMPLATE_ENTRY
    override fun IElementType.isShortStringTemplateEntry(): Boolean = this == KtNodeTypes.SHORT_STRING_TEMPLATE_ENTRY
    override fun IElementType.isLongStringTemplateEntry(): Boolean = this == KtNodeTypes.LONG_STRING_TEMPLATE_ENTRY

    override fun IElementType.isArrayAccessExpression(): Boolean = this == KtNodeTypes.ARRAY_ACCESS_EXPRESSION
    override fun IElementType.isSafeAccessExpression(): Boolean = this == KtNodeTypes.SAFE_ACCESS_EXPRESSION
    override fun IElementType.isParenthesized(): Boolean = this == KtNodeTypes.PARENTHESIZED
    override fun IElementType.isLabeledExpression(): Boolean = this == KtNodeTypes.LABELED_EXPRESSION
    override fun IElementType.isAnnotatedExpression(): Boolean = this == KtNodeTypes.ANNOTATED_EXPRESSION

    override val LighterASTNode.asText: String
        get() = this.toString()

    override fun LighterASTNode.getLabelName(): String? {
        if (tokenType == KtNodeTypes.FUN) {
            return getParent()?.getLabelName()
        }
        this.forEachChildren {
            when (it.tokenType) {
                KtNodeTypes.LABEL_QUALIFIER -> return it.asText.replaceFirst("@", "").let(::unquoteIdentifier)
            }
        }

        return null
    }

    override fun LighterASTNode.getChildren(): List<LighterASTNode> {
        val children = Ref<Array<LighterASTNode?>>()
        val count = tree.getChildren(this, children)
        @Suppress("UNCHECKED_CAST")
        return if (count > 0) children.get().take(count) as List<LighterASTNode> else emptyList()
    }

    fun LighterASTNode.getChildExpression(): LighterASTNode? = getFirstChildExpression()

    override fun LighterASTNode.getFirstChildExpression(): LighterASTNode? {
        return getChildrenAsArray().firstOrNull { it?.isExpression() == true }
    }

    protected fun LighterASTNode.getFirstChildExpressionUnwrapped(): LighterASTNode? {
        val expression = getFirstChildExpression() ?: return null
        return if (expression.tokenType == KtNodeTypes.PARENTHESIZED) {
            expression.getFirstChildExpressionUnwrapped()
        } else {
            expression
        }
    }

    override fun LighterASTNode.getLastChildExpression(): LighterASTNode? {
        var result: LighterASTNode? = null
        forEachChildren {
            if (it.isExpression()) {
                result = it
            }
        }

        return result
    }

    override fun LighterASTNode.getChildNodeByType(type: IElementType): LighterASTNode? {
        return getChildrenAsArray().firstOrNull { it?.tokenType == type }
    }

    override val LighterASTNode?.receiverExpression: LighterASTNode?
        get() {
            var candidate: LighterASTNode? = null
            this?.forEachChildren {
                when (it.tokenType) {
                    DOT, SAFE_ACCESS -> return if (candidate?.elementType != TokenType.ERROR_ELEMENT) candidate else null
                    else -> candidate = it
                }
            }
            return null
        }

    override val LighterASTNode?.selectorExpression: LighterASTNode?
        get() {
            var isSelector = false
            this?.forEachChildren {
                when (it.tokenType) {
                    DOT, SAFE_ACCESS -> isSelector = true
                    else -> if (isSelector) return if (it.elementType != TokenType.ERROR_ELEMENT) it else null
                }
            }
            return null
        }

    override val LighterASTNode?.indexExpressions: List<LighterASTNode>?
        get() = this?.getLastChildExpression()?.getChildrenAsArray()?.filterNotNull()?.filter { it.isExpression() }

    override fun LighterASTNode.getModifierList(): LighterASTNode? = getChildNodeByType(KtNodeTypes.MODIFIER_LIST)

    override fun LighterASTNode.getVarargKeyword(): LighterASTNode? = getChildNodeByType(VARARG_KEYWORD)

    override fun LighterASTNode.getParent(): LighterASTNode? {
        return tree.getParent(this)
    }

    fun LighterASTNode?.getChildNodesByType(type: IElementType): List<LighterASTNode> {
        return this?.forEachChildrenReturnList { node, container ->
            when (node.tokenType) {
                type -> container += node
            }
        } ?: emptyList()
    }

    fun LighterASTNode?.getChildrenAsArray(): Array<out LighterASTNode?> {
        if (this == null) return arrayOf()

        val kidsRef = Ref<Array<LighterASTNode?>>()
        tree.getChildren(this, kidsRef)
        return kidsRef.get()
    }

    fun LighterASTNode?.getFirstChild(): LighterASTNode? {
        return getChildrenAsArray().firstOrNull()
    }

    override fun LighterASTNode.forEachChildren(f: (LighterASTNode) -> Unit) {
        val kidsArray = this.getChildrenAsArray()
        for (kid in kidsArray) {
            if (kid == null) break
            if (ignoredTokens.contains(kid.tokenType)) continue
            f(kid)
        }
    }

    override fun <T> LighterASTNode.forEachChildrenReturnList(f: (LighterASTNode, MutableList<T>) -> Unit): MutableList<T> {
        val kidsArray = this.getChildrenAsArray()

        val container = mutableListOf<T>()
        for (kid in kidsArray) {
            if (kid == null) break
            if (ignoredTokens.contains(kid.tokenType)) continue
            f(kid, container)
        }

        return container
    }
}
