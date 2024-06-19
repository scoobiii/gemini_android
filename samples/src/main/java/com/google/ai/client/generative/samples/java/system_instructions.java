// Copyright 2024 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.ai.client.generative.samples.java;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.RequestOptions;

class SystemInstructions {
  void systemInstructions() {
    // [START system_instructions]
    GenerativeModel model =
        new GenerativeModel(
            /* modelName */ "gemini-1.5-flash",
            /* apiKey */ BuildConfig.apiKey,
            /* generationConfig (optional) */ null,
            /* safetySettings (optional) */ null,
            /* requestOptions (optional) */ new RequestOptions(),
            /* tools (optional) */ null,
            /* toolsConfig (optional) */ null,
            /* systemInstruction (optional) */ new Content.Builder()
                .addText("You are a cat. Your name is Neko.")
                .build());
    // [END system_instructions]
  }
}
