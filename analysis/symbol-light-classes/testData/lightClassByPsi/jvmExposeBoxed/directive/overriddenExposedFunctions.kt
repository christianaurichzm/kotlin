// WITH_STDLIB
// LIBRARY_PLATFORMS: JVM
// JVM_EXPOSE_BOXED

@JvmInline
value class ValueInt(val i: Int)

interface Iface {
    fun useValueInt(valueInt: ValueInt)
    fun returnValueInt(): ValueInt
}

class Clazz : Iface {
    override fun useValueInt(valueInt: ValueInt) {}

    override fun returnValueInt(): ValueInt = ValueInt(42)
}

// DECLARATIONS_NO_LIGHT_ELEMENTS: Iface.class[returnValueInt;useValueInt]
// LIGHT_ELEMENTS_NO_DECLARATION: Clazz.class[returnValueInt-EIAok-E;useValueInt-1UXusoc], Iface.class[returnValueInt-EIAok-E;useValueInt-1UXusoc], ValueInt.class[constructor-impl;equals-impl;equals-impl0;hashCode-impl;toString-impl]
