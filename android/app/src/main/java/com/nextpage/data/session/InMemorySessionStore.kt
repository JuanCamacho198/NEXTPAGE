package com.nextpage.data.session

import com.nextpage.domain.model.AuthSession

class InMemorySessionStore : SessionStore {
    @Volatile
    private var cached: AuthSession? = null

    override fun read(): AuthSession? = cached

    override fun write(session: AuthSession) {
        cached = session
    }

    override fun clear() {
        cached = null
    }
}
