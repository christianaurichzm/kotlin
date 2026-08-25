// ISSUE: KT-88707
// FILE: OuterJava.java

import lombok.NoArgsConstructor;

public class OuterJava {
    @NoArgsConstructor(force = true)
    public class Inner {
        public int value;

        public Inner(int value) {
            this.value = value;
        }
    }
}

// FILE: GenericOuterJava.java

import lombok.NoArgsConstructor;

public class GenericOuterJava<T> {
    @NoArgsConstructor(force = true)
    public class Inner {
        public T value;

        public Inner(T value) {
            this.value = value;
        }
    }
}

// FILE: test.kt

fun box(): String {
    val outer = OuterJava()
    val inner = outer.Inner()
    if (inner.value != 0) return "FAIL: ${inner.value}"

    val genericOuter = GenericOuterJava<String>()
    val genericInner = genericOuter.Inner()
    if (genericInner.value != null) return "FAIL: ${genericInner.value}"

    return "OK"
}
