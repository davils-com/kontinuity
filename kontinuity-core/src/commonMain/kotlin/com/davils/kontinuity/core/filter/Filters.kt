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

package com.davils.kontinuity.core.filter

/**
 * Builds a validated failure filter: `failures { record<IOException>(); ignore<BusinessException>() }`.
 *
 * @param configure declares the rules.
 * @return the filter.
 * @throws com.davils.kore.core.validation.report.ValidationException if the filter has more than
 *   [FailureFilter.HARD_RULES] rules.
 * @since 1.0.0
 */
public fun failures(configure: FailureFilterBuilder.() -> Unit): FailureFilter {
    val builder = FailureFilterBuilder()
    builder.configure()
    return builder.build()
}

/**
 * Builds a validated result filter: `results { failIf<Response> { response -> response.status >= 500 } }`.
 *
 * @param configure declares the rules.
 * @return the filter.
 * @throws com.davils.kore.core.validation.report.ValidationException if the filter has more than
 *   [FailureFilter.HARD_RULES] rules.
 * @since 1.0.0
 */
public fun results(configure: ResultFilterBuilder.() -> Unit): ResultFilter {
    val builder = ResultFilterBuilder()
    builder.configure()
    return builder.build()
}
