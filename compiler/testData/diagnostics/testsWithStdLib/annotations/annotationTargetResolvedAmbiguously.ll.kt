// LL_FIR_DIVERGENCE
// State Keeper for ANNOTATION_ARGUMENTS phase currently rebuilds annotation arguments
// anew. Thus, it can delete results from previous COMPILER_REQUIRED_ARGUMENTS phase.
// LL_FIR_DIVERGENCE
// RUN_PIPELINE_TILL: FRONTEND
import kotlin.annotation.AnnotationTarget.FIELD

object Some {
    @Target(<!ARGUMENT_TYPE_MISMATCH!>AnnotationTarget.CLASS<!>)
    annotation class Ann

    enum class AnnotationTarget {
        CLASS
    }

    @Target(<!ARGUMENT_TYPE_MISMATCH!>FIELD<!>)
    annotation class Ann2

    const val FIELD = ""
}

object SomeMore {
    @Target(<!ARGUMENT_TYPE_MISMATCH!>kotlin.annotation.AnnotationTarget.FUNCTION<!>)
    annotation class Ann3

    object kotlin {
        object annotation {
            enum class AnnotationTarget {
                FUNCTION
            }
        }
    }
}

abstract class Base {
    annotation class Target(val target: AnnotationTarget)

    enum class AnnotationTarget {
        TYPE
    }
}

class Derived : Base() {
    @<!COMPILER_REQUIRED_ANNOTATION_AMBIGUITY!>Target<!>(<!ARGUMENT_TYPE_MISMATCH!>AnnotationTarget.TYPE<!>)
    annotation class Ann

    fun foo(x: @Ann String) {}
}

/* GENERATED_FIR_TAGS: annotationDeclaration, classDeclaration, const, enumDeclaration, enumEntry, functionDeclaration,
nestedClass, objectDeclaration, primaryConstructor, propertyDeclaration, stringLiteral */
