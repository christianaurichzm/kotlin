/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.KtSourceFile
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirImplementationDetail
import org.jetbrains.kotlin.fir.FirLabel
import org.jetbrains.kotlin.fir.FirLoopTarget
import org.jetbrains.kotlin.fir.FirModuleData
import org.jetbrains.kotlin.fir.builder.AbstractRawFirBuilder.ForbiddenLabelKind
import org.jetbrains.kotlin.fir.builder.DestructuringKind
import org.jetbrains.kotlin.fir.builder.escapedStringToCharacter
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirDeclarationStatus
import org.jetbrains.kotlin.fir.declarations.FirErrorProperty
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirTypeParameter
import org.jetbrains.kotlin.fir.declarations.FirTypeParameterRef
import org.jetbrains.kotlin.fir.declarations.FirVariable
import org.jetbrains.kotlin.fir.declarations.builder.FirAnonymousObjectBuilder
import org.jetbrains.kotlin.fir.declarations.builder.FirErrorPrimaryConstructorBuilder
import org.jetbrains.kotlin.fir.declarations.builder.FirFileBuilder
import org.jetbrains.kotlin.fir.declarations.builder.FirRegularClassBuilder
import org.jetbrains.kotlin.fir.declarations.builder.FirValueParameterBuilder
import org.jetbrains.kotlin.fir.declarations.builder.buildConstructedClassTypeParameterRef
import org.jetbrains.kotlin.fir.declarations.builder.buildErrorProperty
import org.jetbrains.kotlin.fir.declarations.destructuringDeclarationContainerVariable
import org.jetbrains.kotlin.fir.declarations.utils.isCompanion
import org.jetbrains.kotlin.fir.diagnostics.ConeDestructuringDeclarationsOnTopLevel
import org.jetbrains.kotlin.fir.diagnostics.ConeDiagnostic
import org.jetbrains.kotlin.fir.diagnostics.ConeMultipleLabelsAreForbidden
import org.jetbrains.kotlin.fir.diagnostics.ConeSimpleDiagnostic
import org.jetbrains.kotlin.fir.diagnostics.ConeSyntaxDiagnostic
import org.jetbrains.kotlin.fir.diagnostics.ConeUnderscoreIsReserved
import org.jetbrains.kotlin.fir.diagnostics.DiagnosticKind
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirErrorExpression
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirImplicitInvokeCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirLoop
import org.jetbrains.kotlin.fir.expressions.FirOperation
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.buildConstOrErrorExpression
import org.jetbrains.kotlin.fir.expressions.builder.FirLoopBuilder
import org.jetbrains.kotlin.fir.expressions.builder.FirLoopJumpBuilder
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildErrorExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildImplicitInvokeCall
import org.jetbrains.kotlin.fir.expressions.builder.buildLiteralExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildStringConcatenationCall
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirErrorPropertySymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.FirErrorTypeRef
import org.jetbrains.kotlin.fir.types.FirImplicitTypeRef
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildErrorTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.impl.FirImplicitBuiltinTypeRef
import org.jetbrains.kotlin.kmp.lexer.KtTokens
import org.jetbrains.kotlin.kmp.parser.KtNodeTypes
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.types.ConstantValueKind
import kotlin.collections.plusAssign

interface NodeTypeAnalyzer<Node : Any, Type : Any> {
    val implicitType: FirImplicitTypeRef
    val implicitUnitType: FirImplicitBuiltinTypeRef
    val implicitAnyType: FirImplicitBuiltinTypeRef
    val implicitEnumType: FirImplicitBuiltinTypeRef
    val implicitAnnotationType: FirImplicitBuiltinTypeRef

    fun Node.toFirSourceElement(kind: KtFakeSourceElementKind? = null): KtSourceElement
    fun KtSourceElement.toNode(): Node
    val Node.elementType: Type
    val Node.asText: String
    fun Node.getAsStringWithoutBacktick(): String {
        return this.asText.replace("`", "")
    }

    fun Node.getLabelName(): String?
    fun Node.getReferencedNameAsName(): Name
    fun Node.getChildNodeByType(type: Type): Node?
    fun Node.getChildren(): List<Node> =
        throw UnsupportedOperationException("Not supported in PsiRawFirBuilder")

