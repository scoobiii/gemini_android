/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.client.generativeai.common

import com.google.ai.client.generativeai.common.client.FunctionCallingConfig
import com.google.ai.client.generativeai.common.client.ToolConfig
import com.google.ai.client.generativeai.common.shared.Content
import com.google.ai.client.generativeai.common.shared.TextPart
import com.google.ai.client.generativeai.common.util.commonTest
import com.google.ai.client.generativeai.common.util.createResponses
import com.google.ai.client.generativeai.common.util.doBlocking
import com.google.ai.client.generativeai.common.util.prepareStreamingResponse
import io.kotest.assertions.json.shouldContainJsonKey
import io.kotest.assertions.json.shouldNotContainJsonKey
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.content.TextContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.writeFully
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

internal class APIControllerTests {
  private val testTimeout = 5.seconds

  @Test
  fun `(generateContentStream) emits responses as they come in`() = commonTest {
    val response = createResponses("The", " world", " is", " a", " beautiful", " place!")
    val bytes = prepareStreamingResponse(response)

    bytes.forEach { channel.writeFully(it) }
    val responses = apiController.generateContentStream(textGenerateContentRequest("test"))

    withTimeout(testTimeout) {
      responses.collect {
        it.candidates?.isEmpty() shouldBe false
        channel.close()
      }
    }
  }

  @Test
  fun `(generateContent) respects a custom timeout`() =
    commonTest(requestOptions = RequestOptions(2.seconds)) {
      shouldThrow<RequestTimeoutException> {
        withTimeout(testTimeout) {
          apiController.generateContent(textGenerateContentRequest("test"))
        }
      }
    }
}

internal class RequestFormatTests {
  @Test
  fun `using default endpoint`() = doBlocking {
    val channel = ByteChannel(autoFlush = true)
    val mockEngine = MockEngine {
      respond(channel, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    prepareStreamingResponse(createResponses("Random")).forEach { channel.writeFully(it) }
    val controller =
      APIController("super_cool_test_key", "gemini-pro-1.0", RequestOptions(), mockEngine)

    withTimeout(5.seconds) {
      controller.generateContentStream(textGenerateContentRequest("cats")).collect {
        it.candidates?.isEmpty() shouldBe false
        channel.close()
      }
    }

    mockEngine.requestHistory.first().url.host shouldBe "generativelanguage.googleapis.com"
  }

  @Test
  fun `using custom endpoint`() = doBlocking {
    val channel = ByteChannel(autoFlush = true)
    val mockEngine = MockEngine {
      respond(channel, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    prepareStreamingResponse(createResponses("Random")).forEach { channel.writeFully(it) }
    val controller =
      APIController(
        "super_cool_test_key",
        "gemini-pro-1.0",
        RequestOptions(endpoint = "https://my.custom.endpoint"),
        mockEngine
      )

    withTimeout(5.seconds) {
      controller.generateContentStream(textGenerateContentRequest("cats")).collect {
        it.candidates?.isEmpty() shouldBe false
        channel.close()
      }
    }

    mockEngine.requestHistory.first().url.host shouldBe "my.custom.endpoint"
  }

  @Test
  fun `generateContentRequest doesn't include the model name`() = doBlocking {
    val channel = ByteChannel(autoFlush = true)
    val mockEngine = MockEngine {
      respond(channel, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    prepareStreamingResponse(createResponses("Random")).forEach { channel.writeFully(it) }
    val controller =
      APIController("super_cool_test_key", "gemini-pro-1.0", RequestOptions(), mockEngine)

    withTimeout(5.seconds) {
      controller.generateContentStream(textGenerateContentRequest("cats")).collect {
        it.candidates?.isEmpty() shouldBe false
        channel.close()
      }
    }

    val requestBodyAsText = (mockEngine.requestHistory.first().body as TextContent).text
    requestBodyAsText shouldContainJsonKey "contents"
    requestBodyAsText shouldNotContainJsonKey "model"
  }

  @Test
  fun `countTokenRequest doesn't include the model name`() = doBlocking {
    val response = JSON.encodeToString(CountTokensResponse(totalTokens = 10))
    val mockEngine = MockEngine {
      respond(response, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    val controller =
      APIController("super_cool_test_key", "gemini-pro-1.0", RequestOptions(), mockEngine)

    withTimeout(5.seconds) { controller.countTokens(textCountTokenRequest("cats")) }

    val requestBodyAsText = (mockEngine.requestHistory.first().body as TextContent).text
    requestBodyAsText shouldContainJsonKey "contents"
    requestBodyAsText shouldNotContainJsonKey "model"
  }

  @Test
  fun `ToolConfig serialization contains correct keys`() = doBlocking {
    val channel = ByteChannel(autoFlush = true)
    val mockEngine = MockEngine {
      respond(channel, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    prepareStreamingResponse(createResponses("Random")).forEach { channel.writeFully(it) }

    val controller =
      APIController("super_cool_test_key", "gemini-pro-1.0", RequestOptions(), mockEngine)

    withTimeout(5.seconds) {
      controller
        .generateContentStream(
          GenerateContentRequest(
            model = "unused",
            contents = listOf(Content(parts = listOf(TextPart("Arbitrary")))),
            toolConfig =
              ToolConfig(
                functionCallingConfig =
                  FunctionCallingConfig(mode = FunctionCallingConfig.Mode.AUTO)
              )
          )
        )
        .collect { channel.close() }
    }

    val requestBodyAsText = (mockEngine.requestHistory.first().body as TextContent).text

    requestBodyAsText shouldContainJsonKey "tool_config.function_calling_config.mode"
  }
}

@RunWith(Parameterized::class)
internal class ModelNamingTests(private val modelName: String, private val actualName: String) {

  @Test
  fun `request should include right model name`() = doBlocking {
    val channel = ByteChannel(autoFlush = true)
    val mockEngine = MockEngine {
      respond(channel, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    prepareStreamingResponse(createResponses("Random")).forEach { channel.writeFully(it) }
    val controller = APIController("super_cool_test_key", modelName, RequestOptions(), mockEngine)

    withTimeout(5.seconds) {
      controller.generateContentStream(textGenerateContentRequest("cats")).collect {
        it.candidates?.isEmpty() shouldBe false
        channel.close()
      }
    }

    mockEngine.requestHistory.first().url.encodedPath shouldContain actualName
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters
    fun data() =
      listOf(
        arrayOf("gemini-pro", "models/gemini-pro"),
        arrayOf("x/gemini-pro", "x/gemini-pro"),
        arrayOf("models/gemini-pro", "models/gemini-pro"),
        arrayOf("/modelname", "/modelname"),
        arrayOf("modifiedNaming/mymodel", "modifiedNaming/mymodel"),
      )
  }
}

fun textGenerateContentRequest(prompt: String) =
  GenerateContentRequest(
    model = "unused",
    contents = listOf(Content(parts = listOf(TextPart(prompt))))
  )

fun textCountTokenRequest(prompt: String) =
  CountTokensRequest(model = "unused", contents = listOf(Content(parts = listOf(TextPart(prompt)))))
