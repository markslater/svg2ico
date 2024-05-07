/*
 * Copyright 2024 Mark Slater
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package release.github

import argo.JsonParser
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import net.sourceforge.urin.Authority
import net.sourceforge.urin.Authority.authority
import net.sourceforge.urin.Host.registeredName
import net.sourceforge.urin.Path.path
import net.sourceforge.urin.scheme.http.HttpQuery.queryParameter
import net.sourceforge.urin.scheme.http.HttpQuery.queryParameters
import net.sourceforge.urin.scheme.http.Https.https
import release.VersionNumber
import release.github.GitHubHttp.AuditEvent.RequestCompleted
import release.pki.ReleaseTrustStore
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class GitHubHttp(
    gitHubApiAuthority: GitHubApiAuthority,
    releaseTrustStore: ReleaseTrustStore,
    private val auditor: Auditor<AuditEvent>,
    connectTimeout: Duration = 1.seconds,
    private val firstByteTimeout: Duration = 2.seconds,
    private val endToEndTimeout: Duration = 2.seconds,
) : GitHub {

    private val releasesUri = https(
        gitHubApiAuthority.authority,
        path("repos", "svg2ico", "svg2ico", "releases"),
        queryParameters(queryParameter("per_page", "1"))
    ).asUri()
    private val httpClient = HttpClient.newBuilder().sslContext(releaseTrustStore.sslContext).connectTimeout(connectTimeout.toJavaDuration()).build()

    override fun latestReleaseVersion() = runBlocking(IO) {
        kotlin.runCatching {
            httpClient.sendAsync(
                HttpRequest.newBuilder(releasesUri)
                    .GET()
                    .setHeader("content-type", "application/json")
                    .setHeader("accept", "application/vnd.github+json")
                    .setHeader("x-github-api-version", "2022-11-28")
                    .timeout(firstByteTimeout.toJavaDuration())
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            ).orTimeout(endToEndTimeout.inWholeMilliseconds, MILLISECONDS).await().let { response ->
                runCatching {
                    auditor.event(RequestCompleted(releasesUri, response.statusCode(), response.body()))
                    if (response.statusCode() != 200) {
                        GitHub.ReleaseVersionOutcome.Failure(
                            Failure.InvalidResponseCode(
                                releasesUri,
                                response.statusCode(),
                                200,
                                response.body()
                            )
                        )
                    } else {
                        GitHub.ReleaseVersionOutcome.Success(
                            JsonParser().parse(response.body()).getArrayNode().map { release ->
                                release.getStringValue("tag_name")
                            }.map { releaseString ->
                                VersionNumber.fromString(releaseString)
                            }.maxOf { it }
                        )
                    }
                }.getOrElse { exception ->
                    GitHub.ReleaseVersionOutcome.Failure(Failure.ResponseHandlingException(releasesUri, response.statusCode(), response.body(), exception))
                }
            }
        }.getOrElse { exception ->
            auditor.event(AuditEvent.RequestFailed(releasesUri, exception))
            GitHub.ReleaseVersionOutcome.Failure(Failure.RequestSubmittingException(releasesUri, exception))
        }
    }

    data class GitHubApiAuthority(val authority: Authority) {
        companion object {
            val productionGitHubApi = GitHubApiAuthority(authority(registeredName("api.github.com")))
        }
    }

    sealed interface AuditEvent {
        data class RequestCompleted(val uri: URI, val statusCode: Int, val responseBody: String) : AuditEvent
        data class RequestFailed(val uri: URI, val cause: Throwable) : AuditEvent
    }

}