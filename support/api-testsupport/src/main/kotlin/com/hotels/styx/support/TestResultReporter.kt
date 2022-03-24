/*
  Copyright (C) 2013-2022 Expedia Inc.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package com.hotels.styx.support

import io.kotest.core.listeners.TestListener
import io.kotest.core.source.SourceRef
import io.kotest.core.spec.Spec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.core.test.TestStatus
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter

object TestResultReporter : TestListener {
    val LOGGER = LoggerFactory.getLogger("Styx-Tests")

    override suspend fun beforeSpec(spec: Spec) {
        super.beforeSpec(spec)
        //LOGGER.info("Starting: ${spec.description().fullName()}")
    }

    override suspend fun afterSpec(spec: Spec) {
        //LOGGER.info("Finished: ${spec.description().fullName()}")
        super.afterSpec(spec)
    }

    override suspend fun beforeTest(testCase: TestCase) {
        super.beforeTest(testCase)

        when (val source = testCase.source) {
            is SourceRef.FileSource -> LOGGER.info("Running: '${testCase.name}' - ${source.fileName}:${source.lineNumber}")
            is SourceRef.ClassSource -> LOGGER.info("Running: '${testCase.name}' - line:${source.lineNumber}")
            else -> LOGGER.info("Running: '${testCase.name}'")
        }
    }

    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        super.afterTest(testCase, result)

        LOGGER.info("Result: ${testCase.name} - ${result.status}")
        when (result.status) {
            TestStatus.Error -> {
                result.errorOrNull?.let {
                    LOGGER.info(it.message)
                    LOGGER.info(it.stackTrace())
                }
            }
            TestStatus.Failure -> {
                result.errorOrNull?.let {
                    LOGGER.info(it.message)
                    LOGGER.info(it.stackTrace())
                }
            }
            else -> { }
        }
    }

    private fun Throwable.stackTrace() = StringWriter()
            .let {
                this.printStackTrace(PrintWriter(it))
                it.toString()
            }
}