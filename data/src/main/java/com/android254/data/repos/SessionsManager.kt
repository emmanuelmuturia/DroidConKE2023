/*
 * Copyright 2022 DroidconKE
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android254.data.repos

import androidx.sqlite.db.SimpleSQLiteQuery
import com.android254.data.dao.BookmarkDao
import com.android254.data.dao.SessionDao
import com.android254.data.db.model.BookmarkEntity
import com.android254.data.di.IoDispatcher
import com.android254.data.network.apis.SessionsApi
import com.android254.data.repos.mappers.toDomainModel
import com.android254.domain.models.Session
import com.android254.domain.repos.SessionsRepo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SessionsManager @Inject constructor(
    private val api: SessionsApi,
    private val dao: SessionDao,
    private val bookmarkDao: BookmarkDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : SessionsRepo {

    override fun fetchSessions(): Flow<List<Session>> {
        val bookmarksFlow = bookmarkDao.getBookmarkIds()
        val sessionsFlow = dao.fetchSessions()
        return combine(sessionsFlow, bookmarksFlow) { sessions, bookmarks ->
            sessions.map { it.toDomainModel() }
                .map { session ->
                    session.copy(isBookmarked = bookmarks.map { it.sessionId }.contains(session.id))
                }
        }.flowOn(ioDispatcher)
    }
    override fun fetchBookmarkedSessions(): Flow<List<Session>> {
        val bookmarksFlow = bookmarkDao.getBookmarkIds()
        val sessionsFlow = dao.fetchSessions()
        return combine(sessionsFlow, bookmarksFlow) { sessions, bookmarks ->
            sessions.map { it.toDomainModel() }
                .map { session ->
                    session.copy(isBookmarked = bookmarks.map { it.sessionId }.contains(session.id))
                }
                .filter { session -> session.isBookmarked }
        }.flowOn(ioDispatcher)
    }

    override fun fetchFilteredSessions(query: String): Flow<List<Session>> {
        val filteredSessions = dao.fetchSessionsWithFilters(SimpleSQLiteQuery(query))
        val bookmarksFlow = bookmarkDao.getBookmarkIds()
        return combine(filteredSessions, bookmarksFlow) { sessions, bookmarks ->
            sessions.map { session ->
                session.copy(
                    isBookmarked = bookmarks.map { it.sessionId }.contains(session.id.toString())
                )
            }.map { it.toDomainModel() }
        }.flowOn(ioDispatcher)
    }

    override fun fetchSessionById(sessionId: String): Flow<Session?> {
        val bookmarksFlow = bookmarkDao.getBookmarkIds()
        val sessionFlow = dao.getSessionById(sessionId).map {
            it?.toDomainModel()
        }
        return combine(sessionFlow, bookmarksFlow) { session, bookmarks ->
            session?.copy(isBookmarked = bookmarks.map { it.sessionId }.contains(session.id))
        }.flowOn(ioDispatcher)
    }

    override suspend fun bookmarkSession(id: String) {
        withContext(ioDispatcher) {
            bookmarkDao.insert(BookmarkEntity(id))
        }
    }

    override suspend fun unBookmarkSession(id: String) {
        withContext(ioDispatcher) {
            bookmarkDao.delete(BookmarkEntity(id))
        }
    }
}