    fun Node.getFirstChild(): Node? = getChildren().firstOrNull()

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

    fun callableIdForName(name: Name): CallableId
    fun destructuringKindOf(hasSquareBrackets: Boolean, isFullForm: Boolean): DestructuringKind

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

    fun Node.toDelegatedSelfType(firClass: FirRegularClassBuilder): FirResolvedTypeRef =
        toDelegatedSelfType(firClass.typeParameters, firClass.symbol)

    fun Node.toDelegatedSelfType(firObject: FirAnonymousObjectBuilder): FirResolvedTypeRef =
        toDelegatedSelfType(firObject.typeParameters, firObject.symbol)

    fun Node.toDelegatedSelfType(typeParameters: List<FirTypeParameterRef>, symbol: FirClassLikeSymbol<*>): FirResolvedTypeRef {
        return buildResolvedTypeRef {
            source = this@toDelegatedSelfType.toFirSourceElement(KtFakeSourceElementKind.ClassSelfTypeRef)
            coneType = ConeClassLikeTypeImpl(
                symbol.toLookupTag(),
                typeParameters.map { ConeTypeParameterType(it.symbol.toLookupTag(), false) }.toTypedArray(),
                false
            )
        }
    }

    fun isImplicitlyActual(status: FirDeclarationStatus, classKind: ClassKind): Boolean {
        return status.isActual && (status.isInline || status.isValue || classKind == ClassKind.ANNOTATION_CLASS)
    }

    fun configureScriptDestructuringDeclarationEntry(declaration: FirVariable, container: FirVariable) {
        (declaration as FirProperty).destructuringDeclarationContainerVariable = container.symbol
    }

    fun createNoTypeForParameterTypeRef(parameterSource: KtSourceElement): FirErrorTypeRef {
        return buildErrorTypeRef {
            source = parameterSource
            diagnostic = ConeSimpleDiagnostic("No type for parameter", DiagnosticKind.ValueParameterWithNoTypeAnnotation)
        }
    }

    fun convertValueParameterName(
        safeName: Name,
        valueParameterDeclaration: ValueParameterDeclaration,
        rawName: () -> String?,
    ): Name {
        return when (valueParameterDeclaration) {
            ValueParameterDeclaration.LAMBDA if (rawName() == "_")
                -> SpecialNames.UNDERSCORE_FOR_UNUSED_VAR
            ValueParameterDeclaration.CATCH, ValueParameterDeclaration.CONTEXT_PARAMETER
                -> if (safeName.asString() == "_") SpecialNames.UNDERSCORE_FOR_UNUSED_VAR else safeName
            else -> safeName
        }
    }

    enum class ValueParameterDeclaration(val shouldExplicitParameterTypeBePresent: Boolean, val isAnnotationOwner: Boolean) {
        FUNCTION(shouldExplicitParameterTypeBePresent = true, isAnnotationOwner = true),
        CATCH(shouldExplicitParameterTypeBePresent = true, isAnnotationOwner = false),
        PRIMARY_CONSTRUCTOR(shouldExplicitParameterTypeBePresent = true, isAnnotationOwner = false),
        SETTER(shouldExplicitParameterTypeBePresent = false, isAnnotationOwner = false),
        LAMBDA(shouldExplicitParameterTypeBePresent = false, isAnnotationOwner = false),
        FOR_LOOP(shouldExplicitParameterTypeBePresent = false, isAnnotationOwner = false),
        CONTEXT_PARAMETER(shouldExplicitParameterTypeBePresent = true, isAnnotationOwner = true),
    }

    fun convertScriptOrSnippets(declaration: Node, sourceFile: KtSourceFile, fileBuilder: FirFileBuilder?): FirDeclaration
    fun Node.forEachChildren(f: (Node) -> Unit) {}
    fun <T> Node.forEachChildrenReturnList(f: (Node, MutableList<T>) -> Unit): MutableList<T> {
        return mutableListOf()
    }

    val Node?.receiverExpression: Node?
    val Node?.selectorExpression: Node?

