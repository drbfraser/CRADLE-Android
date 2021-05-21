package com.cradleplatform.neptune.utilities

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HostnameValidationTests {

    @Test
    fun validatePort_validPort_isValid() {
        val result = validatePort("8080")
        assertTrue(result.isRight)
    }

    @Test
    fun validatePort_1_isValid() {
        val result = validatePort("1")
        assertTrue(result.isRight)
    }

    @Test
    fun validatePort_emptyString_isValid() {
        val result = validatePort("")
        assertTrue(result.isRight)
    }

    @Test
    fun validatePort_blankString_isNotValid() {
        val result = validatePort(" ")
        assertTrue(result.isLeft)
    }

    @Test
    fun validatePort_0_isNotValid() {
        val result = validatePort("0")
        assertTrue(result.isLeft)
    }

    @Test
    fun validatePort_negativeInteger_isNotValid() {
        val result = validatePort("-100")
        assertTrue(result.isLeft)
    }

    @Test
    fun validateHostname_singleSubDomain_isValid() {
        val result = validateHostname("example-hostname")
        assertTrue(result.isRight)
    }

    @Test
    fun validateHostname_fqdn_isValid() {
        val result = validateHostname("sample10.domain.example-hostname.com")
        assertTrue(result.isRight)
    }

    @Test
    fun validateHostname_emptyString_isNotValid() {
        val result = validateHostname("")
        assertTrue(result.isLeft)
    }

    @Test
    fun validateHostname_blankString_isNotValid() {
        val result = validateHostname(" ")
        assertTrue(result.isLeft)
    }

    @Test
    fun validateHostname_subDomainWithSpace_isNotValid() {
        val result = validateHostname("sample.example domain.come")
        assertTrue(result.isLeft)
    }

    @Test
    fun validateHostname_emptySubDomain_isNotValid() {
        val result = validateHostname("sample..domain.com")
        assertTrue(result.isLeft)
    }

    @Test
    fun validateHostname_subDomainWithIllegalCharacter_isNotValid() {
        val result = validateHostname("sample.*.com")
        assertTrue(result.isLeft)
    }

    @Test
    fun validateHostname_nonAscii_isNotValid() {
        val result = validateHostname("s\u00e1mple.example.com")
        assertTrue(result.isLeft)
    }
}
