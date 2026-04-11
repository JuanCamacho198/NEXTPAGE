package com.nextpage.data.remote.supabase

import com.nextpage.domain.error.ErrorCategory
import io.github.jan.supabase.SupabaseClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class SupabaseClientProviderTest {

    @Test
    fun initDiagnostic_isSuccess_whenConfigValidAndFactoryReturnsClient() {
        val fakeClient = Proxy.newProxyInstance(
            SupabaseClient::class.java.classLoader,
            arrayOf(SupabaseClient::class.java)
        ) { _, _, _ -> null } as SupabaseClient

        val provider = SupabaseClientProvider(
            config = SupabaseConfig(
                url = "https://example.supabase.co",
                anonKey = "header.payload.signature"
            ),
            component = "SupabaseClientProviderTest",
            clientFactory = { _ -> fakeClient }
        )

        assertTrue(provider.isConfigured)
        assertEquals(SupabaseInitDiagnostic.Status.SUCCESS, provider.initDiagnostic.status)
        assertEquals("Supabase client initialized successfully.", provider.initDiagnostic.message)
    }

    @Test
    fun initDiagnostic_isConfigError_whenConfigInvalid() {
        val provider = SupabaseClientProvider(
            config = SupabaseConfig(url = "", anonKey = "invalid"),
            component = "SupabaseClientProviderTest"
        )

        assertNull(provider.client)
        assertTrue(!provider.isConfigured)
        assertEquals(SupabaseInitDiagnostic.Status.CONFIG_ERROR, provider.initDiagnostic.status)
        assertEquals(ErrorCategory.CONFIG_ERROR, provider.initDiagnostic.error?.category)
    }

    @Test
    fun initDiagnostic_isWiringError_whenFactoryReturnsNullWithValidConfig() {
        val provider = SupabaseClientProvider(
            config = SupabaseConfig(
                url = "https://example.supabase.co",
                anonKey = "header.payload.signature"
            ),
            component = "SupabaseClientProviderTest",
            clientFactory = { _ -> null }
        )

        assertNull(provider.client)
        assertTrue(!provider.isConfigured)
        assertEquals(SupabaseInitDiagnostic.Status.WIRING_ERROR, provider.initDiagnostic.status)
        assertEquals(ErrorCategory.WIRING_ERROR, provider.initDiagnostic.error?.category)
        assertEquals("SUPABASE_CLIENT_BOOTSTRAP_FAILED", provider.initDiagnostic.error?.code)
    }

    @Test
    fun initDiagnostic_isWiringError_whenFactoryThrowsWithValidConfig() {
        val provider = SupabaseClientProvider(
            config = SupabaseConfig(
                url = "https://example.supabase.co",
                anonKey = "header.payload.signature"
            ),
            component = "SupabaseClientProviderTest",
            clientFactory = { _ -> throw IllegalStateException("provider wiring broken") }
        )

        assertNull(provider.client)
        assertTrue(!provider.isConfigured)
        assertEquals(SupabaseInitDiagnostic.Status.WIRING_ERROR, provider.initDiagnostic.status)
        assertEquals(ErrorCategory.WIRING_ERROR, provider.initDiagnostic.error?.category)
        assertEquals("SUPABASE_CLIENT_BOOTSTRAP_FAILED", provider.initDiagnostic.error?.code)
        assertEquals("provider wiring broken", provider.initDiagnostic.error?.message)
    }

}
