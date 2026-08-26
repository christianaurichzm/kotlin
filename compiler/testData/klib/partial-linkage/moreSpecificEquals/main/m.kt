import abitestutils.abiTest

class D : WithRemovedBound()

fun box() = abiTest {
    expectSuccess(true) { removedEqualityBound() }
    expectSuccess(true) { addedEqualityBound() }
    expectSuccess(false) { differentClasses() }
    expectSuccess(true) { changedEqualityBound() }

    expectFailure(linkage("Constructor 'WithRemovedBound.<init>' can not be called: Class 'WithRemovedBound' uses unlinked class symbol '/RemovedClass'")) { withRemovedBoundEquals() }
    expectFailure(linkage("Constructor 'D.<init>' can not be called: Class 'D' uses unlinked class symbol '/RemovedClass'")) { D() }
    expectSuccess(false) { changedHierarchyEquals() }

    expectSuccess(true) { removedAliasEquals() }
    expectSuccess(true) { changedAliasEquals() }
}
