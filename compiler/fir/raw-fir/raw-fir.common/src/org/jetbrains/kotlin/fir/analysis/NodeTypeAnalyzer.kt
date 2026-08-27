/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.FirModuleData
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirErrorProperty
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.builder.FirRegularClassBuilder
import org.jetbrains.kotlin.fir.declarations.builder.FirValueParameterBuilder
import org.jetbrains.kotlin.fir.declarations.builder.buildErrorProperty
import org.jetbrains.kotlin.fir.diagnostics.ConeDestructuringDeclarationsOnTopLevel
import org.jetbrains.kotlin.fir.diagnostics.ConeSyntaxDiagnostic
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildErrorExpression
import org.jetbrains.kotlin.fir.symbols.impl.FirErrorPropertySymbol
import org.jetbrains.kotlin.fir.types.FirImplicitTypeRef
import org.jetbrains.kotlin.fir.types.impl.FirImplicitBuiltinTypeRef
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

interface NodeTypeAnalyzer<Node : Any, Type : Any> {
    val implicitType: FirImplicitTypeRef
    val implicitUnitType: FirImplicitBuiltinTypeRef
    val implicitAnyType: FirImplicitBuiltinTypeRef
    val implicitEnumType: FirImplicitBuiltinTypeRef
    val implicitAnnotationType: FirImplicitBuiltinTypeRef

    fun Node.toFirSourceElement(kind: KtFakeSourceElementKind? = null): KtSourceElement
    val Node.elementType: Type
    val Node.asText: String
    fun Node.getReferencedNameAsName(): Name
    fun Node.getChildNodeByType(type: Type): Node?
    fun Node.getChildren(): List<Node> =
        throw UnsupportedOperationException("Not supported in PsiRawFirBuilder")

    fun Node.getParent(): Node? =
        throw UnsupportedOperationException("Not supported in PsiRawFirBuilder")

    fun Node.getChildNodeByTokenId(tokenId: Int): Node? {
        var result: Node? = null
        forEachChildren { node ->
            if (result != null) return@forEachChildren
            when (node.elementType.toTokenId()) {
                tokenId -> result = node
            }
        }
        return result
    }

    fun Node?.getChildNodesByTokenId(tokenId: Int): List<Node> {
        return this?.forEachChildrenReturnList { node, container ->
            when (node.elementType.toTokenId()) {
                tokenId -> container += node
            }
        } ?: emptyList()
    }

    fun Node.getOperationTokenId(): Int {
        return getChildren().first().elementType.toTokenId()
    }

    fun isCallableLocal(callableNode: Node, getParent: Node.() -> Node?): Boolean {
        val parentNode = callableNode.getParent()
        return when (parentNode?.elementType?.toTokenId()) {
            KtNodeTypes.KT_FILE_ID, KtNodeTypes.CLASS_BODY_ID -> false
            KtNodeTypes.BLOCK_ID -> when (parentNode.getParent()?.elementType?.toTokenId()) {
                KtNodeTypes.SCRIPT_ID -> false
                else -> true
            }
            else -> true
        }
    }

    fun Node.forEachChildren(f: (Node) -> Unit) {}
    fun <T> Node.forEachChildrenReturnList(f: (Node, MutableList<T>) -> Unit): MutableList<T> {
        return mutableListOf()
    }

    fun generateDataClassMembers(
        source: Node,
        classBuilder: FirRegularClassBuilder,
        firPrimaryConstructor: FirConstructor,
        zippedParameters: List<Pair<Node, FirProperty>>,
        packageFqName: FqName,
        classFqName: FqName,
        addValueParameterAnnotations: FirValueParameterBuilder.(Node) -> Unit,
    )

    fun buildErrorNonLocalDestructuringDeclaration(
        source: KtSourceElement,
        initializer: FirExpression?,
        baseModuleData: FirModuleData,
    ): FirErrorProperty = buildErrorProperty {
        this.source = source
        moduleData = baseModuleData
        origin = FirDeclarationOrigin.Source
        name = Name.special("<destructuring>")
        diagnostic = ConeDestructuringDeclarationsOnTopLevel
        symbol = FirErrorPropertySymbol(diagnostic)
        this.initializer = initializer ?: buildErrorExpression {
            this.source = source
            diagnostic = ConeSyntaxDiagnostic("Initializer required for destructuring declaration")
        }
    }

    fun Type.toTokenId(): Int

    fun Type.isPlus(): Boolean
    fun Type.isMinus(): Boolean

    fun Type.isIntegerConstant(): Boolean
    fun Type.isFloatConstant(): Boolean
    fun Type.isCharacterConstant(): Boolean
    fun Type.isBooleanConstant(): Boolean
    fun Type.isNullConstant(): Boolean

    fun Type.isStringInterpolationPrefix(): Boolean
    fun Type.isOpenQuote(): Boolean
    fun Type.isClosingQuote(): Boolean
    fun Type.isLiteralStringTemplateEntry(): Boolean
    fun Type.isEscapeStringTemplateEntry(): Boolean
    fun Type.isShortStringTemplateEntry(): Boolean
    fun Type.isLongStringTemplateEntry(): Boolean

    fun Type.isArrayAccessExpression(): Boolean
    fun Type.isSafeAccessExpression(): Boolean
    fun Type.isParenthesized(): Boolean
    fun Type.isLabeledExpression(): Boolean
    fun Type.isAnnotatedExpression(): Boolean
}
