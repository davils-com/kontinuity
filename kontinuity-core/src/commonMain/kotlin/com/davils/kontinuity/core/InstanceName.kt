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

package com.davils.kontinuity.core

import com.davils.kore.core.naming.NameRule
import com.davils.kore.core.naming.nameRule
import kotlin.jvm.JvmInline

private const val KIND: String = "kontinuity instance name"

/**
 * The name of a circuit breaker, retry, rate limiter or any other instance of kontinuity, such as `payments.charge`.
 *
 * A name is one or more parts joined by dots; a part starts with a lower-case ASCII letter and continues with
 * lower-case letters, digits and single `-` or `_`, without a separator at its end. It is at most [MAXIMUM_LENGTH]
 * characters long. The grammar keeps a name safe to show in every exception message, event, label value of kollect and
 * topic header without escaping, and it decides no complexity: it is checked by a [NameRule] of Kore, never by a
 * regular expression.
 *
 * @property value the name as text.
 * @since 1.0.0
 */
@JvmInline
public value class InstanceName private constructor(public val value: String) {
    /**
     * Returns the name as text.
     *
     * @return [value].
     * @since 1.0.0
     */
    override fun toString(): String = value

    /**
     * Creates and checks instance names.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The longest name: 128 characters.
         *
         * @since 1.0.0
         */
        public const val MAXIMUM_LENGTH: Int = 128

        /**
         * The grammar of a name, as described at [InstanceName].
         *
         * @since 1.0.0
         */
        public val RULE: NameRule = nameRule {
            separators = "-_"
            segmentSeparator = '.'
            isStrict = true
            maxLength = MAXIMUM_LENGTH
            example = "payments.charge"
        }

        /**
         * Returns the name [text], after checking it against [RULE].
         *
         * @param text the name, which may come from configuration.
         * @return the name.
         * @throws IllegalArgumentException if [text] does not follow [RULE]; the message shows it escaped and
         *   shortened.
         * @since 1.0.0
         */
        public fun of(text: String): InstanceName = InstanceName(RULE.requireValid(text, KIND))

        /**
         * Returns the name [text], or null if it does not follow [RULE].
         *
         * @param text the name, which may come from configuration.
         * @return the name, or null.
         * @since 1.0.0
         */
        public fun ofOrNull(text: String): InstanceName? {
            val valid = RULE.validOrNull(text) ?: return null
            return InstanceName(valid)
        }

        /**
         * Tells whether [text] follows [RULE].
         *
         * @param text the text to check.
         * @return true if [text] is a valid name.
         * @since 1.0.0
         */
        public fun isValid(text: CharSequence): Boolean = RULE.matches(text)
    }
}
