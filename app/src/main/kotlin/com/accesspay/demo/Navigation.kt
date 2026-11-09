package com.accesspay.demo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Route { HOME, TRANSFER, HISTORY, BENEFICIARIES }

/** One back stack for the app's four screens. AccessAI navigation actions move it too. */
class Navigator {
    private var stack = listOf(Route.HOME)
    private val _route = MutableStateFlow(Route.HOME)
    val route: StateFlow<Route> = _route.asStateFlow()

    @Synchronized
    fun open(route: Route) {
        stack = if (route == Route.HOME) listOf(Route.HOME) else stack.filterNot { it == route } + route
        _route.value = stack.last()
    }

    /** Returns false at Home, so the system can close the app. */
    @Synchronized
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack = stack.dropLast(1)
        _route.value = stack.last()
        return true
    }
}
