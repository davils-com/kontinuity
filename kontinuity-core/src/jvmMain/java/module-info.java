/*
 * Copyright 2026 Davils
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

module com.davils.kontinuity.core {
    requires transitive kotlin.stdlib;
    requires transitive com.davils.kore.core;
    requires transitive com.davils.konfetti.core;
    requires transitive com.davils.kollect.core;
    requires transitive kotlinx.coroutines.core;

    exports com.davils.kontinuity.core;
    exports com.davils.kontinuity.core.backoff;
    exports com.davils.kontinuity.core.fallback;
    exports com.davils.kontinuity.core.filter;
    exports com.davils.kontinuity.core.registry;
    exports com.davils.kontinuity.core.telemetry;
}
