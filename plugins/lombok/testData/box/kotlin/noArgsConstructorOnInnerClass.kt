// ISSUE: KT-88707
// FIR_DUMP

import lombok.NoArgsConstructor

class Outer {
    // The generated constructor takes the outer instance as its dispatch receiver, exactly as the declared one
    // does, so `outer.Inner()` resolves and compiles.
    @NoArgsConstructor(force = true)
    inner class Inner(val value: Int)

    // A nested class has no outer instance, and nothing about it changes.
    @NoArgsConstructor(force = true)
    class Nested(val value: Int)
}

// The outer class's type parameter belongs to the receiver, not to the constructed class, so the generated
// constructor must not re-declare it - `GenericOuter<String>().Inner()` could not infer `T` otherwise.
class GenericOuter<T> {
    @NoArgsConstructor(force = true)
    inner class Inner(val value: T)
}

fun box(): String {
    val outer = Outer()

    val inner = outer.Inner()
    assertEquals(0, inner.value)

    val nested = Outer.Nested()
    assertEquals(0, nested.value)

    val genericInner = GenericOuter<String>().Inner()
    assertEquals(null, genericInner.value)

    return "OK"
}
