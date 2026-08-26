// WITH_STDLIB
// LIBRARY_PLATFORMS: JVM
// JVM_EXPOSE_BOXED

@JvmInline
value class ValueInt(val i: Int)

interface Iface {
    var valueInt: ValueInt
}

class Clazz : Iface {
    override var valueInt: ValueInt = ValueInt(42)
}

// DECLARATIONS_NO_LIGHT_ELEMENTS: Iface.class[valueInt]
// LIGHT_ELEMENTS_NO_DECLARATION: Clazz.class[getValueInt-EIAok-E;setValueInt-1UXusoc], Iface.class[getValueInt-EIAok-E;setValueInt-1UXusoc], ValueInt.class[constructor-impl;equals-impl;equals-impl0;hashCode-impl;toString-impl]
