package com.nextpage.data.remote.supabase

import com.nextpage.domain.error.AppError
import com.nextpage.domain.error.ErrorCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseConfigTest {

    @Test
    fun validate_returnsConfigError_whenUrlMissing() {
        val config = SupabaseConfig(url = "", anonKey = "a.b.c")

        val result = config.validate()

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as AppError
        assertEquals(ErrorCategory.CONFIG_ERROR, error.category)
        assertEquals("SUPABASE_CONFIG_MISSING_URL", error.code)
    }

    @Test
    fun validate_returnsConfigError_whenAnonKeyMalformed() {
        val config = SupabaseConfig(url = "https://example.supabase.co", anonKey = "not-a-jwt")

        val result = config.validate()

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as AppError
        assertEquals(ErrorCategory.CONFIG_ERROR, error.category)
        assertEquals("SUPABASE_CONFIG_MALFORMED_ANON_KEY", error.code)
    }

    @Test
    fun validate_returnsConfigError_whenAnonKeyBlank() {
        val config = SupabaseConfig(url = "https://example.supabase.co", anonKey = "   ")

        val result = config.validate()

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as AppError
        assertEquals(ErrorCategory.CONFIG_ERROR, error.category)
        assertEquals("SUPABASE_CONFIG_MISSING_ANON_KEY", error.code)
    }

    @Test
    fun validate_returnsSuccess_whenConfigLooksValid() {
        val config = SupabaseConfig(
            url = "https://example.supabase.co",
            anonKey = "header.payload.signature"
        )

        val result = config.validate()

        assertTrue(result.isSuccess)
        assertTrue(config.isConfigured)
    }
}
