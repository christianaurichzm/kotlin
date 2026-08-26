/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.lightTree.converter

import com.intellij.lang.LighterASTNode
import com.intellij.psi.TokenType
import org.jetbrains.kotlin.*
import org.jetbrains.kotlin.ElementTypeUtils.isExpression
import org.jetbrains.kotlin.KtNodeTypes.*
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget.*
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.analysis.NodeTypeAnalyzer
import org.jetbrains.kotlin.fir.builder.*
import org.jetbrains.kotlin.fir.builder.AbstractRawFirBuilder.ValueParameterDeclaration
import org.jetbrains.kotlin.fir.contracts.FirContractDescription
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.builder.FirAnonymousFunctionBuilder
import org.jetbrains.kotlin.fir.declarations.builder.FirNamedFunctionBuilder
import org.jetbrains.kotlin.fir.declarations.builder.buildDanglingModifierList
import org.jetbrains.kotlin.fir.declarations.builder.buildFile
import org.jetbrains.kotlin.fir.declarations.builder.buildImport
import org.jetbrains.kotlin.fir.declarations.builder.buildProperty
import org.jetbrains.kotlin.fir.declarations.builder.buildRegularClass
import org.jetbrains.kotlin.fir.declarations.builder.buildTypeAlias
import org.jetbrains.kotlin.fir.declarations.impl.FirDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyGetter
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertySetter
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.utils.addDeclarations
import org.jetbrains.kotlin.fir.diagnostics.ConeDanglingModifierOnTopLevel
import org.jetbrains.kotlin.fir.diagnostics.ConeDiagnostic
import org.jetbrains.kotlin.fir.diagnostics.ConeSimpleDiagnostic
import org.jetbrains.kotlin.fir.diagnostics.ConeSyntaxDiagnostic
import org.jetbrains.kotlin.fir.diagnostics.DiagnosticKind
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.builder.FirWrappedDelegateExpressionBuilder
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationCall
import org.jetbrains.kotlin.fir.expressions.builder.buildAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildErrorAnnotationCall
import org.jetbrains.kotlin.fir.lightTree.fir.ClassWrapper
import org.jetbrains.kotlin.fir.lightTree.fir.TypeConstraint
import org.jetbrains.kotlin.fir.lightTree.fir.modifier.ModifierList
import org.jetbrains.kotlin.fir.references.builder.buildSimpleNamedReference
import org.jetbrains.kotlin.fir.symbols.impl.FirAnonymousFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirDanglingModifierSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFieldSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFileSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirLocalPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeAliasSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeTypeImpl
import org.jetbrains.kotlin.fir.types.FirImplicitTypeRef
import org.jetbrains.kotlin.fir.types.FirTypeProjection
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildDynamicTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildErrorTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildFunctionTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildUserTypeRef
import org.jetbrains.kotlin.fir.types.impl.FirImplicitTypeRefImplWithoutSource
import org.jetbrains.kotlin.fir.types.impl.FirQualifierPartImpl
import org.jetbrains.kotlin.fir.types.impl.FirTypeArgumentListImpl
import org.jetbrains.kotlin.lexer.KtTokens.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.util.getChildren
import org.jetbrains.kotlin.utils.addToStdlib.runIf
import org.jetbrains.kotlin.utils.addToStdlib.runUnless
import kotlin.collections.plusAssign

