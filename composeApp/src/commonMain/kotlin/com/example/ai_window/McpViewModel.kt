// Day 10: MCP ViewModel для управления состоянием MCP интеграции
package com.example.ai_window

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_window.model.McpServerInfo
import com.example.ai_window.service.McpService
import com.example.ai_window.service.McpToolItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel для управления состоянием MCP интеграции
 * Загружает информацию о доступных MCP tools и resources с сервера
 */
class McpViewModel : ViewModel() {

    private val mcpService = McpService()

    // Состояние загрузки
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Полная информация о MCP сервере
    private val _mcpServerInfo = MutableStateFlow<McpServerInfo?>(null)
    val mcpServerInfo: StateFlow<McpServerInfo?> = _mcpServerInfo.asStateFlow()

    // Список MCP tools (упрощенный формат)
    private val _mcpTools = MutableStateFlow<List<McpToolItem>>(emptyList())
    val mcpTools: StateFlow<List<McpToolItem>> = _mcpTools.asStateFlow()

    // Количество доступных tools
    private val _toolsCount = MutableStateFlow(0)
    val toolsCount: StateFlow<Int> = _toolsCount.asStateFlow()

    // Сообщение об ошибке
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        // Загружаем данные при инициализации
        loadMcpServerInfo()
    }

    /**
     * Загрузить полную информацию о MCP сервере
     */
    fun loadMcpServerInfo() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            mcpService.getMcpServerInfo()
                .onSuccess { serverInfo ->
                    _mcpServerInfo.value = serverInfo
                    _toolsCount.value = serverInfo.tools.size
                    println("[McpViewModel] Loaded MCP server info: ${serverInfo.serverName} v${serverInfo.version}")
                    println("[McpViewModel] Tools: ${serverInfo.tools.size}, Resources: ${serverInfo.resources.size}")
                }
                .onFailure { error ->
                    _errorMessage.value = "Ошибка загрузки MCP info: ${error.message}"
                    println("[McpViewModel] Error loading MCP server info: ${error.message}")
                    error.printStackTrace()
                }

            _isLoading.value = false
        }
    }

    /**
     * Загрузить список MCP tools (упрощенный формат)
     */
    fun loadMcpTools() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            mcpService.getMcpTools()
                .onSuccess { toolsResponse ->
                    _mcpTools.value = toolsResponse.tools
                    _toolsCount.value = toolsResponse.count
                    println("[McpViewModel] Loaded ${toolsResponse.count} MCP tools")
                }
                .onFailure { error ->
                    _errorMessage.value = "Ошибка загрузки MCP tools: ${error.message}"
                    println("[McpViewModel] Error loading MCP tools: ${error.message}")
                    error.printStackTrace()
                }

            _isLoading.value = false
        }
    }

    /**
     * Очистить сообщение об ошибке
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Перезагрузить данные
     */
    fun refresh() {
        loadMcpServerInfo()
    }
}
