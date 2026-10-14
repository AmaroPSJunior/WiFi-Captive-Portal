package br.com.portalwifi
import org.junit.Assert.*
import org.junit.Test
class VersionComparatorTest {
    @Test fun newerMinor() { assertTrue(VersionComparator.isNewer("v0.2.0", "0.1.0")) }
    @Test fun equal() { assertFalse(VersionComparator.isNewer("v1.2.3", "1.2.3")) }
    @Test fun old() { assertFalse(VersionComparator.isNewer("1.2.2", "1.2.3")) }
    @Test fun dev() { assertTrue(VersionComparator.isNewer("0.1.0", "0.1.0-dev.21")) }
    @Test fun invalid() { assertFalse(VersionComparator.isNewer("broken", "1.0.0")) }
}
