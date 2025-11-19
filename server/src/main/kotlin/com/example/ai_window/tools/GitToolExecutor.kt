// Day 11: Git Tool Executor - выполнение git команд через ProcessBuilder
package com.example.ai_window.tools

import com.example.ai_window.model.ToolResult
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Исполнитель Git команд для MCP tools
 * Выполняет реальные git команды в указанном репозитории
 */
class GitToolExecutor(
    private val repoPath: String = System.getenv("GIT_REPO_PATH")
        ?: System.getProperty("user.dir")
) {

    init {
        println("[GitToolExecutor] Initialized with repo path: $repoPath")
    }

    /**
     * Выполнить git tool по имени
     */
    fun execute(toolName: String, params: Map<String, String>): ToolResult {
        val startTime = System.currentTimeMillis()

        return try {
            val result = when (toolName) {
                "git-log" -> executeGitLog(params)
                "git-status" -> executeGitStatus()
                "git-diff" -> executeGitDiff(params)
                "git-branches" -> executeGitBranches()
                else -> ToolResult(
                    success = false,
                    output = "",
                    error = "Unknown tool: $toolName"
                )
            }

            val executionTime = System.currentTimeMillis() - startTime
            result.copy(executionTime = executionTime)
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            ToolResult(
                success = false,
                output = "",
                error = "Execution error: ${e.message}",
                executionTime = executionTime
            )
        }
    }

    /**
     * git log - история коммитов
     */
    private fun executeGitLog(params: Map<String, String>): ToolResult {
        val count = params["count"]?.toIntOrNull() ?: 10
        val command = listOf(
            "git", "log",
            "--oneline",
            "--pretty=format:%h | %s | %an | %ar",
            "-n", count.toString()
        )
        return runGitCommand(command)
    }

    /**
     * git status - статус репозитория
     */
    private fun executeGitStatus(): ToolResult {
        val command = listOf("git", "status", "--short")
        return runGitCommand(command)
    }

    /**
     * git diff - изменения в файлах
     */
    private fun executeGitDiff(params: Map<String, String>): ToolResult {
        val file = params["file"]
        val command = if (file != null) {
            listOf("git", "diff", "--stat", file)
        } else {
            listOf("git", "diff", "--stat")
        }
        return runGitCommand(command)
    }

    /**
     * git branch - список веток
     */
    private fun executeGitBranches(): ToolResult {
        val command = listOf("git", "branch", "-a", "--format=%(refname:short) %(HEAD)")
        return runGitCommand(command)
    }

    /**
     * Выполнить git команду через ProcessBuilder
     */
    private fun runGitCommand(command: List<String>): ToolResult {
        println("[GitToolExecutor] Running: ${command.joinToString(" ")}")

        val processBuilder = ProcessBuilder(command)
            .directory(File(repoPath))
            .redirectErrorStream(true)

        val process = processBuilder.start()
        val output = process.inputStream.bufferedReader().readText()

        val completed = process.waitFor(30, TimeUnit.SECONDS)

        return if (completed && process.exitValue() == 0) {
            println("[GitToolExecutor] Success: ${output.take(100)}...")
            ToolResult(
                success = true,
                output = output.trim().ifEmpty { "No output" }
            )
        } else {
            val errorMessage = if (!completed) {
                "Command timed out after 30 seconds"
            } else {
                "Exit code: ${process.exitValue()}"
            }
            println("[GitToolExecutor] Error: $errorMessage")
            ToolResult(
                success = false,
                output = output.trim(),
                error = errorMessage
            )
        }
    }
}
