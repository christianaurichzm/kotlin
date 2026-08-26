// ISSUE: KT-88707
// Lombok's static factory cannot exist on an inner class: the generated `static of()` has no enclosing instance
// to pass to `new Inner(...)`, and `javac` rejects the annotated Java class outright. The plugin generates
// nothing; the default constructor of each inner class stays private, the way `staticName` hides the
// constructor on any class.

// FILE: Outer.java
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;

public class Outer {
    @NoArgsConstructor(staticName = "of", force = true)
    public class InnerNoArgs {
        public int value;
    }

    @AllArgsConstructor(staticName = "of")
    public class InnerAllArgs {
        public int value;
    }

    @Data(staticConstructor = "of")
    public class InnerData {
        private final int value;
    }

    @Value(staticConstructor = "of")
    public class InnerValue {
        int value;
    }
}

// FILE: GenericOuter.java
import lombok.AllArgsConstructor;

public class GenericOuter<T> {
    @AllArgsConstructor(staticName = "of")
    public class Inner {
        public T value;
    }
}

// FILE: test.kt
fun test(outer: Outer, genericOuter: GenericOuter<String>) {
    Outer.InnerNoArgs.<!UNRESOLVED_REFERENCE!>of<!>()
    Outer.InnerAllArgs.<!UNRESOLVED_REFERENCE!>of<!>(1)
    Outer.InnerData.<!UNRESOLVED_REFERENCE!>of<!>(1)
    Outer.InnerValue.<!UNRESOLVED_REFERENCE!>of<!>(1)
    GenericOuter.Inner.<!UNRESOLVED_REFERENCE!>of<!>("a")

    outer.<!INVISIBLE_REFERENCE!>InnerNoArgs<!>()
    genericOuter.<!INVISIBLE_REFERENCE!>Inner<!>()
}
