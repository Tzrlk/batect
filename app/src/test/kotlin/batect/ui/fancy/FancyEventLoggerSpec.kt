/*
   Copyright 2017 Charles Korn.

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

package batect.ui.fancy

import batect.config.Container
import batect.docker.DockerContainer
import batect.model.events.ContainerBecameHealthyEvent
import batect.model.events.ContainerRemovedEvent
import batect.model.events.RunningContainerExitedEvent
import batect.model.steps.CreateTaskNetworkStep
import batect.model.steps.DisplayTaskFailureStep
import batect.model.steps.RunContainerStep
import batect.testutils.CreateForEachTest
import batect.testutils.imageSourceDoesNotMatter
import batect.ui.Console
import batect.ui.ConsoleColor
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object FancyEventLoggerSpec : Spek({
    describe("a fancy event logger") {
        val whiteConsole by CreateForEachTest(this) { mock<Console>() }
        val console by CreateForEachTest(this) {
            mock<Console> {
                on { withColor(eq(ConsoleColor.White), any()) } doAnswer {
                    val printStatements = it.getArgument<Console.() -> Unit>(1)
                    printStatements(whiteConsole)
                }
            }
        }

        val redErrorConsole by CreateForEachTest(this) { mock<Console>() }
        val errorConsole by CreateForEachTest(this) {
            mock<Console> {
                on { withColor(eq(ConsoleColor.Red), any()) } doAnswer {
                    val printStatements = it.getArgument<Console.() -> Unit>(1)
                    printStatements(redErrorConsole)
                }
            }
        }

        val startupProgressDisplay by CreateForEachTest(this) { mock<StartupProgressDisplay>() }
        val cleanupProgressDisplay by CreateForEachTest(this) { mock<CleanupProgressDisplay>() }

        val logger by CreateForEachTest(this) {
            FancyEventLogger(console, errorConsole, startupProgressDisplay, cleanupProgressDisplay)
        }

        describe("when logging that a step is starting") {
            val container = Container("task-container", imageSourceDoesNotMatter())
            val dockerContainer = DockerContainer("some-id")

            on("while the task is starting up") {
                val step = CreateTaskNetworkStep
                logger.onStartingTaskStep(step)

                it("notifies the startup progress display of the step and then reprints it") {
                    inOrder(startupProgressDisplay) {
                        verify(startupProgressDisplay).onStepStarting(step)
                        verify(startupProgressDisplay).print(console)
                    }
                }

                it("does not print the cleanup progress display") {
                    verify(cleanupProgressDisplay, never()).print(console)
                }
            }

            on("and that step is to run the task container") {
                val step = RunContainerStep(container, dockerContainer)
                logger.onStartingTaskStep(step)

                it("notifies the startup progress display of the step, reprints it and then prints a blank line") {
                    inOrder(startupProgressDisplay, console) {
                        verify(startupProgressDisplay).onStepStarting(step)
                        verify(startupProgressDisplay).print(console)
                        verify(console).println()
                    }
                }

                it("does not print the cleanup progress display") {
                    verify(cleanupProgressDisplay, never()).print(console)
                }
            }

            describe("and that step is to display an error message") {
                on("and no error messages have been displayed yet") {
                    val step = DisplayTaskFailureStep("Something went wrong.")
                    logger.onStartingTaskStep(step)

                    it("prints the message to the output") {
                        inOrder(console, redErrorConsole) {
                            verify(console).println()
                            verify(redErrorConsole).println(step.message)
                        }
                    }

                    it("prints the cleanup progress to the console") {
                        verify(cleanupProgressDisplay).print(console)
                    }

                    it("prints the error message before printing the cleanup progress, with a blank line in between") {
                        inOrder(redErrorConsole, cleanupProgressDisplay) {
                            verify(redErrorConsole).println(step.message)
                            verify(redErrorConsole).println()
                            verify(cleanupProgressDisplay).print(console)
                        }
                    }

                    it("does not notify the startup progress display") {
                        verify(startupProgressDisplay, never()).onStepStarting(step)
                    }

                    it("does not reprint the startup progress display") {
                        verify(startupProgressDisplay, never()).print(console)
                    }
                }

                on("and an error message has already been displayed") {
                    logger.onStartingTaskStep(DisplayTaskFailureStep("Something went wrong the first time."))
                    reset(redErrorConsole)
                    reset(console)
                    reset(cleanupProgressDisplay)

                    val step = DisplayTaskFailureStep("Something went wrong for a second time.")
                    logger.onStartingTaskStep(step)

                    it("prints the message to the output") {
                        verify(redErrorConsole).println(step.message)
                    }

                    it("prints the cleanup progress to the console") {
                        verify(cleanupProgressDisplay).print(console)
                    }

                    it("clears the existing cleanup progress before printing the error message and reprinting the cleanup progress") {
                        inOrder(console, redErrorConsole, cleanupProgressDisplay) {
                            verify(cleanupProgressDisplay).clear(console)
                            verify(redErrorConsole).println(step.message)
                            verify(redErrorConsole).println()
                            verify(cleanupProgressDisplay).print(console)
                        }
                    }
                }
            }
        }

        describe("when logging an event") {
            on("while the task is starting up") {
                val event = ContainerBecameHealthyEvent(Container("some-container", imageSourceDoesNotMatter()))
                logger.postEvent(event)

                it("notifies the startup progress display of the event and then reprints it") {
                    inOrder(startupProgressDisplay) {
                        verify(startupProgressDisplay).onEventPosted(event)
                        verify(startupProgressDisplay).print(console)
                    }
                }

                it("notifies the cleanup progress display of the event") {
                    verify(cleanupProgressDisplay).onEventPosted(event)
                }

                it("does not print the cleanup progress display") {
                    verify(cleanupProgressDisplay, never()).print(console)
                }
            }

            on("when the task finishes") {
                val container = Container("task-container", imageSourceDoesNotMatter())
                logger.onStartingTaskStep(RunContainerStep(container, DockerContainer("some-id")))
                reset(startupProgressDisplay)
                reset(cleanupProgressDisplay)

                val event = RunningContainerExitedEvent(container, 123)
                logger.postEvent(event)

                it("does not reprint the startup progress display") {
                    verify(startupProgressDisplay, never()).print(any())
                }

                it("does not notify the startup progress display of the event") {
                    verify(startupProgressDisplay, never()).onEventPosted(event)
                }

                it("notifies the cleanup progress display of the event before printing it") {
                    inOrder(cleanupProgressDisplay, console) {
                        verify(cleanupProgressDisplay).onEventPosted(event)
                        verify(console).println()
                        verify(cleanupProgressDisplay).print(console)
                    }
                }

                it("does attempt to clear the previous cleanup progress") {
                    verify(cleanupProgressDisplay, never()).clear(console)
                }
            }

            on("after the task has finished") {
                val container = Container("task-container", imageSourceDoesNotMatter())
                logger.onStartingTaskStep(RunContainerStep(container, DockerContainer("some-id")))
                logger.postEvent(RunningContainerExitedEvent(container, 123))
                reset(startupProgressDisplay)
                reset(cleanupProgressDisplay)

                val event = ContainerRemovedEvent(Container("some-container", imageSourceDoesNotMatter()))
                logger.postEvent(event)

                it("does not reprint the startup progress display") {
                    verify(startupProgressDisplay, never()).print(any())
                }

                it("does not notify the startup progress display of the event") {
                    verify(startupProgressDisplay, never()).onEventPosted(event)
                }

                it("notifies the cleanup progress display of the event before reprinting it") {
                    inOrder(cleanupProgressDisplay) {
                        verify(cleanupProgressDisplay).onEventPosted(event)
                        verify(cleanupProgressDisplay).print(console)
                    }
                }

                it("clears the previously displayed cleanup progress before reprinting it") {
                    inOrder(cleanupProgressDisplay) {
                        verify(cleanupProgressDisplay).clear(console)
                        verify(cleanupProgressDisplay).print(console)
                    }
                }
            }

            on("after an error message has been displayed") {
                logger.onStartingTaskStep(DisplayTaskFailureStep("Something went wrong"))
                reset(startupProgressDisplay)
                reset(cleanupProgressDisplay)

                val event = ContainerBecameHealthyEvent(Container("some-container", imageSourceDoesNotMatter()))
                logger.postEvent(event)

                it("does not reprint the startup progress display") {
                    verify(startupProgressDisplay, never()).print(any())
                }

                it("does not notify the startup progress display of the event") {
                    verify(startupProgressDisplay, never()).onEventPosted(event)
                }

                it("notifies the cleanup progress display of the event before reprinting it") {
                    inOrder(cleanupProgressDisplay) {
                        verify(cleanupProgressDisplay).onEventPosted(event)
                        verify(cleanupProgressDisplay).print(console)
                    }
                }

                it("clears the previously displayed cleanup progress before reprinting it") {
                    inOrder(cleanupProgressDisplay) {
                        verify(cleanupProgressDisplay).clear(console)
                        verify(cleanupProgressDisplay).print(console)
                    }
                }
            }
        }
    }
})