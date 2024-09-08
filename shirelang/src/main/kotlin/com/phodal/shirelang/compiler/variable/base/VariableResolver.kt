package com.phodal.shirelang.compiler.variable.base

/**
 * The `VariableResolver` interface is designed to provide a mechanism for resolving variables.
 */
interface VariableResolver {
    suspend fun resolve(initVariables: Map<String, Any>): Map<String, Any>
}
