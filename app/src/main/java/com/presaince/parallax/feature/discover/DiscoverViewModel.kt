/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.presaince.parallax.core.AppResult
import com.presaince.parallax.data.discover.DiscoverEngine
import com.presaince.parallax.data.discover.Recipe
import com.presaince.parallax.data.discover.RecipeRepository
import com.presaince.parallax.data.discover.RecipeResult
import com.presaince.parallax.data.discover.WalkStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiscoverUiState(
    val recipes: List<Recipe> = emptyList(),
    val selected: String? = null,
    val picks: List<WalkStep> = emptyList(),
    val walking: Boolean = false,
    val message: String? = null,
    /** The recipe in force, including any tuning made on this screen. */
    val recipe: Recipe? = null,
)

class DiscoverViewModel(
    private val recipes: RecipeRepository,
    private val engine: DiscoverEngine,
) : ViewModel() {

    private val _state = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val problems = recipes.ensureBuiltIns()
            if (problems.isNotEmpty()) {
                _state.update { it.copy(message = problems.first()) }
            }
        }
        // The chips follow the table: a backup or a delete elsewhere is not a stale list here.
        viewModelScope.launch {
            recipes.observeRecipes().collect { loaded ->
                _state.update { it.copy(recipes = loaded) }
            }
        }
    }

    /** A recipe is a button: the tap selects it and starts the fetch in the same moment. */
    fun selectAndWalk(name: String) {
        load(name)
    }

    fun refresh() {
        _state.value.selected?.let(::load)
    }

    fun load(name: String) {
        // walking is set here, before the coroutine, so the screen says so on the same frame.
        _state.update {
            it.copy(
                selected = name,
                picks = emptyList(),
                walking = true,
                message = null,
                recipe = tuned[name] ?: it.recipe?.takeIf { r -> r.name == name },
            )
        }
        viewModelScope.launch {
            val recipe = tuned[name] ?: recipes.find(name)
            if (recipe == null) {
                _state.update { it.copy(walking = false, message = "Recipe not found") }
                return@launch
            }
            _state.update { it.copy(recipe = recipe) }
            when (val result = engine.picks(recipe, PAGE)) {
                is AppResult.Ok -> _state.update {
                    it.copy(
                        walking = false,
                        picks = result.value,
                        message = if (result.value.isEmpty()) {
                            "Nothing passed this recipe's filters"
                        } else {
                            "${result.value.size} picks from ${recipe.name}"
                        },
                    )
                }
                is AppResult.Err -> _state.update {
                    it.copy(walking = false, message = result.message)
                }
            }
        }
    }

    /** Tuning is kept for the session and applied to the next fetch, not written to the recipe. */
    private val tuned = mutableMapOf<String, Recipe>()

    fun tune(recipe: Recipe) {
        tuned[recipe.name] = recipe
        load(recipe.name)
    }

    fun resetTuning(name: String) {
        tuned.remove(name)
        load(name)
    }

    fun import(json: String) {
        viewModelScope.launch {
            when (val result = recipes.import(json)) {
                is RecipeResult.Ok -> {
                    val loaded = recipes.all()
                    _state.update {
                        it.copy(
                            recipes = loaded,
                            selected = result.recipe.name,
                            message = "Imported ${result.recipe.name}",
                            picks = emptyList(),
                        )
                    }
                }
                is RecipeResult.Err -> _state.update {
                    it.copy(message = result.problems.take(3).joinToString("; "))
                }
            }
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    /** A note the screen owns: a file that could not be read, before any recipe is touched. */
    fun notify(message: String) {
        _state.update { it.copy(message = message) }
    }

    /** Only the reader's own recipes reach this: the query refuses a built-in by source. */
    fun delete(name: String) {
        viewModelScope.launch {
            recipes.delete(name)
            _state.update {
                if (it.selected == name) {
                    it.copy(
                        selected = null,
                        recipe = null,
                        picks = emptyList(),
                        message = "Removed $name",
                    )
                } else {
                    it.copy(message = "Removed $name")
                }
            }
        }
    }

    private companion object {
        const val PAGE = 12
    }
}