    fun FirCallableDeclaration.initContainingClassAttr()
    fun FirClassLikeDeclaration.initContainingClassForLocalAttr()
    fun FirRegularClass.initContainingScriptOrReplAttr()
    fun FirRegularClassBuilder.initCompanionObjectSymbolAttr() {
        companionObjectSymbol = (declarations.firstOrNull { it is FirRegularClass && it.isCompanion } as FirRegularClass?)?.symbol
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

    fun constructorTypeParametersFromConstructedClass(ownerTypeParameters: List<FirTypeParameterRef>): List<FirTypeParameterRef> {
        return ownerTypeParameters.mapNotNull {
            val declaredTypeParameter = (it as? FirTypeParameter) ?: return@mapNotNull null
            buildConstructedClassTypeParameterRef {
                source = declaredTypeParameter.symbol.source?.fakeElement(KtFakeSourceElementKind.ConstructorTypeParameter)
                symbol = declaredTypeParameter.symbol
            }
        }
    }

    fun createErrorConstructorBuilder(diagnostic: ConeDiagnostic): FirErrorPrimaryConstructorBuilder =
        FirErrorPrimaryConstructorBuilder().apply { this.diagnostic = diagnostic }

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

    fun buildExpressionHandlingLabelErrors(
        element: FirElement?,
        elementSource: KtSourceElement,
        forbiddenLabelKind: ForbiddenLabelKind?,
        forbiddenLabelSource: KtSourceElement?,
    ): FirElement {
        if (element == null) return org.jetbrains.kotlin.fir.expressions.buildErrorExpression(
            elementSource,
            ConeSyntaxDiagnostic("Empty label")
        )
        if (forbiddenLabelKind == null) return element

        require(forbiddenLabelSource != null)
        return buildErrorExpression {
            this.source = element.source
            this.expression = element as? FirExpression
            this.nonExpressionElement = element.takeUnless { it is FirExpression }
            diagnostic = when (forbiddenLabelKind) {
                ForbiddenLabelKind.UNDERSCORE_IS_RESERVED -> ConeUnderscoreIsReserved(forbiddenLabelSource)
                ForbiddenLabelKind.MULTIPLE_LABEL -> ConeMultipleLabelsAreForbidden(forbiddenLabelSource)
            }
        }
    }

    fun convertUnaryPlusMinusCallOnIntegerLiteralIfNecessary(
        source: Node,
        receiver: FirExpression,
        operationTokenId: Int,
    ): FirExpression? {
        if (receiver !is FirLiteralExpression) return null
        if (receiver.kind != ConstantValueKind.IntegerLiteral) return null

        val convertedValue = when (operationTokenId) {
            KtTokens.MINUS_ID -> -(receiver.value as Long)
            KtTokens.PLUS_ID -> receiver.value as Long
            else -> return null
        }

        return buildLiteralExpression(
            source.toFirSourceElement(),
            ConstantValueKind.IntegerLiteral,
            convertedValue,
            setType = false
        )
    }

    fun convertFirSelector(
        firSelector: FirQualifiedAccessExpression,
        source: KtSourceElement?,
        receiver: FirExpression,
    ): FirQualifiedAccessExpression {
        return if (firSelector is FirImplicitInvokeCall) {
            buildImplicitInvokeCall {
                this.source = source
                annotations.addAll(firSelector.annotations)
                typeArguments.addAll(firSelector.typeArguments)
                explicitReceiver = firSelector.explicitReceiver
                argumentList = buildArgumentList {
                    arguments.add(receiver)
                    arguments.addAll(firSelector.arguments)
                }
                isCallWithExplicitReceiver = true
                calleeReference = firSelector.calleeReference
            }
        } else {
            firSelector.replaceExplicitReceiver(receiver)
            @OptIn(FirImplementationDetail::class)
            firSelector.replaceSource(source)
            firSelector
        }
    }

    fun List<Node?>.toInterpolatingCall(
        base: Node,
        getElementType: (Node) -> Type = { it.elementType },
        convertTemplateEntry: Node?.(String) -> Collection<FirExpression>,
        prefix: () -> String,
    ): FirExpression {
        return buildStringConcatenationCall {
            val sb = StringBuilder()
            var hasExpressions = false
            argumentList = buildArgumentList {
                L@ for (entry in this@toInterpolatingCall) {
                    if (entry == null) continue
                    val type = getElementType(entry)
                    when {
                        type.isStringInterpolationPrefix() || type.isOpenQuote() || type.isClosingQuote() -> continue@L
                        type.isLiteralStringTemplateEntry() -> {
                            sb.append(entry.asText)
                            arguments += buildLiteralExpression(
                                entry.toFirSourceElement(), ConstantValueKind.String, entry.asText, setType = false
                            )
                        }
                        type.isEscapeStringTemplateEntry() -> {
                            val entryText = entry.asText
                            val characterWithDiagnostic = escapedStringToCharacter(entryText)
                            val unescapedCharacter = characterWithDiagnostic.value
                            if (unescapedCharacter != null) {
                                sb.append(unescapedCharacter)
                            }

                            arguments += buildConstOrErrorExpression(
                                entry.toFirSourceElement(),
                                ConstantValueKind.String,
                                unescapedCharacter?.toString(),
                                "character",
                                entryText,
                                characterWithDiagnostic.getDiagnostic() ?: DiagnosticKind.IllegalConstExpression
                            )
                        }
                        type.isShortStringTemplateEntry() || type.isLongStringTemplateEntry() -> {
                            hasExpressions = true
                            val expressions = entry.convertTemplateEntry("Incorrect template argument")
                            if (expressions.isNotEmpty()) {
                                arguments += expressions
                            } else {
                                arguments += buildErrorExpression {
                                    source = entry.toFirSourceElement()
                                    diagnostic = ConeSyntaxDiagnostic("Incorrect template argument")
                                }
                            }
                        }
                        else -> {
                            hasExpressions = true
                            arguments += buildErrorExpression {
                                source = entry.toFirSourceElement()
                                diagnostic = ConeSyntaxDiagnostic("Incorrect template entry: ${entry.asText}")
                            }
                        }
                    }
                }
            }
            source = base.toFirSourceElement()
            interpolationPrefix = prefix()
            // Fast-pass if there is no errors and non-const string expressions
            if (!hasExpressions && !argumentList.arguments.any { it is FirErrorExpression })
                return buildLiteralExpression(
                    source,
                    ConstantValueKind.String,
                    sb.toString(),
                    setType = false,
                    prefix = interpolationPrefix.takeIf { it.isNotEmpty() }
                )
        }
    }

    fun buildLabel(rawName: String, source: KtSourceElement): FirLabel {
        val firLabel = org.jetbrains.kotlin.fir.builder.buildLabel {
            name = KtPsiUtil.unquoteIdentifier(rawName)
            this.source = source
        }

        return firLabel
    }

    fun registerSelfType(selfType: FirResolvedTypeRef)
    fun dispatchReceiverForInnerClassConstructor(): ConeClassLikeType?
    fun callableIdForClassConstructor(): CallableId
    fun FirLoopJumpBuilder.bindLabel(expression: Node): FirLoopJumpBuilder
    fun FirLoopBuilder.prepareTarget(firLabelUser: Any): FirLoopTarget
    fun FirLoopBuilder.configure(target: FirLoopTarget, generateBlock: () -> FirBlock): FirLoop

    fun FirExpression.toReturn(
        baseSource: KtSourceElement? = source,
        labelName: String? = null,
        fromKtReturnExpression: Boolean = false,
    ): FirReturnExpression

    fun Node?.generateAssignment(
        baseSource: KtSourceElement,
        arrayAccessSource: KtSourceElement?,
        rhsExpression: FirExpression,
        operation: FirOperation,
        annotations: List<FirAnnotation>,
        // Effectively `value = rhs?.convert()`, but at generateIndexedAccessAugmentedAssignment we need to recreate FIR for rhs
        // since there should be different nodes for desugaring as `.set(.., get().plus($rhs1))` and `.get(...).plusAssign($rhs2)`
        // Once KT-50861 is fixed, those two parameters shall be eliminated
        rhsAST: Node?,
        isLhsParenthesized: Boolean,
        convert: Node.() -> FirExpression,
    ): FirStatement

    fun generateIncrementOrDecrementBlock(
        // Used to get source-element or text
        wholeExpression: Node,
        operationReference: Node?,
        receiver: Node?,
        callName: Name,
        prefix: Boolean,
        convert: Node.() -> FirExpression,
    ): FirExpression

    fun generateConstantExpressionByLiteral(expression: Node): FirExpression

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
