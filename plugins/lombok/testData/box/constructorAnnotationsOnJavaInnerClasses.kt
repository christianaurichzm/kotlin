// ISSUE: KT-88707
// FILE: Outer.java

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Value;

public class Outer {
    @RequiredArgsConstructor
    public class InnerRequiredArgs {
        public final int value;
        public int value2;
    }

    @AllArgsConstructor
    public class InnerAllArgs {
        public int value;
        public int value2;
    }

    @Data
    public class InnerData {
        private final int value;
        private int value2;
    }

    @Value
    public class InnerValue {
        int value;
    }

    public void usage() {
        InnerRequiredArgs requiredArgs = new InnerRequiredArgs(1);
        requiredArgs.value2 = 2;

        InnerAllArgs allArgs = new InnerAllArgs(1, 2);

        InnerData data = new InnerData(1);
        data.setValue2(2);
        data.getValue();
        data.toString();
        data.equals(new InnerData(1));

        InnerValue value = new InnerValue(1);
        value.getValue();
    }
}

// FILE: GenericOuter.java

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Value;

public class GenericOuter<T> {
    @RequiredArgsConstructor
    public class InnerRequiredArgs {
        public final T value;
        public T value2;
    }

    @AllArgsConstructor
    public class InnerAllArgs {
        public T value;
        public T value2;
    }

    @Data
    public class InnerData {
        private final T value;
        private T value2;
    }

    @Value
    public class InnerValue {
        T value;
    }

    public void usage() {
        GenericOuter<String> outer = new GenericOuter<String>();

        GenericOuter<String>.InnerRequiredArgs requiredArgs = outer.new InnerRequiredArgs("a");
        requiredArgs.value2 = "b";

        GenericOuter<String>.InnerAllArgs allArgs = outer.new InnerAllArgs("a", "b");

        GenericOuter<String>.InnerData data = outer.new InnerData("a");
        data.setValue2("b");
        data.getValue();
        data.toString();
        data.equals(outer.new InnerData("a"));

        GenericOuter<String>.InnerValue value = outer.new InnerValue("a");
        value.getValue();
    }
}

// FILE: test.kt

fun box(): String {
    val outer = Outer()

    val requiredArgs = outer.InnerRequiredArgs(1)
    if (requiredArgs.value != 1) return "FAIL requiredArgs.value: ${requiredArgs.value}"
    requiredArgs.value2 = 2
    if (requiredArgs.value2 != 2) return "FAIL requiredArgs.value2: ${requiredArgs.value2}"

    val allArgs = outer.InnerAllArgs(1, 2)
    if (allArgs.value != 1) return "FAIL allArgs.value: ${allArgs.value}"
    if (allArgs.value2 != 2) return "FAIL allArgs.value2: ${allArgs.value2}"

    val data = outer.InnerData(1)
    data.value2 = 2
    if (data.value != 1) return "FAIL data.value: ${data.value}"
    if (data.value2 != 2) return "FAIL data.value2: ${data.value2}"

    val value = outer.InnerValue(1)
    if (value.value != 1) return "FAIL value.value: ${value.value}"

    val genericOuter = GenericOuter<String>()

    val genericRequiredArgs = genericOuter.InnerRequiredArgs("a")
    if (genericRequiredArgs.value != "a") return "FAIL genericRequiredArgs.value: ${genericRequiredArgs.value}"
    genericRequiredArgs.value2 = "b"
    if (genericRequiredArgs.value2 != "b") return "FAIL genericRequiredArgs.value2: ${genericRequiredArgs.value2}"

    val genericAllArgs = genericOuter.InnerAllArgs("a", "b")
    if (genericAllArgs.value != "a") return "FAIL genericAllArgs.value: ${genericAllArgs.value}"
    if (genericAllArgs.value2 != "b") return "FAIL genericAllArgs.value2: ${genericAllArgs.value2}"

    val genericData = genericOuter.InnerData("a")
    if (genericData.value != "a") return "FAIL genericData.value: ${genericData.value}"
    genericData.value2 = "b"
    if (genericData.value2 != "b") return "FAIL genericData.value2: ${genericData.value2}"

    val genericDataSame = genericOuter.InnerData("a")
    genericDataSame.value2 = "b"
    val genericDataOther = genericOuter.InnerData("c")
    if (genericData != genericDataSame) return "FAIL genericData != genericDataSame"
    if (genericData.hashCode() != genericDataSame.hashCode()) return "FAIL genericData.hashCode() != genericDataSame.hashCode()"
    if (genericData == genericDataOther) return "FAIL genericData == genericDataOther"
    if (!genericData.toString().contains("value=a")) return "FAIL genericData.toString(): $genericData"

    val genericValue = genericOuter.InnerValue("a")
    if (genericValue.value != "a") return "FAIL genericValue.value: ${genericValue.value}"

    val genericValueSame = genericOuter.InnerValue("a")
    val genericValueOther = genericOuter.InnerValue("c")
    if (genericValue != genericValueSame) return "FAIL genericValue != genericValueSame"
    if (genericValue.hashCode() != genericValueSame.hashCode()) return "FAIL genericValue.hashCode() != genericValueSame.hashCode()"
    if (genericValue == genericValueOther) return "FAIL genericValue == genericValueOther"
    if (!genericValue.toString().contains("value=a")) return "FAIL genericValue.toString(): $genericValue"

    return "OK"
}
