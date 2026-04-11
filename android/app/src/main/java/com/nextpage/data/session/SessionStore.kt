package com.nextpage.data.session

import com.nextpage.domain.model.AuthSession

interface SessionStore {
    fun read(): AuthSession?
    fun write(session: AuthSession)
    fun clear()
}
