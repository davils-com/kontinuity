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

/**
 * Why a call through a [Stage] ended without a result, as [attempt] reports it.
 *
 * @since 1.0.0
 */
public sealed interface CallFailure {
    /**
     * The exception the call ended with.
     *
     * @since 1.0.0
     */
    public val cause: Throwable

    /**
     * The call ran and failed with [cause].
     *
     * @property cause the failure of the call.
     * @since 1.0.0
     */
    public class Failed(override val cause: Throwable) : CallFailure {
        /**
         * Tells whether [other] is a failure with the same cause.
         *
         * @param other the object to compare with.
         * @return true if [other] is equal.
         * @since 1.0.0
         */
        override fun equals(other: Any?): Boolean = other is Failed && other.cause == cause

        /**
         * Returns a hash of the cause.
         *
         * @return the hash.
         * @since 1.0.0
         */
        override fun hashCode(): Int = cause.hashCode()

        /**
         * Shows the type of the cause, without its message, which may hold secrets.
         *
         * @return the failure as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Failed(${cause::class.simpleName ?: "Throwable"})"
    }

    /**
     * An instance of kontinuity refused the call, which never ran.
     *
     * @property cause the refusal.
     * @since 1.0.0
     */
    public class Rejected(override val cause: CallRejectedException) : CallFailure {
        /**
         * Tells whether [other] is a rejection with the same cause.
         *
         * @param other the object to compare with.
         * @return true if [other] is equal.
         * @since 1.0.0
         */
        override fun equals(other: Any?): Boolean = other is Rejected && other.cause == cause

        /**
         * Returns a hash of the cause.
         *
         * @return the hash.
         * @since 1.0.0
         */
        override fun hashCode(): Int = cause.hashCode()

        /**
         * Shows the kind and the name of the instance that refused the call.
         *
         * @return the rejection as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Rejected(${cause.kind.id} ${cause.instance})"
    }
}
