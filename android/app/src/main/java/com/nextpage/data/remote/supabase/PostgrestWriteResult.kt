package com.nextpage.data.remote.supabase

import io.github.jan.supabase.postgrest.result.PostgrestResult

/**
 * Decode a single row from a PostgREST **write** response, tolerating an empty body.
 *
 * PostgREST writes (`upsert` / `insert` / `update` / `delete`) may legally return:
 *  - `204 No Content` (the representation is stripped by RLS or an intermediary), or
 *  - a `201 Created` whose body is empty because the server was configured with
 *    `Prefer: return=minimal`.
 *
 * The SDK's [PostgrestResult.decodeSingleOrNull] first decodes the body as a JSON
 * array (`typeOf<List<T>>()`), so an empty body throws a [kotlinx.serialization.SerializationException]
 * with `Expected start of the array '[', but had 'EOF' instead at path: $`. For a
 * WRITE an empty body is a SUCCESS, not a failure — only reads must stay strict.
 *
 * Callers get `null` on an empty/whitespace-only body and should fall back to the
 * row they sent (all write callers already own the primary key of the row). A
 * non-empty but malformed body still throws, so genuine protocol errors are never
 * swallowed.
 */
internal inline fun <reified T : Any> PostgrestResult.decodeSingleOrNullTolerant(): T? =
    if (data.isBlank()) null else decodeSingleOrNull<T>()
