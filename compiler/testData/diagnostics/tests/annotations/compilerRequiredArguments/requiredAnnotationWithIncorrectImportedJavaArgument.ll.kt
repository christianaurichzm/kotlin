// LL_FIR_DIVERGENCE
// State Keeper for ANNOTATION_ARGUMENTS phase currently rebuilds annotation arguments
// anew. Thus, it can delete results from previous COMPILER_REQUIRED_ARGUMENTS phase.
// LL_FIR_DIVERGENCE
// RUN_PIPELINE_TILL: FRONTEND
// ISSUE: KT-75844
// FILE: JavaTarget.java
public enum JavaTarget {
    ANNOTATION_CLASS;
}

// FILE: MyAnnotation.kt
import JavaTarget.ANNOTATION_CLASS

@Target(<!ARGUMENT_TYPE_MISMATCH!>ANNOTATION_CLASS<!>)
annotation class MyAnnotation

/* GENERATED_FIR_TAGS: annotationDeclaration, javaProperty, javaType */