class TreeRawFirDeclarationBuilderProxy<Node : Any, Type : Any>(
    val analyzer: NodeTypeAnalyzer<Node, Type>,
    val context: Context<Node>,
    val baseModuleData: FirModuleData,
) : NodeTypeAnalyzer<Node, Type> by analyzer {
    /**
     * [org.jetbrains.kotlin.parsing.KotlinParsing.parseFile]
     * [org.jetbrains.kotlin.parsing.KotlinParsing.parsePreamble]
     */
    fun convertFile(file: Node, sourceFile: KtSourceFile, linesMapping: KtSourceFileLinesMapping): FirFile {
        if (!file.elementType.isFile()) {
            //TODO throw error
            throw Exception()
        }

        val fileSymbol = FirFileSymbol()
        val fileAnnotations = mutableListOf<FirAnnotation>()
        val importList = mutableListOf<FirImport>()
        val firDeclarationList = mutableListOf<FirDeclaration>()
        val modifierList = mutableListOf<Node>()
        val scriptNodes = mutableListOf<Node>()
        context.packageFqName = FqName.ROOT
        var packageDirective: FirPackageDirective? = null
        file.forEachChildren { child ->
            val tokenType = child.elementType
            when {
                tokenType.isFileAnnotationList() -> {
                    context.withContainerSymbol(fileSymbol) {
                        convertAnnotationsOnlyTo(child, fileAnnotations)
                    }
                }
                tokenType.isPackageDirective() -> {
                    packageDirective = convertPackageDirective(child).also { context.packageFqName = it.packageFqName }
                }
                tokenType.isImportList() -> importList += convertImportDirectives(child)
                tokenType.isClass() || tokenType.isObjectDeclaration() -> firDeclarationList += convertClass(child)
                tokenType.isTypealias() -> firDeclarationList += convertTypeAlias(child)
                tokenType.isFun() -> firDeclarationList += convertFunctionDeclaration(child) as FirDeclaration
                tokenType.isProperty() -> firDeclarationList += convertPropertyDeclaration(child)
                tokenType.isDestructuringDeclaration() -> {
                    val initializer = buildFirDestructuringDeclarationInitializer(child)
                    firDeclarationList += buildErrorNonLocalDestructuringDeclaration(
                        child.toFirSourceElement(), initializer, baseModuleData
                    )
                }
                tokenType.isScript() -> scriptNodes += child
                tokenType.isModifierList() -> modifierList += child
            }
        }

        modifierList.forEach {
            firDeclarationList += buildErrorNonLocalDeclarationForDanglingModifierList(it)
        }

        return buildFile {
            symbol = fileSymbol
            source = file.toFirSourceElement()
            origin = FirDeclarationOrigin.Source
            moduleData = baseModuleData
            name = sourceFile.name
            this.sourceFile = sourceFile
            this.sourceFileLinesMapping = linesMapping
            this.packageDirective = packageDirective ?: buildPackageDirective { packageFqName = context.packageFqName }
            annotations += fileAnnotations
            imports += importList
            for (scriptNode in scriptNodes) {
                firDeclarationList += convertScriptOrSnippets(scriptNode, sourceFile, this@buildFile)
            }
            declarations += firDeclarationList
        }
    }

    /*****    ANNOTATIONS    *****/
    /**
     * Convert only annotations
     * @see org.jetbrains.kotlin.parsing.KotlinParsing.parseModifierList
     */
    private fun convertAnnotationsOnlyTo(modifierList: Node, list: MutableList<in FirAnnotationCall>) {
        modifierList.forEachChildren { node ->
            convertAnnotationOrAnnotationEntryTo(node, list)
        }
    }

    private fun convertAnnotationOrAnnotationEntryTo(node: Node, list: MutableList<in FirAnnotationCall>) {
        val tokenType = node.elementType
        when {
            tokenType.isAnnotation() -> convertAnnotationTo(node, list)
            tokenType.isAnnotationEntry() -> list += convertAnnotationEntry(node)
        }
    }

    /**
     * @see org.jetbrains.kotlin.parsing.KotlinParsing.parseAnnotationOrList
     */
    fun convertAnnotationTo(node: Node, list: MutableList<in FirAnnotationCall>) {
        var annotationTarget: AnnotationUseSiteTarget? = null
        node.forEachChildren { child ->
            val tokenType = child.elementType
            when {
                tokenType.isAnnotationTarget() -> annotationTarget = convertAnnotationTarget(child)
                tokenType.isAnnotationEntry() -> list += convertAnnotationEntry(
                    child,
                    annotationTarget,
                    runIf(annotationTarget == ALL) {
                        ConeSimpleDiagnostic(
                            "Multiple annotation syntax with @all use-site target is forbidden",
                            DiagnosticKind.MultipleAnnotationWithAllTarget
                        )
                    }
                )
            }
        }
    }

    /**
     * @see org.jetbrains.kotlin.parsing.KotlinParsing.parseAnnotation
     * can be treated as unescapedAnnotation
     */
    fun convertAnnotationEntry(
        unescapedAnnotation: Node,
        defaultAnnotationUseSiteTarget: AnnotationUseSiteTarget? = null,
        diagnostic: ConeDiagnostic? = null,
    ): FirAnnotationCall = context.withForcedLocalContext {
        var annotationUseSiteTarget: AnnotationUseSiteTarget? = null
        lateinit var constructorCalleePair: Pair<FirTypeRef, List<FirExpression>>
        unescapedAnnotation.forEachChildren { child ->
            val tokenType = child.elementType
            when {
                tokenType.isAnnotationTarget() -> annotationUseSiteTarget = convertAnnotationTarget(child)
                tokenType.isConstructorCallee() -> constructorCalleePair = convertConstructorInvocation(unescapedAnnotation)
            }
        }
        val qualifier = (constructorCalleePair.first as? FirUserTypeRef)?.qualifier?.last()
        val name = qualifier?.name ?: Name.special("<no-annotation-name>")
        val theCalleeReference = buildSimpleNamedReference {
            source = unescapedAnnotation
                .getChildNodeByType(CONSTRUCTOR_CALLEE)
                ?.getChildNodeByType(TYPE_REFERENCE)
                ?.getChildNodeByType(USER_TYPE)
                ?.getChildNodeByType(REFERENCE_EXPRESSION)
                ?.toFirSourceElement()
            this.name = name
        }

        if (diagnostic == null) {
            buildAnnotationCall {
                source = unescapedAnnotation.toFirSourceElement()
                useSiteTarget = annotationUseSiteTarget ?: defaultAnnotationUseSiteTarget
                annotationTypeRef = constructorCalleePair.first
                calleeReference = theCalleeReference
                extractArgumentsFrom(constructorCalleePair.second)
                typeArguments += qualifier?.typeArgumentList?.typeArguments ?: listOf()
                containingDeclarationSymbol = context.containerSymbol
            }
        } else {
            buildErrorAnnotationCall {
                source = unescapedAnnotation.toFirSourceElement()
                useSiteTarget = annotationUseSiteTarget ?: defaultAnnotationUseSiteTarget
                annotationTypeRef = constructorCalleePair.first
                this.diagnostic = diagnostic
                calleeReference = theCalleeReference
                extractArgumentsFrom(constructorCalleePair.second)
                typeArguments += qualifier?.typeArgumentList?.typeArguments ?: listOf()
                containingDeclarationSymbol = context.containerSymbol
            }
        }
    }

    /**
     * @see org.jetbrains.kotlin.parsing.KotlinParsing.parseAnnotationTarget
     */
    private fun convertAnnotationTarget(annotationUseSiteTarget: Node): AnnotationUseSiteTarget {
        lateinit var annotationTarget: AnnotationUseSiteTarget
        annotationUseSiteTarget.forEachChildren {
            val tokenType = it.elementType
            when {
                tokenType.isAllKeyword() -> annotationTarget = ALL
                tokenType.isFieldKeyword() -> annotationTarget = FIELD
                tokenType.isFileKeyword() -> annotationTarget = AnnotationUseSiteTarget.FILE
                tokenType.isPropertyKeyword() -> annotationTarget = AnnotationUseSiteTarget.PROPERTY
                tokenType.isGetKeyword() -> annotationTarget = PROPERTY_GETTER
                tokenType.isSetKeyword() -> annotationTarget = PROPERTY_SETTER
                tokenType.isReceiverKeyword() -> annotationTarget = RECEIVER
                tokenType.isParamKeyword() -> annotationTarget = CONSTRUCTOR_PARAMETER
                tokenType.isSetParamKeyword() -> annotationTarget = SETTER_PARAMETER
                tokenType.isDelegateKeyword() -> annotationTarget = PROPERTY_DELEGATE_FIELD
            }
        }

        return annotationTarget
    }

    /*****    PREAMBLE    *****/
    /**
     * @see org.jetbrains.kotlin.parsing.KotlinParsing.parsePackageName
     */
    private fun convertPackageDirective(packageNode: Node): FirPackageDirective {
        var packageName: FqName = FqName.ROOT
        packageNode.forEachChildren {
            when (it.tokenType) {
                //TODO separate logic for both expression types
                DOT_QUALIFIED_EXPRESSION, REFERENCE_EXPRESSION -> packageName = parsePackageName(it)
            }
        }
        return buildPackageDirective {
            packageFqName = packageName
            source = packageNode.toFirSourceElement()
        }
    }

    private fun parsePackageName(node: Node): FqName {
        var packageName: FqName = FqName.ROOT
        val parts = parsePackageParts(node)

        for (part in parts) {
            packageName = packageName.child(Name.identifier(part))
        }

        return packageName
    }

    private fun parsePackageParts(node: Node): List<String> {
        fun parse(node: Node): MutableList<String> {
            if (node.tokenType == DOT_QUALIFIED_EXPRESSION) {
                val children = node.getChildren(tree)

                if (children.size == 3) {
                    return parse(children.first()).apply {
                        add(children.last().getAsStringWithoutBacktick())
                    }
                }
            }

            if (node.tokenType == REFERENCE_EXPRESSION) {
                return mutableListOf(node.getAsStringWithoutBacktick())
            }

            return mutableListOf()
        }

        return parse(node)
    }

    /**
     * @see org.jetbrains.kotlin.parsing.KotlinParsing.parseImportDirectives
     */
    private fun convertImportDirectives(importList: Node): List<FirImport> {
        return importList.forEachChildrenReturnList { node, container ->
            when (node.tokenType) {
                IMPORT_DIRECTIVE -> container += convertImportDirective(node)
            }
        }
    }

    /**
     * @see org.jetbrains.kotlin.parsing.KotlinParsing.parseImportDirective
     */
    private fun convertImportDirective(importDirective: Node): FirImport {
        var importedFqName: FqName? = null
        var isAllUnder = false
        var aliasName: String? = null
        var aliasSource: KtSourceElement? = null
        importDirective.forEachChildren { child ->
            when (child.tokenType) {
                REFERENCE_EXPRESSION, DOT_QUALIFIED_EXPRESSION -> {
                    importedFqName = mutableListOf<String>()
                        .apply { collectSegments(child) }
                        .joinToString(".")
                        .let { FqName(it) }
                }
                MUL -> isAllUnder = true
                IMPORT_ALIAS -> {
                    val importAlias = convertImportAlias(child)
                    if (importAlias != null) {
                        aliasName = importAlias.first
                        aliasSource = importAlias.second
                    }
                }
            }
        }

        return buildImport {
            source = importDirective.toFirSourceElement()
            this.importedFqName = importedFqName
            this.isAllUnder = isAllUnder
            this.aliasName = aliasName?.let { Name.identifier(it) }
            this.aliasSource = aliasSource
        }
    }

    private fun MutableList<String>.collectSegments(expression: Node) {
        when (expression.tokenType) {
            REFERENCE_EXPRESSION -> add(expression.getAsStringWithoutBacktick())
            DOT_QUALIFIED_EXPRESSION -> {
                expression.forEachChildren {
                    collectSegments(it)
                }
            }
            else -> {}
        }
    }

    private fun convertImportAlias(importAlias: Node): Pair<String, KtSourceElement>? {
        var result: Pair<String, KtSourceElement>? = null
        importAlias.forEachChildren {
            if (result != null) return@forEachChildren
            when (it.tokenType) {
                IDENTIFIER -> result = Pair(it.asText, it.toFirSourceElement())
            }
        }

        return null
    }

    /*****    DECLARATIONS    *****/
    /**
     * @see org.jetbrains.kotlin.parsing.KotlinParsing.parseClassOrObject
     */
    fun convertClass(classNode: Node): FirRegularClass {
        var modifiers: ModifierList? = null
        var classKind: ClassKind = ClassKind.CLASS
        var identifier: String? = null
        val firTypeParameters = mutableListOf<FirTypeParameter>()
        var primaryConstructor: Node? = null
        val typeConstraints = mutableListOf<TypeConstraint>()
        var classBody: Node? = null
        var superTypeList: Node? = null
        var typeParameterList: Node? = null
        classNode.forEachChildren {
            when (it.tokenType) {
                MODIFIER_LIST -> modifiers = convertModifierList(it, isInClass = true)
                IDENTIFIER -> identifier = it.asText
            }
        }

        val calculatedModifiers = modifiers ?: ModifierList()
        val className = identifier.nameAsSafeName(if (calculatedModifiers.isCompanion()) "Companion" else "")
        val isLocalWithinParent = classNode.getParent()?.elementType != CLASS_BODY && isClassLocal(classNode) { getParent() }
        val classIsExpect = calculatedModifiers.hasExpect() || context.containerIsExpect

        return context.withChildClassName(className, isExpect = classIsExpect, isLocalWithinParent) {
            val classSymbol = FirRegularClassSymbol(context.currentClassId)
            context.withContainerSymbol(classSymbol) {
                classNode.forEachChildren {
                    when (it.tokenType) {
                        CLASS_KEYWORD -> classKind = ClassKind.CLASS
                        INTERFACE_KEYWORD -> classKind = ClassKind.INTERFACE
                        OBJECT_KEYWORD -> classKind = ClassKind.OBJECT
                        TYPE_PARAMETER_LIST -> typeParameterList = it
                        PRIMARY_CONSTRUCTOR -> primaryConstructor = it
                        SUPER_TYPE_LIST -> superTypeList = it
                        TYPE_CONSTRAINT_LIST -> typeConstraints += convertTypeConstraints(it)
                        CLASS_BODY -> classBody = it
                    }
                }

                if (classKind == ClassKind.CLASS) {
                    classKind = when {
                        calculatedModifiers.isEnum() -> ClassKind.ENUM_CLASS
                        calculatedModifiers.isAnnotation() -> ClassKind.ANNOTATION_CLASS
                        else -> classKind
                    }
                }

                val isLocal = context.inLocalContext
                val status = FirDeclarationStatusImpl(
                    if (isLocal) Visibilities.Local else calculatedModifiers.getVisibility(publicByDefault = true),
                    calculatedModifiers.getModality(isClassOrObject = true)
                ).apply {
                    isExpect = classIsExpect
                    isActual = calculatedModifiers.hasActual()
                    isInner = calculatedModifiers.isInner()
                    isCompanion = calculatedModifiers.isCompanion() && classKind == ClassKind.OBJECT
                    isData = calculatedModifiers.isDataClass()
                    isInline = calculatedModifiers.isInlineClass()
                    isValue = calculatedModifiers.isValueClass()
                    isFun = calculatedModifiers.isFunctionalInterface()
                    isExternal = calculatedModifiers.hasExternal()
                }


                typeParameterList?.let { firTypeParameters += convertTypeParameters(it, typeConstraints, classSymbol) }

                withCapturedTypeParameters(
                    status = status.isInner || isLocal,
                    declarationSource = classNode.toFirSourceElement(),
                    currentFirTypeParameters = firTypeParameters,
                ) {
                    var delegatedFieldsMap: Map<Int, FirFieldSymbol>? = null
                    val companionBlockCollector = CompanionBlockCollector()
                    buildRegularClass {
                        source = classNode.toFirSourceElement()
                        moduleData = baseModuleData
                        origin = FirDeclarationOrigin.Source
                        name = className
                        this.status = status
                        this.classKind = classKind
                        scopeProvider = baseScopeProvider
                        symbol = classSymbol
                        modifiers?.convertAnnotationsTo(annotations)
                        typeParameters += firTypeParameters

                        context.appendOuterTypeParameters(ignoreLastLevel = true, typeParameters)

                        val selfType = classNode.toDelegatedSelfType(this)
                        registerSelfType(selfType)

                        val delegationSpecifiers = superTypeList?.let { convertDelegationSpecifiers(it) }
                        var delegatedSuperTypeRef: FirTypeRef? = delegationSpecifiers?.superTypeCalls?.lastOrNull()?.delegatedSuperTypeRef
                        val delegatedConstructorSource: KtLightSourceElement? = delegationSpecifiers?.superTypeCalls?.lastOrNull()?.source

                        val superTypeRefs = mutableListOf<FirTypeRef>()

                        delegationSpecifiers?.let { superTypeRefs += it.superTypesRef }

                        when {
                            calculatedModifiers.isEnum() && (classKind == ClassKind.ENUM_CLASS) && delegatedConstructorSource == null -> {
                                delegatedSuperTypeRef = buildResolvedTypeRef {
                                    coneType = ConeClassLikeTypeImpl(
                                        implicitEnumType.coneType.lookupTag,
                                        arrayOf(selfType.coneType),
                                        isMarkedNullable = false
                                    )
                                    source = classNode.toFirSourceElement(KtFakeSourceElementKind.EnumSuperTypeRef)
                                }
                                superTypeRefs += delegatedSuperTypeRef
                            }
                            calculatedModifiers.isAnnotation() && (classKind == ClassKind.ANNOTATION_CLASS) -> {
                                superTypeRefs += implicitAnnotationType
                                delegatedSuperTypeRef = implicitAnyType
                            }
                        }

                        val classIsKotlinAny = symbol.classId == StandardClassIds.Any

                        if (superTypeRefs.isEmpty() && !classIsKotlinAny) {
                            val classIsKotlinNothing = symbol.classId == StandardClassIds.Nothing
                            // kotlin.Nothing doesn't have `Any` supertype, but does have delegating constructor call to Any
                            if (!classIsKotlinNothing) {
                                superTypeRefs += implicitAnyType
                            }
                            delegatedSuperTypeRef = implicitAnyType
                        }

                        this.superTypeRefs += superTypeRefs

                        val secondaryConstructors = classBody.getChildNodesByType(SECONDARY_CONSTRUCTOR)
                        val classWrapper = ClassWrapper(
                            calculatedModifiers, classKind, this, hasSecondaryConstructor = secondaryConstructors.isNotEmpty(),
                            hasDefaultConstructor = if (primaryConstructor != null) !primaryConstructor.hasValueParameters()
                            else secondaryConstructors.isEmpty() || secondaryConstructors.any { !it.hasValueParameters() },
                            delegatedSelfTypeRef = selfType,
                            delegatedSuperTypeRef = delegatedSuperTypeRef ?: FirImplicitTypeRefImplWithoutSource,
                            delegatedSuperCalls = delegationSpecifiers?.superTypeCalls ?: emptyList(),
                            companionBlockCollector,
                        )
                        //parse primary constructor
                        val primaryConstructorWrapper = convertPrimaryConstructor(
                            primaryConstructor,
                            selfType.source,
                            classWrapper,
                            delegatedConstructorSource,
                            containingClassIsExpectClass = status.isExpect,
                            isImplicitlyActual = isImplicitlyActual(status, classKind),
                            isKotlinAny = classIsKotlinAny,
                        )
                        val firPrimaryConstructor = primaryConstructorWrapper?.firConstructor
                        firPrimaryConstructor?.let { declarations += it }
                        delegationSpecifiers?.delegateFieldsMap?.values?.mapTo(declarations) { it.fir }
                        delegatedFieldsMap = delegationSpecifiers?.delegateFieldsMap?.takeIf { it.isNotEmpty() }

                        val properties = mutableListOf<FirProperty>()
                        if (primaryConstructor != null && firPrimaryConstructor != null) {
                            //parse properties
                            properties += primaryConstructorWrapper.valueParameters
                                .filter { it.hasValOrVar() }
                                .map {
                                    it.toFirPropertyFromPrimaryConstructor(
                                        baseModuleData,
                                        callableIdForName(it.firValueParameter.name),
                                        classIsExpect,
                                        currentDispatchReceiverType(),
                                        context
                                    )
                                }
                            addDeclarations(properties)
                        }

                        //parse declarations
                        classBody?.let {
                            addDeclarations(convertClassBody(it, classWrapper))
                        }

                        //parse data class
                        if (calculatedModifiers.isDataClass() && firPrimaryConstructor != null) {
                            val zippedParameters = properties.map { it.source!!.lighterASTNode to it }
                            generateDataClassMembers(
                                primaryConstructor ?: classNode,
                                this,
                                firPrimaryConstructor,
                                zippedParameters,
                                context.packageFqName,
                                context.className,
                                addValueParameterAnnotations = { valueParam ->
                                    context.withContainerSymbol(symbol) {
                                        valueParam.forEachChildren { node ->
                                            if (node.tokenType == MODIFIER_LIST) {
                                                buildList {
                                                    convertAnnotationsOnlyTo(node, this)
                                                }.filterTo(annotations) {
                                                    it.useSiteTarget.appliesToPrimaryConstructorParameter()
                                                }
                                            }
                                        }
                                    }
                                },
                            )
                        }

                        if (calculatedModifiers.isEnum()) {
                            generateValuesFunction(
                                baseModuleData,
                                context.packageFqName,
                                context.className,
                                classIsExpect
                            )
                            generateValueOfFunction(
                                baseModuleData,
                                context.packageFqName,
                                context.className,
                                classIsExpect
                            )
                            generateEntriesGetter(
                                baseModuleData,
                                context.packageFqName,
                                context.className,
                                classIsExpect
                            )
                        }
                        initCompanionObjectSymbolAttr()

                        contextParameters.addContextParameters(modifiers?.contextLists, classSymbol)
                    }.apply {
                        this.delegateFieldsMap = delegatedFieldsMap
                        companionBlockCollector.toCompanionBlockInfoOrNull()?.let { companionBlocks = it }
                    }
                }.also {
                    fillDanglingConstraintsTo(firTypeParameters, typeConstraints, it)
                }
            }
        }.also {
            if (classNode.getParent()?.elementType == CLASS_BODY) {
                it.initContainingClassForLocalAttr()
            }
            it.initContainingScriptOrReplAttr()
            if (isDirectlyInsideCompanionBlock) {
                it.isIllegalCompanionBlockMember = true
            }
        }
    }

    /**
     * @see org.jetbrains.kotlin.parsing.KotlinParsing.parseTypeAlias
     */
    fun convertTypeAlias(typeAlias: Node): FirTypeAlias {
        var modifiers: ModifierList? = null
        var identifier: String? = null
        lateinit var typeRefNode: Node
        var typeParametersNode: Node? = null

        typeAlias.forEachChildren {
            when (it.tokenType) {
                MODIFIER_LIST -> modifiers = convertModifierList(it)
                IDENTIFIER -> identifier = it.asText
                TYPE_REFERENCE -> typeRefNode = it
                TYPE_PARAMETER_LIST -> typeParametersNode = it
            }
        }

        val calculatedModifiers = modifiers ?: ModifierList()
        val typeAliasName = identifier.nameAsSafeName()
        val typeAliasIsExpect = calculatedModifiers.hasExpect() || context.containerIsExpect
        return context.withChildClassName(typeAliasName, isExpect = typeAliasIsExpect) {
            val typeAliasSymbol = FirTypeAliasSymbol(context.currentClassId)
            context.withContainerSymbol(typeAliasSymbol) {
                val isInner = calculatedModifiers.isInner()
                buildTypeAlias {
                    source = typeAlias.toFirSourceElement()
                    moduleData = baseModuleData
                    origin = FirDeclarationOrigin.Source
                    scopeProvider = baseScopeProvider
                    name = typeAliasName
                    val isLocal = context.inLocalContext
                    status = FirDeclarationStatusImpl(
                        if (isLocal) Visibilities.Local else calculatedModifiers.getVisibility(publicByDefault = true),
                        Modality.FINAL,
                    ).apply {
                        isExpect = typeAliasIsExpect
                        isActual = calculatedModifiers.hasActual()
                        this.isInner = isInner
                    }

                    symbol = typeAliasSymbol
                    expandedTypeRef = convertType(typeRefNode)
                    modifiers?.convertAnnotationsTo(annotations)
                    typeParametersNode?.let { typeParameters += convertTypeParameters(it, emptyList(), typeAliasSymbol) }

                    if (isInner || isLocal) {
                        context.appendOuterTypeParameters(ignoreLastLevel = false, typeParameters)
                    }
                }
            }
        }.also {
            if (typeAlias.getParent()?.elementType == CLASS_BODY) {
                it.initContainingClassForLocalAttr()
            }
            if (isDirectlyInsideCompanionBlock) {
                it.isIllegalCompanionBlockMember = true
            }
        }
    }

    /**
     * @see org.jetbrains.kotlin.parsing.KotlinParsing.parseFunction
     */
    fun convertFunctionDeclaration(functionDeclaration: Node): FirStatement {
        var modifiers: ModifierList? = null
        var identifier: String? = null
        var valueParametersList: Node? = null
        var isReturnType = false
        var receiverTypeNode: Node? = null
        var returnType: FirTypeRef? = null
        val typeConstraints = mutableListOf<TypeConstraint>()
        var block: Node? = null
        var expression: Node? = null
        var hasEqToken = false
        var typeParameterList: Node? = null
        var outerContractDescription: FirContractDescription? = null
        functionDeclaration.getChildNodeByType(IDENTIFIER)?.let {
            identifier = it.asText
        }

        val isLocal = isCallableLocal(functionDeclaration) { getParent() }
        val functionSource = functionDeclaration.toFirSourceElement()
        val isAnonymousFunction = identifier == null && isLocal
        val functionName = identifier.nameAsSafeName()
        val functionSymbol: FirFunctionSymbol<*> = if (isAnonymousFunction) {
            FirAnonymousFunctionSymbol()
        } else {
            FirNamedFunctionSymbol(callableIdForName(functionName))
        }
        val isCompanionBlockMember = isDirectlyInsideCompanionBlock

        context.withContainerSymbol(functionSymbol, isLocal) {
            val target: FirFunctionTarget
            functionDeclaration.forEachChildren {
                when (it.tokenType) {
                    MODIFIER_LIST -> {
                        modifiers = convertModifierList(it)
                    }
                    TYPE_PARAMETER_LIST -> typeParameterList = it
                    VALUE_PARAMETER_LIST -> valueParametersList = it //must convert later, because it can contain "return"
                    COLON -> isReturnType = true
                    TYPE_REFERENCE -> if (isReturnType) returnType = convertType(it) else receiverTypeNode = it
                    TYPE_CONSTRAINT_LIST -> typeConstraints += convertTypeConstraints(it)
                    CONTRACT_EFFECT_LIST -> outerContractDescription = obtainContractDescription(it)
                    BLOCK -> block = it
                    EQ -> hasEqToken = true
                    else -> if (it.isExpression()) expression = it
                }
            }

            val calculatedModifiers = modifiers ?: ModifierList()

            if (returnType == null) {
                returnType =
                    if (block != null || !hasEqToken) implicitUnitType
                    else implicitType
            }

            val receiverTypeCalculator = receiverTypeNode?.let { { convertType(it) } }
            val functionBuilder = if (isAnonymousFunction) {
                FirAnonymousFunctionBuilder().apply {
                    source = functionSource
                    receiverParameter = receiverTypeCalculator?.let { createReceiverParameter(it, baseModuleData, functionSymbol) }
                    symbol = functionSymbol as FirAnonymousFunctionSymbol
                    isLambda = false
                    hasExplicitParameterList = true
                    label = context.getLastLabel(functionDeclaration)
                    val labelName = label?.name ?: context.calleeNamesForLambda.lastOrNull()?.identifier
                    target = FirFunctionTarget(labelName = labelName, isLambda = false)

                    val isExpect = calculatedModifiers.hasExpect() || context.containerIsExpect
                    val isActual = calculatedModifiers.hasActual()
                    val isOverride = calculatedModifiers.hasOverride()
                    val isOperator = calculatedModifiers.hasOperator()
                    val isInfix = calculatedModifiers.hasInfix()
                    val isInline = calculatedModifiers.hasInline()
                    val isTailRec = calculatedModifiers.hasTailrec()
                    val isExternal = calculatedModifiers.hasExternal()
                    val isSuspend = calculatedModifiers.hasSuspend()

                    if (isExpect || isActual || isOverride || isOperator || isInfix || isInline || isTailRec || isExternal || isSuspend) {
                        status = FirResolvedDeclarationStatusImpl.DEFAULT_STATUS_FOR_STATUSLESS_DECLARATIONS.copy(
                            isExpect = isExpect,
                            isActual = isActual,
                            isOverride = isOverride,
                            isOperator = isOperator,
                            isInfix = isInfix,
                            isInline = isInline,
                            isTailRec = isTailRec,
                            isExternal = isExternal,
                            isSuspend = isSuspend,
                        )
                    }
                }
            } else {
                val labelName =
                    context.getLastLabel(functionDeclaration)?.name ?: runIf(!functionName.isSpecial) { functionName.identifier }
                target = FirFunctionTarget(labelName, isLambda = false)
                FirNamedFunctionBuilder().apply {
                    source = functionSource
                    receiverParameter = receiverTypeCalculator?.let { createReceiverParameter(it, baseModuleData, functionSymbol) }
                    name = functionName
                    this.isLocal = context.inLocalContext
                    status = FirDeclarationStatusImpl(
                        if (isLocal) Visibilities.Local else calculatedModifiers.getVisibility(),
                        calculatedModifiers.getModality(isClassOrObject = false)
                    ).apply {
                        isExpect = calculatedModifiers.hasExpect() || context.containerIsExpect
                        isActual = calculatedModifiers.hasActual()
                        isOverride = calculatedModifiers.hasOverride()
                        isOperator = calculatedModifiers.hasOperator()
                        isInfix = calculatedModifiers.hasInfix()
                        isInline = calculatedModifiers.hasInline()
                        isTailRec = calculatedModifiers.hasTailrec()
                        isExternal = calculatedModifiers.hasExternal()
                        isSuspend = calculatedModifiers.hasSuspend()
                        isStatic = calculatedModifiers.hasCompanion() || isCompanionBlockMember
                    }

                    symbol = functionSymbol as FirNamedFunctionSymbol
                    dispatchReceiverType = runIf(!isLocal && !isCompanionBlockMember) { currentDispatchReceiverType() }
                }
            }

            val firTypeParameters = mutableListOf<FirTypeParameter>()
            typeParameterList?.let { firTypeParameters += convertTypeParameters(it, typeConstraints, functionSymbol) }

            val function = functionBuilder.apply {
                moduleData = baseModuleData
                origin = FirDeclarationOrigin.Source
                returnTypeRef = returnType

                context.firFunctionTargets += target
                modifiers?.convertAnnotationsTo(annotations)
                typeParameters += firTypeParameters

                withCapturedTypeParameters(true, functionSource, typeParameters) {
                    contextParameters.addContextParameters(modifiers?.contextLists, functionSymbol)

                    valueParametersList?.let { list ->
                        valueParameters += convertValueParameters(
                            list,
                            functionSymbol,
                            if (isAnonymousFunction) ValueParameterDeclaration.LAMBDA else ValueParameterDeclaration.FUNCTION
                        ).map { it.firValueParameter }
                    }

                    val allowLegacyContractDescription = outerContractDescription == null
                    val bodyWithContractDescription = context.withForcedLocalContext(
                        forceKeepingTheBodyInHeaderMode = functionBuilder.status.isInline || functionBuilder.returnTypeRef is FirImplicitTypeRef
                    ) {
                        convertFunctionBody(block, expression, allowLegacyContractDescription)
                    }
                    this.body = bodyWithContractDescription.first
                    val contractDescription = outerContractDescription ?: bodyWithContractDescription.second
                    contractDescription?.let {
                        if (this is FirNamedFunctionBuilder) {
                            this.contractDescription = it
                        } else if (this is FirAnonymousFunctionBuilder) {
                            this.contractDescription = it
                        }
                    }
                }
                context.firFunctionTargets.removeLast()
            }.build().also {
                target.bind(it)
                fillDanglingConstraintsTo(firTypeParameters, typeConstraints, it)

                if (!isLocal && isCompanionBlockMember) {
                    it.initContainingClassAttr()
                }
            }

            return if (function is FirAnonymousFunction) {
                buildAnonymousFunctionExpression {
                    source = functionSource
                    anonymousFunction = function
                }
            } else {
                function
            }
        }
    }

    /**
     * @see org.jetbrains.kotlin.parsing.KotlinParsing.parseProperty
     */
    fun convertPropertyDeclaration(property: Node, classWrapper: ClassWrapper? = null): FirProperty {
        var modifiers: ModifierList? = null
        var identifier: String? = null
        val firTypeParameters = mutableListOf<FirTypeParameter>()
        var isReturnType = false
        var delegate: Node? = null
        var isVar = false
        var receiverTypeNode: Node? = null
        var returnType: FirTypeRef = implicitType
        val typeConstraints = mutableListOf<TypeConstraint>()
        val accessors = mutableListOf<Node>()
        var propertyInitializer: FirExpression? = null
        var typeParameterList: Node? = null
        var fieldDeclaration: Node? = null
        property.getChildNodeByType(IDENTIFIER)?.let {
            identifier = it.asText
        }

        val isLocal = isCallableLocal(property) { getParent() }
        val isInsideScript = context.containingScriptSymbol != null && context.className == FqName.ROOT
        val propertyName = when {
            (isLocal || isInsideScript) && identifier == "_" -> SpecialNames.UNDERSCORE_FOR_UNUSED_VAR
            else -> identifier.nameAsSafeName()
        }
        val propertySymbol = if (isLocal) {
            FirLocalPropertySymbol()
        } else {
            FirRegularPropertySymbol(callableIdForName(propertyName))
        }
        val isCompanionBlockMember = isDirectlyInsideCompanionBlock

        context.withContainerSymbol(propertySymbol, isLocal) {
            val propertySource = property.toFirSourceElement()
            property.forEachChildren {
                when (it.tokenType) {
                    MODIFIER_LIST -> {
                        modifiers = convertModifierList(it)
                    }
                    TYPE_PARAMETER_LIST -> typeParameterList = it
                    COLON -> isReturnType = true
                    TYPE_REFERENCE -> if (isReturnType) returnType = convertType(it) else receiverTypeNode = it
                    TYPE_CONSTRAINT_LIST -> typeConstraints += convertTypeConstraints(it)
                    PROPERTY_DELEGATE -> delegate = it
                    VAR_KEYWORD -> isVar = true
                    PROPERTY_ACCESSOR -> {
                        accessors += it
                    }
                    BACKING_FIELD -> fieldDeclaration = it
                    else -> if (it.isExpression()) {
                        context.calleeNamesForLambda += null
                        val keepBodyInHeaderMode = !isReturnType || modifiers?.isConst() == true
                        propertyInitializer = runIf(isLocal || !headerMode || keepBodyInHeaderMode) {
                            context.withForcedLocalContext(keepBodyInHeaderMode) {
                                expressionConverter.getAsFirExpression(it, "Should have initializer")
                            }
                        }
                        context.calleeNamesForLambda.removeLast()
                    }
                }
            }

            val calculatedModifiers = modifiers ?: ModifierList()
            val propertyAnnotations = calculatedModifiers.convertAnnotations()
            val isStatic = calculatedModifiers.hasCompanion() || isCompanionBlockMember

            return buildProperty {
                source = propertySource
                moduleData = baseModuleData
                origin = FirDeclarationOrigin.Source
                returnTypeRef = returnType
                name = propertyName
                this.isVar = isVar

                receiverParameter = receiverTypeNode?.let { createReceiverParameter({ convertType(it) }, moduleData, propertySymbol) }
                initializer = propertyInitializer

                //probably can do this for delegateExpression itself
                val delegateSource = delegate?.let {
                    (it.getChildExpression() ?: it).toFirSourceElement()
                }

                symbol = propertySymbol
                this.isLocal = context.inLocalContext

                typeParameterList?.let { firTypeParameters += convertTypeParameters(it, typeConstraints, symbol) }

                backingField = fieldDeclaration.convertBackingField(
                    symbol, calculatedModifiers, returnType, isVar,
                    if (isLocal) emptyList() else propertyAnnotations.filter {
                        it.useSiteTarget == FIELD || it.useSiteTarget == PROPERTY_DELEGATE_FIELD
                    },
                    property,
                    isStatic,
                ).also {
                    if (!isLocal) {
                        it.initContainingClassAttr()
                    }
                }

                if (isLocal) {
                    val delegateBuilder = delegate?.let {
                        FirWrappedDelegateExpressionBuilder().apply {
                            source = delegateSource?.fakeElement(KtFakeSourceElementKind.WrappedDelegate)
                            expression = expressionConverter.getAsFirExpression(it, "Incorrect delegate expression")
                        }
                    }
                    status = FirDeclarationStatusImpl(Visibilities.Local, Modality.FINAL).apply {
                        isLateInit = calculatedModifiers.hasLateinit()
                    }

                    typeParameters += firTypeParameters
                    generateAccessorsByDelegate(
                        delegateBuilder,
                        baseModuleData,
                        classWrapper?.classBuilder?.ownerRegularOrAnonymousObjectSymbol,
                        context = context,
                        isExtension = false,
                        explicitDeclarationSource = propertySource,
                    )
                } else {
                    if (!isCompanionBlockMember) {
                        dispatchReceiverType = currentDispatchReceiverType()
                    }
                    withCapturedTypeParameters(true, propertySource, firTypeParameters) {
                        typeParameters += firTypeParameters

                        val delegateBuilder = delegate?.let {
                            FirWrappedDelegateExpressionBuilder().apply {
                                source = delegateSource?.fakeElement(KtFakeSourceElementKind.WrappedDelegate)
                                expression = expressionConverter.getAsFirExpression(it, "Should have delegate")
                            }
                        }

                        val propertyVisibility = calculatedModifiers.getVisibility()

                        fun defaultAccessorStatus() =
                            // Downward propagation of `inline` and `external` modifiers (from property to its accessors)
                            FirDeclarationStatusImpl(propertyVisibility, null).apply {
                                isInline = calculatedModifiers.hasInline()
                                isExternal = calculatedModifiers.hasExternal()
                                this.isStatic = isStatic
                            }

                        val convertedAccessors = accessors.map {
                            convertGetterOrSetter(it, returnType, propertyVisibility, symbol, calculatedModifiers, propertyAnnotations, isCompanionBlockMember)
                        }
                        this.getter = convertedAccessors.find { it.isGetter }
                            ?: FirDefaultPropertyGetter(
                                source = property.toFirSourceElement(KtFakeSourceElementKind.DefaultAccessor.Getter),
                                moduleData = moduleData,
                                origin = FirDeclarationOrigin.Source,
                                propertyTypeRef = returnType.copyWithNewSourceKind(KtFakeSourceElementKind.DefaultAccessor.Getter),
                                visibility = propertyVisibility,
                                propertySymbol = symbol,
                                modality = calculatedModifiers.getModality(isClassOrObject = false),
                            ).also {
                                it.status = defaultAccessorStatus()
                                it.replaceAnnotations(propertyAnnotations.filterUseSiteTarget(PROPERTY_GETTER))
                                it.initContainingClassAttr()
                            }
                        // NOTE: We still need the setter even for a val property, so we can report errors (e.g., VAL_WITH_SETTER).
                        this.setter = convertedAccessors.find { it.isSetter }
                            ?: if (isVar) {
                                FirDefaultPropertySetter(
                                    source = property.toFirSourceElement(KtFakeSourceElementKind.DefaultAccessor.Setter),
                                    moduleData = moduleData,
                                    origin = FirDeclarationOrigin.Source,
                                    propertyTypeRef = returnType
                                        .copyWithNewSourceKind(KtFakeSourceElementKind.DefaultAccessor.Setter),
                                    visibility = propertyVisibility,
                                    propertySymbol = symbol,
                                    modality = calculatedModifiers.getModality(isClassOrObject = false),
                                    parameterAnnotations = propertyAnnotations.filterUseSiteTarget(SETTER_PARAMETER),
                                ).also {
                                    it.status = defaultAccessorStatus()
                                    it.replaceAnnotations(propertyAnnotations.filterUseSiteTarget(PROPERTY_SETTER))
                                    it.initContainingClassAttr()
                                }
                            } else null

                        status = FirDeclarationStatusImpl(
                            propertyVisibility, calculatedModifiers.getModality(isClassOrObject = false)
                        ).apply {
                            isExpect = calculatedModifiers.hasExpect() || context.containerIsExpect
                            isActual = calculatedModifiers.hasActual()
                            isOverride = calculatedModifiers.hasOverride()
                            isConst = calculatedModifiers.isConst()
                            isLateInit = calculatedModifiers.hasLateinit()
                            isExternal = calculatedModifiers.hasExternal()
                            this.isStatic = isStatic
                        }

                        generateAccessorsByDelegate(
                            delegateBuilder,
                            baseModuleData,
                            runUnless(isStatic) { classWrapper?.classBuilder?.ownerRegularOrAnonymousObjectSymbol },
                            context,
                            isExtension = receiverTypeNode != null && !isStatic,
                            explicitDeclarationSource = propertySource,
                        )
                    }
                }
                annotations += when {
                    isLocal -> propertyAnnotations
                    else -> propertyAnnotations.filterStandalonePropertyRelevantAnnotations(isVar)
                }

                contextParameters.addContextParameters(modifiers?.contextLists, propertySymbol)
            }.also {
                if (!isLocal) {
                    fillDanglingConstraintsTo(firTypeParameters, typeConstraints, it)

                    if (isCompanionBlockMember) {
                        it.initContainingClassAttr()
                    }
                }
            }
        }
    }

    private fun buildFirDestructuringDeclarationInitializer(destructuringDeclaration: Node): FirExpression {
        val initializer = destructuringDeclaration.getChildExpression().takeUnless { it?.tokenType == PROPERTY_DELEGATE }
        return expressionConverter.getAsFirExpression(
            initializer,
            "Initializer required for destructuring declaration",
            sourceWhenInvalidExpression = destructuringDeclaration
        )
    }

    private fun buildErrorNonLocalDeclarationForDanglingModifierList(node: Node): FirDanglingModifierList {
        return buildDanglingModifierList {
            this.source = node.toFirSourceElement(KtFakeSourceElementKind.DanglingModifierList)
            moduleData = baseModuleData
            origin = FirDeclarationOrigin.Source
            diagnostic = ConeDanglingModifierOnTopLevel
            symbol = FirDanglingModifierSymbol()
            context.withContainerSymbol(symbol) {
                val modifiers = convertModifierList(node)
                contextParameters.addContextParameters(modifiers.contextLists, symbol)
                modifiers.convertAnnotationsTo(annotations)
            }
        }.apply {
            containingClassAttr = currentDispatchReceiverType()?.lookupTag
        }
    }

    /**
     * @see org.jetbrains.kotlin.parsing.KotlinParsing.parseTypeRef
     */
    fun convertType(type: Node): FirTypeRef {
        val typeRefSource = type.toFirSourceElement()

        // There can be MODIFIER_LIST children on the TYPE_REFERENCE node AND the descendant NULLABLE_TYPE nodes.
        // We aggregate them to get modifiers and annotations. Not only that, there could be multiple modifier lists on each. Examples:
        //
        //   `@A() (@B Int)`   -> Has 2 modifier lists (@A and @B) in TYPE_REFERENCE
        //   `(@A() (@B Int))? -> No modifier list on TYPE_REFERENCE, but 2 modifier lists (@A and @B) on child NULLABLE_TYPE
        //   `@A() (@B Int)?   -> Has 1 modifier list (@A) on TYPE_REFERENCE, and 1 modifier list (@B) on child NULLABLE_TYPE
        //   `@A (@B() (@C() (@Bar D)?)?)?` -> Has 1 modifier list (@A) on B and 1 modifier list on each of the
        //                                     3 descendant NULLABLE_TYPE (@B, @C, @D)
        //
        // We need to examine all modifier lists for some cases:
        // 1. `@A Int?` and `(@A Int)?` are effectively the same, but in the latter, the modifier list is on the child NULLABLE_TYPE
        // 2. `(suspend @A () -> Int)?` is a nullable suspend function type, but the modifier list is on the child NULLABLE_TYPE
        //
        val allTypeModifiers = mutableListOf<ModifierList>()

        var firType: FirTypeRef? = null
        type.forEachChildren { child ->
            val tokenType = child.elementType
            when {
                tokenType.isTypeReference() -> firType = convertType(child)
                tokenType.isModifierList() -> allTypeModifiers += convertModifierList(child)
                tokenType.isUserType() -> firType = convertUserType(typeRefSource, child)
                tokenType.isNullableType() -> firType = convertNullableType(typeRefSource, child, allTypeModifiers)
                tokenType.isFunctionType() -> firType = convertFunctionType(typeRefSource, child, allTypeModifiers)
                tokenType.isDynamicType() -> firType = buildDynamicTypeRef {
                    source = typeRefSource
                    isMarkedNullable = false
                }
                tokenType.isIntersectionType() -> firType = convertIntersectionType(typeRefSource, child, false)
                CONTEXT_PARAMETER_LIST, TokenType.ERROR_ELEMENT -> firType =
                    buildErrorTypeRef {
                        source = typeRefSource
                        diagnostic = ConeSyntaxDiagnostic("Unwrapped type is null")
                    }
            }
        }

        val calculatedFirType = firType ?: buildErrorTypeRef {
            source = typeRefSource
            diagnostic = ConeSyntaxDiagnostic("Incomplete code")
        }

        for (modifierList in allTypeModifiers) {
            calculatedFirType.replaceAnnotations(calculatedFirType.annotations.smartPlus(modifierList.convertAnnotations()))
        }
        return calculatedFirType
    }

    /**
     * @see org.jetbrains.kotlin.parsing.KotlinParsing.parseUserType
     */
    private fun convertUserType(
        typeRefSource: KtSourceElement,
        userType: Node,
        isNullable: Boolean = false
    ): FirTypeRef {
        var simpleFirUserType: FirUserTypeRef? = null
        var identifier: String? = null
        var identifierSource: KtSourceElement? = null
        val firTypeArguments = mutableListOf<FirTypeProjection>()
        var typeArgumentsSource: KtSourceElement? = null
        userType.forEachChildren {
            when (it.tokenType) {
                USER_TYPE -> simpleFirUserType = convertUserType(typeRefSource, it) as? FirUserTypeRef //simple user type
                REFERENCE_EXPRESSION -> {
                    identifierSource = it.toFirSourceElement()
                    identifier = it.asText
                }
                TYPE_ARGUMENT_LIST -> {
                    typeArgumentsSource = it.toFirSourceElement()
                    firTypeArguments += convertTypeArguments(it, allowedUnderscoredTypeArgument = false)
                }
            }
        }

        if (identifier == null) {
            return buildErrorTypeRef {
                source = typeRefSource
                diagnostic = ConeSyntaxDiagnostic("Incomplete user type")
                simpleFirUserType?.let { qualifierPart ->
                    if (qualifierPart.qualifier.isNotEmpty()) {
                        partiallyResolvedTypeRef = buildUserTypeRef {
                            source = qualifierPart.qualifier.last().source!!
                            isMarkedNullable = false
                            this.qualifier.addAll(qualifierPart.qualifier)
                        }
                    }
                }
            }
        }

        val qualifierPart = FirQualifierPartImpl(
            identifierSource!!,
            identifier.nameAsSafeName(),
            FirTypeArgumentListImpl(typeArgumentsSource ?: typeRefSource).apply {
                typeArguments += firTypeArguments
            }
        )

        return buildUserTypeRef {
            source = typeRefSource
            isMarkedNullable = isNullable
            qualifier.add(qualifierPart)
            simpleFirUserType?.qualifier?.let { this.qualifier.addAll(0, it) }
        }
    }

    /**
     * @see org.jetbrains.kotlin.parsing.KotlinParsing.parseNullableTypeSuffix
     */
    private fun convertNullableType(
        typeRefSource: KtSourceElement,
        nullableType: Node,
        allTypeModifiers: MutableList<ModifierList>,
        isNullable: Boolean = true
    ): FirTypeRef {
        lateinit var firType: FirTypeRef
        nullableType.forEachChildren { child ->
            val tokenType = child.elementType
            when {
                tokenType.isModifierList() -> allTypeModifiers += convertModifierList(child)
                tokenType.isUserType() -> firType = convertUserType(typeRefSource, child, isNullable)
                tokenType.isFunctionType() -> firType = convertFunctionType(typeRefSource, child, allTypeModifiers, isNullable)
                tokenType.isNullableType() -> firType = convertNullableType(typeRefSource, child, allTypeModifiers)
                tokenType.isDynamicType() -> firType = buildDynamicTypeRef {
                    source = typeRefSource
                    isMarkedNullable = true
                }
                tokenType.isIntersectionType() -> firType = convertIntersectionType(typeRefSource, child, isNullable)
            }
        }

        return firType
    }

    /**
     * @see org.jetbrains.kotlin.parsing.KotlinParsing.parseFunctionType
     */
    private fun convertFunctionType(
        typeRefSource: KtSourceElement,
        functionType: Node,
        allTypeModifiers: List<ModifierList>,
        isNullable: Boolean = false,
    ): FirTypeRef {
        var receiverTypeReference: FirTypeRef? = null
        lateinit var returnTypeReference: FirTypeRef
        val parameters = mutableListOf<FirFunctionTypeParameter>()
        var contextList: Node? = null
        functionType.forEachChildren {
            when (it.tokenType) {
                FUNCTION_TYPE_RECEIVER -> receiverTypeReference = convertReceiverType(it)
                VALUE_PARAMETER_LIST -> parameters += convertFunctionTypeParameters(it)
                TYPE_REFERENCE -> returnTypeReference = convertType(it)
                CONTEXT_PARAMETER_LIST -> contextList = it
            }
        }

        return buildFunctionTypeRef {
            source = typeRefSource
            isMarkedNullable = isNullable
            receiverTypeRef = receiverTypeReference
            returnTypeRef = returnTypeReference
            this.parameters += parameters
            isSuspend = allTypeModifiers.any { it.hasSuspend() }

            contextList?.forEachChildren {
                when (it.elementType) {
                    CONTEXT_RECEIVER, VALUE_PARAMETER -> {
                        val typeReference = it.getChildNodeByType(TYPE_REFERENCE)

                        contextParameterTypeRefs += typeReference?.let(::convertType)
                            ?: buildErrorTypeRef { diagnostic = ConeSimpleDiagnostic("Type missing") }
                    }
                }
            }
        }
    }
}
